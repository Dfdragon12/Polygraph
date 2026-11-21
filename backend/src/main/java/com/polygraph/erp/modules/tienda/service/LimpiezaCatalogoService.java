package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.entity.LimpiezaCatalogoAuditoria;
import com.polygraph.erp.modules.tienda.entity.PrecioCiudadArchivado;
import com.polygraph.erp.modules.tienda.repository.LimpiezaCatalogoAuditoriaRepository;
import com.polygraph.erp.modules.tienda.repository.PrecioCiudadArchivadoRepository;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TP-10: limpieza del catálogo actual. SOLO la corre el administrador, con confirmación explícita y después de importar los precios nuevos (TP-05).
 * <ol>
 *   <li>ARCHIVAR_PRECIOS_CIUDAD: Antecedentes y Análisis Financiero no cobran por ciudad: sus recargos se archivan (no se borran) y se apaga la casilla.</li>
 *   <li>ACTIVAR_PRECIO_CIUDAD: Poligrafía Pre Empleo, Avanzado, Poligrafía + Antecedentes y Poligrafía + Visita llevan precio por ciudad; se enciende la casilla
 *       SOLO si ya tienen los recargos importados (intermedia y municipio). Los valores nunca se inventan: salen de la importación.</li>
 *   <li>UNIFICAR_DIAS: si el texto comercial dice «12 horas» o «N días hábiles», los días hábiles de la ficha se alinean con el texto.</li>
 *   <li>Reporte (solo reporta, no oculta ni cambia nada): servicios activos sin precio base o sin textos comerciales.</li>
 * </ol>
 * Sin confirmación no se aplica nada; aplicar dos veces no hace nada la segunda vez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimpiezaCatalogoService {

    /** Lo que el administrador debe escribir para aplicar. */
    public static final String CONFIRMACION = "CONFIRMO";

    public static final String ARCHIVAR = "ARCHIVAR_PRECIOS_CIUDAD";
    public static final String ACTIVAR = "ACTIVAR_PRECIO_CIUDAD";
    public static final String DIAS = "UNIFICAR_DIAS";

    private static final Set<String> SIN_PRECIO_CIUDAD = Set.of("antecedentes", "analisis financiero");
    private static final Set<String> CON_PRECIO_CIUDAD = Set.of("poligrafia pre empleo", "avanzado", "poligrafia + antecedentes", "poligrafia + visita");
    private static final Pattern DIAS_HABILES = Pattern.compile("(\\d+)\\s*d[ií]as?\\s*h[aá]biles?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HORAS = Pattern.compile("(\\d+)\\s*horas?", Pattern.CASE_INSENSITIVE);

    private final ProcesoRepository procesoRepository;
    private final PrecioCiudadProcesoRepository precioRepository;
    private final PrecioCiudadArchivadoRepository archivoRepository;
    private final LimpiezaCatalogoAuditoriaRepository auditoriaRepository;
    private final Clock clock;

    public record Accion(String paso, Integer idProceso, String servicio, String descripcion) {}

    public record Hallazgo(Integer idProceso, String servicio, String detalle) {}

    public record Plan(List<Accion> acciones, List<String> advertencias, List<Hallazgo> sinPrecioBase, List<Hallazgo> sinTextos) {}

    public record Resultado(boolean aplicado, Plan plan) {}

    private record Paso(Accion accion, Runnable ejecutar) {}

    private record Calculo(List<Paso> pasos, Plan plan) {}

    /** Lo que se haría y el reporte, sin tocar nada. */
    @Transactional(readOnly = true)
    public Plan simular() {
        return calcular("simulacion").plan();
    }

    /**
     * Aplica la limpieza. {@code confirmacion} debe ser exactamente «CONFIRMO». {@code pasos} limita a qué pasos se aplican (null o vacío = todos).
     */
    @Transactional
    public Resultado aplicar(String confirmacion, Set<String> pasos, String usuario) {
        if (!CONFIRMACION.equals(confirmacion == null ? null : confirmacion.trim())) {
            throw new ApiException("Para aplicar la limpieza escribe «" + CONFIRMACION + "». No se cambió nada.", HttpStatus.BAD_REQUEST);
        }
        Calculo c = calcular(usuario);
        int aplicados = 0;
        for (Paso p : c.pasos()) {
            if (pasos != null && !pasos.isEmpty() && !pasos.contains(p.accion().paso())) continue;
            p.ejecutar().run();
            auditoriaRepository.save(LimpiezaCatalogoAuditoria.builder().fecha(ahora()).usuario(usuario).paso(p.accion().paso())
                    .idProceso(p.accion().idProceso()).detalle(recortar(p.accion().descripcion())).build());
            aplicados++;
        }
        log.info("Limpieza del catálogo aplicada por {}: {} cambio(s)", usuario, aplicados);
        return new Resultado(true, calcular(usuario).plan());   // lo que quede pendiente después
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA));
    }

    private static String recortar(String s) {
        return s != null && s.length() > 490 ? s.substring(0, 490) : s;
    }

    private Calculo calcular(String usuario) {
        List<Proceso> procesos = procesoRepository.findAll();
        List<Paso> pasos = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        List<String> encontrados = new ArrayList<>();

        for (Proceso p : procesos) {
            String nombre = normalizar(p.getNombreProceso());
            Map<NivelCiudad, BigDecimal> recargos = recargosDe(p);
            List<PrecioCiudadProceso> filas = precioRepository.findByProceso_IdProcesoOrderByNivelCiudadAsc(p.getIdProceso());

            // 1) sin precio por ciudad: archivar
            if (SIN_PRECIO_CIUDAD.contains(nombre)) {
                encontrados.add(nombre);
                if (Boolean.TRUE.equals(p.getAplicaPrecioCiudad()) || !filas.isEmpty()) {
                    String d = "Archiva " + filas.size() + " recargo(s) por ciudad y apaga «aplica precio por ciudad» (no se borran: quedan archivados)";
                    pasos.add(new Paso(new Accion(ARCHIVAR, p.getIdProceso(), p.getNombreProceso(), d), () -> archivar(p, filas, usuario)));
                }
            }

            // 2) con precio por ciudad: encender la casilla si ya tiene los recargos importados
            if (CON_PRECIO_CIUDAD.contains(nombre)) {
                encontrados.add(nombre);
                boolean completo = positivo(recargos.get(NivelCiudad.INTERMEDIA)) && positivo(recargos.get(NivelCiudad.MUNICIPIO_SECUNDARIO));
                if (!completo) {
                    advertencias.add("«" + p.getNombreProceso() + "» no tiene cargados los recargos de ciudad intermedia y municipio: impórtalos (TP-05) antes de aplicar; no se toca.");
                } else if (!Boolean.TRUE.equals(p.getAplicaPrecioCiudad())) {
                    String d = "Enciende «aplica precio por ciudad» (recargos importados: intermedia " + recargos.get(NivelCiudad.INTERMEDIA).toPlainString()
                            + ", municipio " + recargos.get(NivelCiudad.MUNICIPIO_SECUNDARIO).toPlainString() + ")";
                    pasos.add(new Paso(new Accion(ACTIVAR, p.getIdProceso(), p.getNombreProceso(), d), () -> {
                        p.setAplicaPrecioCiudad(true);
                        procesoRepository.save(p);
                    }));
                }
            }

            // 3) días hábiles de la ficha vs. lo que dicen los textos
            Integer deTexto = diasDelTexto(p.getTituloComercial(), p.getFrasePrincipal(), p.getDescripcionCorta());
            if (Boolean.TRUE.equals(p.getActivo()) && deTexto != null && !deTexto.equals(p.getDiasHabilesEntrega())) {
                String d = "Días hábiles de la ficha: " + p.getDiasHabilesEntrega() + " → " + deTexto + " (según el texto comercial)";
                pasos.add(new Paso(new Accion(DIAS, p.getIdProceso(), p.getNombreProceso(), d), () -> {
                    p.setDiasHabilesEntrega(deTexto);
                    procesoRepository.save(p);
                }));
            }
        }
        for (String esperado : unir(SIN_PRECIO_CIUDAD, CON_PRECIO_CIUDAD)) {
            if (!encontrados.contains(esperado)) advertencias.add("No se encontró un servicio llamado «" + esperado + "» (se comparan nombres sin tildes ni mayúsculas).");
        }

        // 4) reporte: solo reporta
        List<Hallazgo> sinPrecio = new ArrayList<>();
        List<Hallazgo> sinTextos = new ArrayList<>();
        for (Proceso p : procesos) {
            if (!Boolean.TRUE.equals(p.getActivo())) continue;
            if (p.getValor() == null) sinPrecio.add(new Hallazgo(p.getIdProceso(), p.getNombreProceso(), "Sin precio base: no aparece en la tienda por planes"));
            List<String> faltan = new ArrayList<>();
            if (vacio(p.getTituloComercial())) faltan.add("título comercial");
            if (vacio(p.getFrasePrincipal())) faltan.add("frase principal");
            if (vacio(p.getDescripcionCorta())) faltan.add("qué es");
            if (!faltan.isEmpty()) sinTextos.add(new Hallazgo(p.getIdProceso(), p.getNombreProceso(),
                    "Falta: " + String.join(", ", faltan) + ". Sigue visible con su nombre actual hasta que se complete"));
        }
        List<Accion> acciones = pasos.stream().map(Paso::accion).toList();
        return new Calculo(pasos, new Plan(acciones, advertencias, sinPrecio, sinTextos));
    }

    private void archivar(Proceso p, List<PrecioCiudadProceso> filas, String usuario) {
        LocalDateTime ahora = ahora();
        for (PrecioCiudadProceso f : filas) {
            archivoRepository.save(PrecioCiudadArchivado.builder().idProceso(p.getIdProceso()).nivelCiudad(f.getNivelCiudad()).valor(f.getValor())
                    .archivadoEn(ahora).archivadoPor(usuario).motivo("TP-10: el servicio no cobra recargo por ciudad").build());
        }
        if (!filas.isEmpty()) precioRepository.deleteByProceso_IdProceso(p.getIdProceso());
        p.setAplicaPrecioCiudad(false);
        procesoRepository.save(p);
    }

    private Map<NivelCiudad, BigDecimal> recargosDe(Proceso p) {
        Map<NivelCiudad, BigDecimal> m = new EnumMap<>(NivelCiudad.class);
        for (PrecioCiudadProceso f : precioRepository.findByProceso_IdProcesoOrderByNivelCiudadAsc(p.getIdProceso())) m.put(f.getNivelCiudad(), f.getValor());
        return m;
    }

    // ───────────────────────── reglas puras ─────────────────────────

    /** Sin tildes, en minúsculas, con un solo espacio y el «+» rodeado de espacios: «Poligrafía+Visita» y «poligrafia + visita» son lo mismo. */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        return s.replaceAll("\\s*\\+\\s*", " + ").replaceAll("\\s+", " ").trim();
    }

    /** «3 días hábiles» → 3; «12 horas» → 1 (un día hábil cubre hasta 24 horas); sin número de tiempo → null. Manda «días hábiles» si aparece. */
    static Integer diasDelTexto(String... textos) {
        StringBuilder sb = new StringBuilder();
        for (String t : textos) if (t != null) sb.append(t).append(' ');
        String todo = sb.toString();
        Matcher d = DIAS_HABILES.matcher(todo);
        if (d.find()) return Integer.parseInt(d.group(1));
        Matcher h = HORAS.matcher(todo);
        if (h.find()) return Math.max(1, (int) Math.ceil(Integer.parseInt(h.group(1)) / 24.0));
        return null;
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static List<String> unir(Set<String> a, Set<String> b) {
        List<String> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
    }
}
