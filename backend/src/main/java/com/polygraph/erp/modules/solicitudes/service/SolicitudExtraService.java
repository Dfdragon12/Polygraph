package com.polygraph.erp.modules.solicitudes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.catalogo.entity.PreguntaPoligrafia;
import com.polygraph.erp.modules.catalogo.repository.PreguntaPoligrafiaRepository;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioDatosSolicitud;
import com.polygraph.erp.modules.servicios.entity.ServicioPreguntaPoligrafia;
import com.polygraph.erp.modules.servicios.repository.ServicioDatosSolicitudRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioPreguntaPoligrafiaRepository;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudClienteResponse;
import com.polygraph.erp.modules.solicitudes.dto.TemaPoligrafiaRequest;
import com.polygraph.erp.modules.solicitudes.formato.FormatoSolicitud;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Lo extra que el cliente diligencia al pedir una poligrafía: el formato adicional del proceso
 *  (pre-empleo, rutina o específica) y hasta 4 temas a evaluar. Se valida TODO antes de crear la
 *  solicitud (`validar`) y se guarda con ella (`guardar`); `obtener` lo devuelve a quien ejecuta o
 *  coordina el estudio. */
@Service
@RequiredArgsConstructor
@Transactional
public class SolicitudExtraService {

    public static final int MAX_TEMAS = 4;
    private static final int MAX_TEXTO = 300;
    private static final int MAX_AREA = 2000;
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter FECHA_LEGIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private final PreguntaPoligrafiaRepository preguntaRepository;
    private final ServicioPreguntaPoligrafiaRepository servicioPreguntaRepository;
    private final ServicioDatosSolicitudRepository datosRepository;
    private final ProcesoTipoProgresoRepository pasoRepository;
    private final ObjectMapper objectMapper;

    /** Lo ya validado de una solicitud: qué se guarda y en cuáles procesos. */
    public record Extras(Map<FormatoSolicitud, Map<String, String>> datosPorFormato, List<PreguntaPoligrafia> temas,
                         Map<Integer, String> detalleDeTema) {
        static final Extras VACIO = new Extras(Map.of(), List.of(), Map.of());
    }

    /** Un servicio es de poligrafía (y deja elegir temas) si su nombre, o el de alguno de sus
     *  subprocesos, lleva la palabra "poligrafía" — con o sin tilde. */
    @Transactional(readOnly = true)
    public boolean admitePreguntas(Proceso proceso) {
        if (proceso == null) return false;
        if (dicePoligrafia(proceso.getNombreProceso())) return true;
        return pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(proceso.getIdProceso())
                .stream().anyMatch(p -> p.getTipoProgreso() != null && dicePoligrafia(p.getTipoProgreso().getNombreProgreso()));
    }

    static boolean dicePoligrafia(String nombre) {
        if (nombre == null) return false;
        String limpio = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return limpio.contains("poligraf");
    }

    @Transactional(readOnly = true)
    public Extras validar(List<Proceso> procesos, Map<String, String> datos, List<TemaPoligrafiaRequest> temas) {
        Map<String, String> enviados = datos == null ? Map.of() : datos;

        Map<FormatoSolicitud, Map<String, String>> porFormato = new LinkedHashMap<>();
        for (Proceso p : procesos) {
            FormatoSolicitud f = p.getFormatoSolicitud();
            if (f != null && !porFormato.containsKey(f)) porFormato.put(f, validarFormato(f, enviados));
        }

        boolean admite = procesos.stream().anyMatch(this::admitePreguntas);
        List<TemaPoligrafiaRequest> pedidos = temas == null ? List.of() : temas;
        if (pedidos.isEmpty()) return porFormato.isEmpty() ? Extras.VACIO : new Extras(porFormato, List.of(), Map.of());
        if (!admite) throw new ApiException("El servicio elegido no admite temas de poligrafía", HttpStatus.BAD_REQUEST);
        Extras temasElegidos = elegirTemas(pedidos, MAX_TEMAS);
        return new Extras(porFormato, temasElegidos.temas(), temasElegidos.detalleDeTema());
    }

    /** Valida los temas pedidos contra el catálogo activo: sin repetir, con su «cuál» si lo piden y sin pasar de `maximo`. */
    private Extras elegirTemas(List<TemaPoligrafiaRequest> pedidos, int maximo) {
        if (pedidos.size() > maximo) {
            throw new ApiException("Puedes elegir máximo " + maximo + " temas para la poligrafía", HttpStatus.BAD_REQUEST);
        }
        List<PreguntaPoligrafia> elegidos = new ArrayList<>();
        Map<Integer, String> detalles = new LinkedHashMap<>();
        Set<Integer> vistos = new HashSet<>();
        for (TemaPoligrafiaRequest t : pedidos) {
            if (t == null || t.idPregunta() == null) throw new ApiException("Hay un tema de poligrafía sin identificar", HttpStatus.BAD_REQUEST);
            if (!vistos.add(t.idPregunta())) throw new ApiException("Elegiste el mismo tema dos veces", HttpStatus.BAD_REQUEST);
            PreguntaPoligrafia tema = preguntaRepository.findById(t.idPregunta())
                    .filter(q -> Boolean.TRUE.equals(q.getActivo()))
                    .orElseThrow(() -> new ApiException("Uno de los temas de poligrafía ya no está disponible", HttpStatus.BAD_REQUEST));
            if (Boolean.TRUE.equals(tema.getRequiereDetalle())) {
                String detalle = t.detalle() == null ? "" : t.detalle().trim();
                if (detalle.isEmpty()) throw new ApiException("Escribe cuál es el tema en «" + tema.getNombre() + "»", HttpStatus.BAD_REQUEST);
                if (detalle.length() > MAX_TEXTO) {
                    throw new ApiException("El tema «" + tema.getNombre() + "» admite máximo " + MAX_TEXTO + " caracteres", HttpStatus.BAD_REQUEST);
                }
                detalles.put(tema.getId(), detalle);
            }
            elegidos.add(tema);
        }
        return new Extras(Map.of(), elegidos, detalles);
    }

    private Map<String, String> validarFormato(FormatoSolicitud formato, Map<String, String> enviados) {
        Map<String, String> limpios = new LinkedHashMap<>();
        for (FormatoSolicitud.Campo c : formato.campos()) {
            String valor = enviados.get(c.clave()) == null ? "" : enviados.get(c.clave()).trim();
            if (valor.isEmpty()) {
                if (c.obligatorio()) throw new ApiException("«" + c.etiqueta() + "» es obligatorio", HttpStatus.BAD_REQUEST);
                continue;
            }
            int maximo = c.tipo() == FormatoSolicitud.Tipo.AREA_TEXTO ? MAX_AREA : MAX_TEXTO;
            if (valor.length() > maximo) {
                throw new ApiException("«" + c.etiqueta() + "» admite máximo " + maximo + " caracteres", HttpStatus.BAD_REQUEST);
            }
            switch (c.tipo()) {
                case FECHA -> {
                    try {
                        LocalDate.parse(valor);
                    } catch (DateTimeParseException e) {
                        throw new ApiException("«" + c.etiqueta() + "» no es una fecha válida", HttpStatus.BAD_REQUEST);
                    }
                }
                case NUMERO -> {
                    try {
                        int n = Integer.parseInt(valor);
                        if (n < 1 || n > 10000) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        throw new ApiException("«" + c.etiqueta() + "» debe ser un número entero mayor que cero", HttpStatus.BAD_REQUEST);
                    }
                }
                case CORREO -> {
                    if (!CORREO.matcher(valor).matches()) {
                        throw new ApiException("«" + c.etiqueta() + "» no es un correo válido", HttpStatus.BAD_REQUEST);
                    }
                }
                default -> { }
            }
            limpios.put(c.clave(), valor);
        }
        return limpios;
    }

    /** Guarda lo validado en el servicio recién creado (cada servicio de la tanda, el que le aplique). */
    public void guardar(Servicio servicio, Extras extras) {
        Proceso proceso = servicio.getProceso();
        FormatoSolicitud formato = proceso != null ? proceso.getFormatoSolicitud() : null;
        if (formato != null && extras.datosPorFormato().containsKey(formato)) {
            try {
                datosRepository.save(ServicioDatosSolicitud.builder()
                        .idServicio(servicio.getIdServicio())
                        .formato(formato.name())
                        .datos(objectMapper.writeValueAsString(extras.datosPorFormato().get(formato)))
                        .build());
            } catch (JsonProcessingException e) {
                throw new ApiException("No se pudo guardar la información de la solicitud", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        if (!extras.temas().isEmpty() && admitePreguntas(proceso)) {
            int orden = 0;
            for (PreguntaPoligrafia t : extras.temas()) {
                servicioPreguntaRepository.save(ServicioPreguntaPoligrafia.builder()
                        .servicio(servicio).idPregunta(t.getId()).nombre(t.getNombre())
                        .detalle(extras.detalleDeTema().get(t.getId())).orden(orden++).build());
            }
        }
    }

    public static final int MAX_TEMAS_INTERNO = 20;

    /** Admin o gestor cambian los temas a evaluar de una solicitud ya creada (también al escoger un grupo completo del catálogo): reemplaza los que
     *  tenía. El cliente tiene tope de MAX_TEMAS; el personal de Polygraph hasta MAX_TEMAS_INTERNO. */
    @Transactional
    public SolicitudClienteResponse reemplazarTemas(Servicio servicio, List<TemaPoligrafiaRequest> temas) {
        if (!admitePreguntas(servicio.getProceso())) {
            throw new ApiException("Este servicio no admite temas de poligrafía", HttpStatus.BAD_REQUEST);
        }
        Extras elegidos = elegirTemas(temas == null ? List.of() : temas, MAX_TEMAS_INTERNO);
        servicioPreguntaRepository.deleteAll(servicioPreguntaRepository.findByServicio_IdServicioOrderByOrdenAscIdAsc(servicio.getIdServicio()));
        servicioPreguntaRepository.flush();
        int orden = 0;
        for (PreguntaPoligrafia t : elegidos.temas()) {
            servicioPreguntaRepository.save(ServicioPreguntaPoligrafia.builder()
                    .servicio(servicio).idPregunta(t.getId()).nombre(t.getNombre())
                    .detalle(elegidos.detalleDeTema().get(t.getId())).orden(orden++).build());
        }
        return obtener(servicio.getIdServicio());
    }

    /** Lo que el cliente pidió en esta solicitud, o null si no diligenció nada extra. */
    @Transactional(readOnly = true)
    public SolicitudClienteResponse obtener(Integer idServicio) {
        if (idServicio == null) return null;
        List<SolicitudClienteResponse.Tema> temas = servicioPreguntaRepository.findByServicio_IdServicioOrderByOrdenAscIdAsc(idServicio).stream()
                .map(t -> new SolicitudClienteResponse.Tema(t.getNombre(), t.getDetalle())).toList();
        ServicioDatosSolicitud guardado = datosRepository.findById(idServicio).orElse(null);
        if (guardado == null && temas.isEmpty()) return null;

        String titulo = null;
        List<SolicitudClienteResponse.Dato> datos = List.of();
        if (guardado != null) {
            FormatoSolicitud formato = FormatoSolicitud.valueOf(guardado.getFormato());
            titulo = formato.titulo();
            Map<String, String> valores;
            try {
                valores = objectMapper.readValue(guardado.getDatos(), MAPA);
            } catch (JsonProcessingException e) {
                valores = Map.of();
            }
            List<SolicitudClienteResponse.Dato> lista = new ArrayList<>();
            for (FormatoSolicitud.Campo c : formato.campos()) {
                String v = valores.get(c.clave());
                if (v == null || v.isBlank()) continue;
                lista.add(new SolicitudClienteResponse.Dato(c.etiqueta(), c.tipo() == FormatoSolicitud.Tipo.FECHA ? fechaLegible(v) : v));
            }
            datos = lista;
        }
        return new SolicitudClienteResponse(titulo, datos, temas);
    }

    /** Para el Word del informe: tablas['preguntas_cliente'] (${['tema']}, ${['detalle']}) y
     *  tablas['solicitud_cliente'] (${['campo']}, ${['valor']}). Vacías si el cliente no pidió nada. */
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> tablasParaWord(Integer idServicio) {
        SolicitudClienteResponse r = obtener(idServicio);
        if (r == null) return Map.of("preguntas_cliente", List.of(), "solicitud_cliente", List.of());
        List<Map<String, Object>> temas = r.temas().stream()
                .<Map<String, Object>>map(t -> Map.of("tema", t.nombre(), "detalle", t.detalle() == null ? "" : t.detalle())).toList();
        List<Map<String, Object>> datos = r.datos().stream()
                .<Map<String, Object>>map(d -> Map.of("campo", d.etiqueta(), "valor", d.valor())).toList();
        return Map.of("preguntas_cliente", temas, "solicitud_cliente", datos);
    }

    private static String fechaLegible(String iso) {
        try {
            return LocalDate.parse(iso).format(FECHA_LEGIBLE);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }
}
