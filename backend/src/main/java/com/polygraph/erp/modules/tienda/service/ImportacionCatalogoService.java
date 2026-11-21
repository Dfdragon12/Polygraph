package com.polygraph.erp.modules.tienda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.BeneficioResponse;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaBeneficio;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPrecio;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.FilaPlan;
import com.polygraph.erp.modules.tienda.entity.ImportacionCatalogo;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.ImportacionCatalogoRepository;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Importación de precios y textos desde la página «Precios del catálogo Polygraph» (TP-05). Carga en una sola dirección y SOLO actualiza
 * servicios que ya existen (se emparejan por idProceso; el nombre solo verifica). Un campo que no viene en el JSON no se toca; uno que
 * viene en null se vacía (donde se puede vaciar). Si cualquier servicio falla, no se importa ninguno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacionCatalogoService {

    public record Cambio(String campo, String antes, String despues) {}

    public record ServicioResultado(Integer idProceso, String nombreProceso, String nombreActual,
                                    List<String> advertencias, List<String> errores, List<Cambio> cambios) {}

    public record Resultado(String modo, boolean aplicado, int totalServicios, int serviciosConCambios, int serviciosConErrores,
                            List<ServicioResultado> servicios) {}

    /** Un valor del JSON: presente (con valor o null explícito) o ausente (no se toca). */
    private record Campo<T>(boolean presente, T valor) {
        static <T> Campo<T> ausente() { return new Campo<>(false, null); }
        static <T> Campo<T> de(T v) { return new Campo<>(true, v); }
    }

    private static final Map<String, Integer> TEXTOS = new LinkedHashMap<>();
    static {
        TEXTOS.put("tituloComercial", 120);
        TEXTOS.put("frasePrincipal", 160);
        TEXTOS.put("descripcionCorta", 400);
        TEXTOS.put("idealPara", 200);
        TEXTOS.put("unidadSingular", 30);
        TEXTOS.put("unidadPlural", 30);
    }

    /** Todo lo que se va a aplicar de un servicio, ya validado. */
    private static final class Preparado {
        Proceso proceso;
        ServicioResultado resultado;
        final Map<String, Campo<String>> textos = new LinkedHashMap<>();
        Campo<List<String>> incluye = Campo.ausente();
        Campo<BigDecimal> costo = Campo.ausente();
        Campo<BigDecimal> pospagoPropio = Campo.ausente();
        Campo<BigDecimal> recargoIntermedia = Campo.ausente();
        Campo<BigDecimal> recargoMunicipio = Campo.ausente();
        Integer dias;
        Boolean visible, aplicaIva, ofrecerPospago;
        Map<Integer, BigDecimal> precios;                 // null = no viene; valores null = quitar el plan
        List<EntradaBeneficio> beneficios;                // null = no viene
    }

    private final ProcesoRepository procesoRepository;
    private final TramoPrecioProcesoRepository tramoRepository;
    private final PrecioCiudadProcesoRepository precioCiudadRepository;
    private final PlanTiendaRepository planRepository;
    private final PreciosPlanService preciosService;
    private final BeneficiosPlanService beneficiosService;
    private final ParametrosTiendaService parametros;
    private final HistorialPreciosService historial;
    private final ImportacionCatalogoRepository importacionRepository;
    private final Clock clock;

    /** `aplicar = false` es la simulación: calcula y valida todo, no guarda nada. */
    @Transactional
    public Resultado importar(JsonNode body, boolean aplicar, String usuario) {
        JsonNode lista = body == null ? null : body.path("servicios");
        if (lista == null || !lista.isArray() || lista.isEmpty()) {
            throw new ApiException("El JSON debe traer una lista «servicios» con al menos un servicio.", HttpStatus.BAD_REQUEST);
        }

        List<PlanTienda> visibles = planRepository.findByVisibleTrueOrderByUnidadesAsc();
        BigDecimal margenMinimo = parametros.decimal("margenMinimoPorcentaje");
        List<Preparado> preparados = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        for (JsonNode n : lista) {
            preparados.add(preparar(n, visibles, margenMinimo, vistos));
        }

        boolean hayErrores = preparados.stream().anyMatch(p -> !p.resultado.errores().isEmpty());
        boolean aplicado = false;
        if (aplicar && !hayErrores) {
            for (Preparado p : preparados) {
                if (!p.resultado.cambios().isEmpty()) aplicarUno(p);
            }
            guardarRegistro(body, preparados, usuario);
            aplicado = true;
            log.info("Importación de catálogo aplicada por {}: {} servicios", usuario, preparados.size());
        }

        List<ServicioResultado> resultados = preparados.stream().map(p -> p.resultado).toList();
        return new Resultado(aplicar ? "aplicar" : "simulacion", aplicado, resultados.size(),
                (int) resultados.stream().filter(r -> !r.cambios().isEmpty()).count(),
                (int) resultados.stream().filter(r -> !r.errores().isEmpty()).count(), resultados);
    }

    // ───────────────────────── validación y diferencias ─────────────────────────

    private Preparado preparar(JsonNode n, List<PlanTienda> visibles, BigDecimal margenMinimo, Set<Integer> vistos) {
        Preparado p = new Preparado();
        List<String> errores = new ArrayList<>(), advertencias = new ArrayList<>();
        List<Cambio> cambios = new ArrayList<>();
        Integer id = n.hasNonNull("idProceso") && n.get("idProceso").canConvertToInt() ? n.get("idProceso").asInt() : null;
        String nombreJson = n.hasNonNull("nombreProceso") ? n.get("nombreProceso").asText() : null;
        p.resultado = new ServicioResultado(id, nombreJson, null, advertencias, errores, cambios);

        if (id == null) {
            errores.add("Falta el idProceso: la importación solo actualiza servicios existentes y nunca crea nuevos.");
            return p;
        }
        if (!vistos.add(id)) {
            errores.add("El idProceso " + id + " viene repetido en el JSON.");
            return p;
        }
        Proceso proceso = procesoRepository.findById(id).orElse(null);
        if (proceso == null) {
            errores.add("No existe un servicio con idProceso " + id + ". La importación no crea servicios nuevos.");
            return p;
        }
        p.proceso = proceso;
        p.resultado = new ServicioResultado(id, nombreJson, proceso.getNombreProceso(), advertencias, errores, cambios);
        if (nombreJson != null && !normalizar(nombreJson).equals(normalizar(proceso.getNombreProceso()))) {
            advertencias.add("El nombre del JSON («" + nombreJson + "») no coincide con el del servicio " + id + " en la plataforma («"
                    + proceso.getNombreProceso() + "»). Verifica que sea el servicio correcto.");
        }

        // Textos
        for (var e : TEXTOS.entrySet()) {
            if (!n.has(e.getKey())) continue;
            JsonNode v = n.get(e.getKey());
            if (!v.isNull() && !v.isTextual()) { errores.add("«" + e.getKey() + "» debe ser texto."); continue; }
            String t = v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
            if (t != null && t.length() > e.getValue()) {
                errores.add("«" + e.getKey() + "» admite máximo " + e.getValue() + " caracteres (trae " + t.length() + ").");
                continue;
            }
            p.textos.put(e.getKey(), Campo.de(t));
            diff(cambios, e.getKey(), actualTexto(proceso, e.getKey()), t);
        }
        if (n.has("incluye")) {
            JsonNode v = n.get("incluye");
            List<String> items = new ArrayList<>();
            if (!v.isNull() && !v.isArray()) errores.add("«incluye» debe ser una lista de textos.");
            else {
                if (v.isArray()) for (JsonNode i : v) if (i.isTextual() && !i.asText().isBlank()) items.add(i.asText().trim());
                if (items.size() > 6) errores.add("«incluye» admite máximo 6 elementos (trae " + items.size() + ").");
                else if (items.stream().anyMatch(i -> i.length() > 80)) errores.add("Cada elemento de «incluye» admite máximo 80 caracteres.");
                else {
                    p.incluye = Campo.de(items);
                    diff(cambios, "incluye", unir(actualIncluye(proceso)), unir(items));
                }
            }
        }

        // Números y banderas
        p.costo = decimal(n, "costoDirecto", true, false, errores);
        if (p.costo.presente()) diff(cambios, "costoDirecto", num(proceso.getCostoDirecto()), num(p.costo.valor()));
        p.pospagoPropio = decimal(n, "precioPospagoPropio", true, true, errores);
        if (p.pospagoPropio.presente()) diff(cambios, "precioPospagoPropio", num(proceso.getPrecioPospagoPropio()), num(p.pospagoPropio.valor()));
        p.recargoIntermedia = decimal(n, "recargoIntermedia", false, false, errores);
        p.recargoMunicipio = decimal(n, "recargoMunicipio", false, false, errores);
        if (n.hasNonNull("diasHabilesEntrega")) {
            JsonNode v = n.get("diasHabilesEntrega");
            if (!v.canConvertToInt() || v.asInt() < 1 || v.asInt() > 365) errores.add("«diasHabilesEntrega» debe ser un entero entre 1 y 365.");
            else { p.dias = v.asInt(); diff(cambios, "diasHabilesEntrega", String.valueOf(proceso.getDiasHabilesEntrega()), String.valueOf(p.dias)); }
        }
        p.visible = booleano(n, "visibleEnTienda", errores);
        if (p.visible != null) diff(cambios, "visibleEnTienda", String.valueOf(!Boolean.FALSE.equals(proceso.getVisibleEnTienda())), String.valueOf(p.visible));
        p.aplicaIva = booleano(n, "aplicaIva", errores);
        if (p.aplicaIva != null) diff(cambios, "aplicaIva", String.valueOf(!Boolean.FALSE.equals(proceso.getAplicaIva())), String.valueOf(p.aplicaIva));
        p.ofrecerPospago = booleano(n, "ofrecerPospago", errores);
        if (p.ofrecerPospago != null) diff(cambios, "ofrecerPospago", String.valueOf(!Boolean.FALSE.equals(proceso.getOfrecerPospago())), String.valueOf(p.ofrecerPospago));

        // Recargos por ciudad: solo cambian los niveles que vienen; aplicaPrecioCiudad se deduce de los valores efectivos.
        BigDecimal actualInter = recargoActual(id, NivelCiudad.INTERMEDIA), actualMuni = recargoActual(id, NivelCiudad.MUNICIPIO_SECUNDARIO);
        if (p.recargoIntermedia.presente()) diff(cambios, "recargoIntermedia", num(orCero(actualInter)), num(orCero(p.recargoIntermedia.valor())));
        if (p.recargoMunicipio.presente()) diff(cambios, "recargoMunicipio", num(orCero(actualMuni)), num(orCero(p.recargoMunicipio.valor())));
        if (p.recargoIntermedia.presente() || p.recargoMunicipio.presente()) {
            BigDecimal inter = p.recargoIntermedia.presente() ? p.recargoIntermedia.valor() : actualInter;
            BigDecimal muni = p.recargoMunicipio.presente() ? p.recargoMunicipio.valor() : actualMuni;
            boolean aplica = (inter != null && inter.signum() > 0) || (muni != null && muni.signum() > 0);
            diff(cambios, "aplicaPrecioCiudad", String.valueOf(Boolean.TRUE.equals(proceso.getAplicaPrecioCiudad())), String.valueOf(aplica));
        }

        // Precios por plan (reglas de TP-02): la escalera se valida con los precios que quedarían.
        Map<Integer, BigDecimal> actuales = preciosActuales(proceso);
        Map<Integer, BigDecimal> efectivos = new HashMap<>(actuales);
        if (n.has("precios")) {
            JsonNode pr = n.get("precios");
            if (!pr.isObject()) errores.add("«precios» debe ser un objeto {\"1\": 40000, \"5\": 38000, …}.");
            else {
                p.precios = new LinkedHashMap<>();
                pr.fields().forEachRemaining(e -> {
                    Integer u = unidades(e.getKey());
                    if (u == null) { errores.add("«precios» trae un plan inválido: «" + e.getKey() + "» (deben ser 1, 5, 20, 100 o 200)."); return; }
                    JsonNode v = e.getValue();
                    if (v.isNull()) { p.precios.put(u, null); efectivos.remove(u); return; }
                    if (!v.isNumber() || v.decimalValue().signum() <= 0) { errores.add("El precio del plan de " + u + " unidades debe ser un número mayor a cero."); return; }
                    p.precios.put(u, v.decimalValue());
                    efectivos.put(u, v.decimalValue());
                });
                if (efectivos.get(1) == null) {
                    errores.add("Falta el precio del plan de 1 unidad (precio base del servicio).");
                } else {
                    Map<Integer, BigDecimal> sinBase = new HashMap<>(efectivos);
                    sinBase.remove(1);
                    CalculadoraPlanes.validarEscalera(visibles, efectivos.get(1), sinBase).ifPresent(errores::add);
                }
                for (int u : PreciosPlanService.UNIDADES_PLAN.stream().sorted().toList()) {
                    if (p.precios.containsKey(u)) diff(cambios, "Precio plan " + u, num(actuales.get(u)), num(p.precios.get(u)));
                }
            }
        }

        // Beneficios
        if (n.has("beneficios")) {
            JsonNode b = n.get("beneficios");
            if (!b.isArray()) errores.add("«beneficios» debe ser una lista de {texto, desde}.");
            else {
                List<EntradaBeneficio> lista = new ArrayList<>();
                for (JsonNode e : b) {
                    String t = e.hasNonNull("texto") ? e.get("texto").asText().trim() : "";
                    Integer desde = e.hasNonNull("desde") && e.get("desde").canConvertToInt() ? e.get("desde").asInt() : null;
                    if (t.isEmpty()) { errores.add("Hay un beneficio sin texto."); continue; }
                    if (t.length() > 120) { errores.add("El beneficio «" + t.substring(0, 30) + "…» supera 120 caracteres."); continue; }
                    if (desde == null || !PreciosPlanService.UNIDADES_PLAN.contains(desde)) {
                        errores.add("El beneficio «" + t + "» debe tener «desde» 1, 5, 20, 100 o 200."); continue;
                    }
                    lista.add(new EntradaBeneficio(t, desde));
                }
                if (lista.size() > BeneficiosPlanService.MAXIMO) errores.add("Máximo " + BeneficiosPlanService.MAXIMO + " beneficios por servicio.");
                p.beneficios = lista;
                diff(cambios, "beneficios", resumirBeneficios(beneficiosService.listar(id)), resumirBeneficios(lista));
            }
        }

        // Advertencias (no impiden importar)
        if (errores.isEmpty()) {
            BigDecimal costo = p.costo.presente() ? p.costo.valor() : proceso.getCostoDirecto();
            if ((p.precios != null || p.costo.presente()) && efectivos.get(1) != null) {
                Map<Integer, BigDecimal> sinBase = new HashMap<>(efectivos);
                sinBase.remove(1);
                for (FilaPlan f : CalculadoraPlanes.analizar(visibles, efectivos.get(1), sinBase, costo, margenMinimo)) {
                    f.alertas().stream().filter(a -> a.startsWith("Margen")).forEach(a -> advertencias.add(f.nombre() + ": " + a));
                }
            }
            boolean ofrece = p.ofrecerPospago != null ? p.ofrecerPospago : !Boolean.FALSE.equals(proceso.getOfrecerPospago());
            BigDecimal propio = p.pospagoPropio.presente() ? p.pospagoPropio.valor() : proceso.getPrecioPospagoPropio();
            if (ofrece && propio == null && efectivos.get(100) == null) {
                advertencias.add("Se ofrece en pospago pero no tiene precio del plan de 100 unidades ni precio pospago propio: quedará sin precio pospago.");
            }
            if (propio != null && efectivos.get(1) != null && propio.compareTo(efectivos.get(1)) > 0) {
                advertencias.add("El precio pospago propio (" + num(propio) + ") es mayor que el precio del plan de 1 unidad (" + num(efectivos.get(1)) + ").");
            }
        }
        return p;
    }

    // ───────────────────────── aplicar ─────────────────────────

    private void aplicarUno(Preparado p) {
        Proceso proceso = p.proceso;
        for (var e : p.textos.entrySet()) ponerTexto(proceso, e.getKey(), e.getValue().valor());
        if (p.incluye.presente()) proceso.setIncluye(p.incluye.valor().isEmpty() ? null : String.join("\n", p.incluye.valor()));
        if (p.costo.presente()) {
            historial.registrar(proceso.getIdProceso(), "Costo directo", proceso.getCostoDirecto(), p.costo.valor());
            proceso.setCostoDirecto(p.costo.valor());
        }
        if (p.pospagoPropio.presente()) {
            historial.registrar(proceso.getIdProceso(), "Precio pospago propio", proceso.getPrecioPospagoPropio(), p.pospagoPropio.valor());
            proceso.setPrecioPospagoPropio(p.pospagoPropio.valor());
            // TP-11: con precio propio el servicio pasa a modo PROPIO; sin él, vuelve al automático (plan de 100)
            proceso.setModoPrecioPospago(p.pospagoPropio.valor() != null ? com.polygraph.erp.shared.enums.ModoPrecioPospago.PROPIO
                    : com.polygraph.erp.shared.enums.ModoPrecioPospago.PLAN_100);
        }
        if (p.dias != null) proceso.setDiasHabilesEntrega(p.dias);
        if (p.visible != null) proceso.setVisibleEnTienda(p.visible);
        if (p.aplicaIva != null) proceso.setAplicaIva(p.aplicaIva);
        if (p.ofrecerPospago != null) proceso.setOfrecerPospago(p.ofrecerPospago);

        if (p.recargoIntermedia.presente()) guardarRecargo(proceso, NivelCiudad.INTERMEDIA, p.recargoIntermedia.valor());
        if (p.recargoMunicipio.presente()) guardarRecargo(proceso, NivelCiudad.MUNICIPIO_SECUNDARIO, p.recargoMunicipio.valor());
        if (p.recargoIntermedia.presente() || p.recargoMunicipio.presente()) {
            boolean aplica = positivo(recargoActual(proceso.getIdProceso(), NivelCiudad.INTERMEDIA))
                    || positivo(recargoActual(proceso.getIdProceso(), NivelCiudad.MUNICIPIO_SECUNDARIO));
            proceso.setAplicaPrecioCiudad(aplica);
        }
        procesoRepository.save(proceso);

        if (p.precios != null) {
            List<EntradaPrecio> entradas = new ArrayList<>();
            if (!p.precios.containsKey(1)) entradas.add(new EntradaPrecio(1, proceso.getValor()));
            p.precios.forEach((u, v) -> entradas.add(new EntradaPrecio(u, v)));
            preciosService.guardar(proceso.getIdProceso(), entradas);
        }
        if (p.beneficios != null) beneficiosService.guardar(proceso.getIdProceso(), p.beneficios);
    }

    private void guardarRecargo(Proceso proceso, NivelCiudad nivel, BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        PrecioCiudadProceso fila = precioCiudadRepository.findByProceso_IdProcesoAndNivelCiudad(proceso.getIdProceso(), nivel)
                .orElseGet(() -> PrecioCiudadProceso.builder().proceso(proceso).nivelCiudad(nivel).build());
        fila.setValor(v);
        precioCiudadRepository.save(fila);
    }

    private void guardarRegistro(JsonNode body, List<Preparado> preparados, String usuario) {
        importacionRepository.save(ImportacionCatalogo.builder()
                .usuario(usuario)
                .fecha(LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA)))
                .totalServicios(preparados.size())
                .idsProceso(preparados.stream().map(p -> String.valueOf(p.resultado.idProceso())).collect(Collectors.joining(",")))
                .payload(body.toString())
                .build());
    }

    // ───────────────────────── utilidades ─────────────────────────

    private Map<Integer, BigDecimal> preciosActuales(Proceso proceso) {
        Map<Integer, BigDecimal> m = new HashMap<>();
        if (proceso.getValor() != null) m.put(1, proceso.getValor());
        for (TramoPrecioProceso t : tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaAsc(proceso.getIdProceso())) {
            if (PreciosPlanService.UNIDADES_PLAN.contains(t.getCantidadMinima())) m.put(t.getCantidadMinima(), t.getValorUnitario());
        }
        return m;
    }

    private BigDecimal recargoActual(Integer idProceso, NivelCiudad nivel) {
        return precioCiudadRepository.findByProceso_IdProcesoAndNivelCiudad(idProceso, nivel).map(PrecioCiudadProceso::getValor).orElse(null);
    }

    /** Sin fila de recargo equivale a recargo 0. */
    private static BigDecimal orCero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static boolean positivo(BigDecimal v) { return v != null && v.signum() > 0; }

    private static Integer unidades(String clave) {
        try {
            int u = Integer.parseInt(clave.trim());
            return PreciosPlanService.UNIDADES_PLAN.contains(u) ? u : null;
        } catch (NumberFormatException e) { return null; }
    }

    private static Campo<BigDecimal> decimal(JsonNode n, String campo, boolean admiteNull, boolean estrictamentePositivo, List<String> errores) {
        if (!n.has(campo)) return Campo.ausente();
        JsonNode v = n.get(campo);
        if (v.isNull()) {
            if (admiteNull) return Campo.de(null);
            errores.add("«" + campo + "» no puede ser null.");
            return Campo.ausente();
        }
        if (!v.isNumber()) { errores.add("«" + campo + "» debe ser un número."); return Campo.ausente(); }
        BigDecimal d = v.decimalValue();
        if (d.signum() < 0 || (estrictamentePositivo && d.signum() == 0)) {
            errores.add("«" + campo + "» debe ser " + (estrictamentePositivo ? "mayor a cero." : "cero o mayor."));
            return Campo.ausente();
        }
        return Campo.de(d);
    }

    private static Boolean booleano(JsonNode n, String campo, List<String> errores) {
        if (!n.hasNonNull(campo)) return null;
        if (!n.get(campo).isBoolean()) { errores.add("«" + campo + "» debe ser true o false."); return null; }
        return n.get(campo).asBoolean();
    }

    private static void diff(List<Cambio> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(vacioANull(antes), vacioANull(despues))) cambios.add(new Cambio(campo, antes, despues));
    }

    private static String vacioANull(String s) { return s == null || s.isBlank() ? null : s; }

    private static String num(BigDecimal v) { return v == null ? null : v.stripTrailingZeros().toPlainString(); }

    private static String unir(List<String> l) { return l == null || l.isEmpty() ? null : String.join(" · ", l); }

    private static String resumirBeneficios(List<?> l) {
        if (l.isEmpty()) return null;
        return l.stream().map(o -> {
            if (o instanceof BeneficioResponse b) return b.texto() + " (desde " + b.desdeUnidades() + ")";
            EntradaBeneficio e = (EntradaBeneficio) o;
            return e.texto() + " (desde " + e.desdeUnidades() + ")";
        }).collect(Collectors.joining(" · "));
    }

    private static List<String> actualIncluye(Proceso p) {
        return p.getIncluye() == null || p.getIncluye().isBlank() ? List.of()
                : java.util.Arrays.stream(p.getIncluye().split("\n")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private static String actualTexto(Proceso p, String campo) {
        return switch (campo) {
            case "tituloComercial" -> p.getTituloComercial();
            case "frasePrincipal" -> p.getFrasePrincipal();
            case "descripcionCorta" -> p.getDescripcionCorta();
            case "idealPara" -> p.getIdealPara();
            case "unidadSingular" -> p.getUnidadSingular();
            case "unidadPlural" -> p.getUnidadPlural();
            default -> null;
        };
    }

    private static void ponerTexto(Proceso p, String campo, String valor) {
        switch (campo) {
            case "tituloComercial" -> p.setTituloComercial(valor);
            case "frasePrincipal" -> p.setFrasePrincipal(valor);
            case "descripcionCorta" -> p.setDescripcionCorta(valor);
            case "idealPara" -> p.setIdealPara(valor);
            case "unidadSingular" -> p.setUnidadSingular(valor);
            case "unidadPlural" -> p.setUnidadPlural(valor);
            default -> { }
        }
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase();
    }
}
