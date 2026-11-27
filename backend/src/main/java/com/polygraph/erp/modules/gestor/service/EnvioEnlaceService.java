package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.auth.service.EmailService;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.service.EvaluadoService;
import com.polygraph.erp.modules.servicios.entity.EstadoLink;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.enums.TipoEnlace;
import com.polygraph.erp.shared.estados.EstadoEnlaceCandidato;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PB-04: enviar el enlace al candidato y seguirlo. El tipo (corto o completo) lo decide el sistema. Canales: correo (sale solo) y WhatsApp (se arma el
 * enlace wa.me con el mensaje escrito y el gestor lo envía desde su WhatsApp; no hay proveedor que lo mande solo). «Copiar enlace» también queda registrado.
 * Todo envío, cambio de contacto y ampliación queda en {@code envios_enlace}.
 *
 * La vigencia es una FECHA DE REFERENCIA (2 días hábiles): desde V197 el enlace no vence por tiempo, así que no cierra el acceso; sirve para el mensaje,
 * el seguimiento y el recordatorio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvioEnlaceService {

    public static final int VIGENCIA_DIAS_HABILES = 2;
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d/MM/yyyy", Locale.forLanguageTag("es-CO"));

    private final LinkCandidatoRepository linkRepository;
    private final UsuarioRepository usuarioRepository;
    private final CandidatoRepository candidatoRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final EvaluadoService evaluadoService;
    private final CalendarioHabilService calendario;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public record Seccion(String titulo, String detalle) {}

    /** venceReferencia: la vigencia que tendrá el envío que se haga AHORA (2 días hábiles desde hoy, PC-03), no la de un envío anterior. envios: el historial, del más reciente al más antiguo. */
    public record Contexto(Long idLink, Integer idServicio, String tipoEnlace, String etiqueta, List<Seccion> secciones, String celular, String correo,
                           String mensaje, LocalDate venceReferencia, String url, String estadoEnlace, List<HistorialEnvios.Envio> envios) {}

    public record Paso(String clave, String titulo, String detalle, String estado, LocalDateTime fecha) {}

    public record EnviarRequest(String celular, String correo, String mensaje, Set<String> canales) {}

    public record EnvioResponse(List<String> enviados, String whatsappUrl, LocalDate venceReferencia) {}

    // ───────────────────────── contexto de la ventana ─────────────────────────

    @Transactional(readOnly = true)
    public Contexto contexto(Long idLink, String emailActor) {
        Usuario actor = actor(emailActor);
        LinkCandidato link = linkAccesible(idLink, actor);
        Servicio s = link.getServicio();
        TipoEnlace tipo = evaluadoService.tipoDeEnlace(s);
        Candidato c = s.getCandidato();
        // PC-03 C: cada envío recalcula la vigencia desde AHORA. La ventana y el mensaje usan esa fecha, nunca la de un envío anterior (ya pasada).
        LocalDate vence = vigenciaDeUnEnvioHoy();
        String url = urlDe(link);
        return new Contexto(link.getId(), s.getIdServicio(), tipo.name(), etiqueta(tipo), secciones(tipo),
                c == null ? null : c.getCelular(), c == null ? null : c.getEmailPrincipal(),
                mensajePlantilla(s, c, url, vence), vence, url, EstadoLink.de(link).name(), historialDe(link));
    }

    /** La vigencia de un envío hecho hoy: 2 días hábiles (con festivos). */
    LocalDate vigenciaDeUnEnvioHoy() {
        return calendario.sumarDiasHabiles(calendario.hoy(), VIGENCIA_DIAS_HABILES);
    }

    /** UNO por envío o reenvío: fecha, canales, quién y hasta cuándo valía. El primer envío nunca se pisa. */
    List<HistorialEnvios.Envio> historialDe(LinkCandidato link) {
        List<HistorialEnvios.Fila> filas = jdbc.query(
                "SELECT e.id, e.id_envio, e.canal, e.fecha, e.vigencia_hasta, TRIM(COALESCE(u.nombre,'') || ' ' || COALESCE(u.apellido,'')) AS usuario "
                        + "FROM envios_enlace e LEFT JOIN usuarios u ON u.id_usuario = e.id_usuario WHERE e.id_link = ? ORDER BY e.fecha, e.id",
                (rs, n) -> new HistorialEnvios.Fila(rs.getLong("id"), rs.getString("id_envio"), rs.getString("canal"), rs.getTimestamp("fecha").toLocalDateTime(),
                        rs.getDate("vigencia_hasta") == null ? null : rs.getDate("vigencia_hasta").toLocalDate(), rs.getString("usuario")),
                link.getId());
        return HistorialEnvios.agrupar(filas);
    }

    static String etiqueta(TipoEnlace t) {
        return t == TipoEnlace.CORTO ? "Enlace corto · Antecedentes" : "Enlace completo";
    }

    static List<Seccion> secciones(TipoEnlace t) {
        if (t == TipoEnlace.CORTO) {
            return List.of(new Seccion("Datos personales", "Primer paso de la hoja de vida"),
                    new Seccion("Declaraciones judiciales", "Procesos judiciales · órdenes de captura o asuntos pendientes · sanciones"),
                    new Seccion("Autorización", "Tratamiento de datos y consulta en bases de datos (Ley 1581)"));
        }
        return List.of(new Seccion("Hoja de vida", "Datos personales, educación, experiencia laboral, referencias y declaraciones"),
                new Seccion("Autorización", "Tratamiento de datos personales y consulta en bases de datos"),
                new Seccion("Documentos", "Los que pide el estudio (cédula, certificados laborales y académicos)"));
    }

    String mensajePlantilla(Servicio s, Candidato c, String url, LocalDate vence) {
        String nombre = c == null ? "" : c.getNombres().trim();
        String empresa = s.getCliente() == null ? "tu empleador" : nombreCliente(s.getCliente());
        // PC-03 E: el servicio que se pidió, con su nombre («tu prueba de poligrafía pre empleo»); «estudio de seguridad» solo si el servicio se llama así.
        return "Hola, " + nombre + ". Te escribimos de Polygraph Service. " + empresa + " nos pidió " + FraseServicio.de(s.getProceso() == null ? null : s.getProceso().getNombreProceso())
                + (s.getCargo() == null || s.getCargo().isBlank() ? "" : " para el cargo de " + s.getCargo()) + ".\n"
                + "Llena tu información en este enlace seguro: " + url + "\n"
                + "Para entrar, escribe tu número de cédula. Puedes guardar y seguir después. Te escribiremos un recordatorio hasta el "
                + FECHA_LARGA.format(vence) + ".\n"
                + "Si tienes dudas, responde este mensaje.";
    }

    // ───────────────────────── enviar ─────────────────────────

    @Transactional
    public EnvioResponse enviar(Long idLink, EnviarRequest req, String emailActor) {
        Usuario actor = actor(emailActor);
        LinkCandidato link = linkAccesible(idLink, actor);
        if (!EstadoLink.de(link).conAcceso()) {
            throw new ApiException("Solo se puede enviar un enlace activo (actual: " + EstadoLink.de(link) + ").", HttpStatus.CONFLICT);
        }
        Set<String> canales = req.canales() == null ? Set.of() : req.canales();
        if (canales.isEmpty()) throw new ApiException("Elige por dónde enviarlo: WhatsApp, correo o copiar el enlace.", HttpStatus.BAD_REQUEST);
        Servicio s = link.getServicio();
        Candidato c = s.getCandidato();
        if (c == null) throw new ApiException("El enlace no tiene un candidato asociado.", HttpStatus.CONFLICT);

        cambiarContacto(link, c, req, actor);

        LocalDateTime ahora = LocalDateTime.now();
        // PC-03 C: cada envío (y reenvío) recalcula la vigencia desde AHORA y la deja anotada en SU fila del historial; el primer envío no se toca.
        LocalDate vence = vigenciaDeUnEnvioHoy();
        link.setFechaExpiracion(vence.atTime(LocalTime.of(23, 59, 59)));   // fecha de referencia, no cierra el acceso (V197)
        if (link.getEnviadoEn() == null) link.setEnviadoEn(ahora);
        String idEnvio = UUID.randomUUID().toString();   // las filas de ESTE envío (una por canal) comparten el id

        List<String> enviados = new ArrayList<>();
        String whatsappUrl = null;
        if (canales.contains("CORREO")) {
            if (c.getEmailPrincipal() == null || c.getEmailPrincipal().isBlank()) throw new ApiException("El candidato no tiene correo. Escríbelo y reintenta.", HttpStatus.BAD_REQUEST);
            emailService.enviarLinkEvaluado(c.getEmailPrincipal(), nombreCompleto(c), link.getToken(), s.getProceso() == null ? null : s.getProceso().getNombreProceso());
            registrar(link.getId(), "CORREO", c.getEmailPrincipal(), null, actor.getIdUsuario(), false, idEnvio, vence);
            enviados.add("CORREO");
        }
        if (canales.contains("WHATSAPP")) {
            String tel = soloDigitos(c.getCelular());
            if (tel.length() < 10) throw new ApiException("El celular del candidato no es válido. Corrígelo y reintenta.", HttpStatus.BAD_REQUEST);
            String mensaje = req.mensaje() == null || req.mensaje().isBlank() ? mensajePlantilla(s, c, urlDe(link), vence) : req.mensaje().trim();
            whatsappUrl = "https://wa.me/" + (tel.startsWith("57") && tel.length() == 12 ? tel : "57" + tel) + "?text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
            registrar(link.getId(), "WHATSAPP", c.getCelular(), "Abierto por el gestor desde WhatsApp", actor.getIdUsuario(), false, idEnvio, vence);
            enviados.add("WHATSAPP");
        }
        if (canales.contains("COPIA")) {
            registrar(link.getId(), "COPIA", null, "Copiado para enviarlo por fuera de la plataforma", actor.getIdUsuario(), false, idEnvio, vence);
            enviados.add("COPIA");
        }
        log.info("Enlace enviado: link={}, servicio={}, canales={}, por={}", link.getId(), s.getIdServicio(), enviados, actor.getIdUsuario());
        return new EnvioResponse(enviados, whatsappUrl, vence);
    }

    /** Si el gestor corrigió el celular o el correo, el cambio queda en el candidato y en el historial. */
    private void cambiarContacto(LinkCandidato link, Candidato c, EnviarRequest req, Usuario actor) {
        boolean cambio = false;
        if (req.celular() != null && !req.celular().isBlank() && !soloDigitos(req.celular()).equals(soloDigitos(c.getCelular()))) {
            registrar(link.getId(), "CAMBIO_CONTACTO", req.celular().trim(), "Celular: " + c.getCelular() + " → " + req.celular().trim(), actor.getIdUsuario(), false);
            c.setCelular(req.celular().trim());
            cambio = true;
        }
        if (req.correo() != null && !req.correo().isBlank() && !req.correo().trim().equalsIgnoreCase(c.getEmailPrincipal() == null ? "" : c.getEmailPrincipal())) {
            registrar(link.getId(), "CAMBIO_CONTACTO", req.correo().trim(), "Correo: " + c.getEmailPrincipal() + " → " + req.correo().trim(), actor.getIdUsuario(), false);
            c.setEmailPrincipal(req.correo().trim());
            cambio = true;
        }
        if (cambio) candidatoRepository.save(c);
    }

    @Transactional
    public void copiado(Long idLink, String emailActor) {
        Usuario actor = actor(emailActor);
        LinkCandidato link = linkAccesible(idLink, actor);
        registrar(link.getId(), "COPIA", null, "Copiado para enviarlo por fuera de la plataforma", actor.getIdUsuario(), false);
    }

    /** Mueve la fecha de referencia {@code dias} días hábiles más allá de la que tenga (o de hoy, si ya pasó). */
    @Transactional
    public LocalDate ampliar(Long idLink, int dias, String emailActor) {
        if (dias < 1 || dias > 10) throw new ApiException("Amplía entre 1 y 10 días hábiles.", HttpStatus.BAD_REQUEST);
        Usuario actor = actor(emailActor);
        LinkCandidato link = linkAccesible(idLink, actor);
        LocalDate base = referencia(link).isBefore(calendario.hoy()) ? calendario.hoy() : referencia(link);
        LocalDate nueva = calendario.sumarDiasHabiles(base, dias);
        registrar(link.getId(), "VIGENCIA", null, referencia(link) + " → " + nueva, actor.getIdUsuario(), false);
        link.setFechaExpiracion(nueva.atTime(LocalTime.of(23, 59, 59)));
        return nueva;
    }

    // ───────────────────────── seguimiento ─────────────────────────

    @Transactional(readOnly = true)
    public List<Paso> seguimiento(Long idLink, String emailActor) {
        Usuario actor = actor(emailActor);
        return seguimiento(linkAccesible(idLink, actor));
    }

    /** Enviado → entregado y abierto → diligenciando x de y → enviado por el candidato → vence. Lo usa también el portal del cliente (PB-05). */
    @Transactional(readOnly = true)
    public List<Paso> seguimiento(LinkCandidato link) {
        Servicio s = link.getServicio();
        TipoEnlace tipo = evaluadoService.tipoDeEnlace(s);
        List<Map<String, Object>> envios = jdbc.queryForList(
                "SELECT e.canal, e.destino, e.fecha, e.automatico, COALESCE(u.nombre,'') AS nombre FROM envios_enlace e LEFT JOIN usuarios u ON u.id_usuario = e.id_usuario "
                        + "WHERE e.id_link = ? ORDER BY e.fecha", link.getId());
        List<Paso> pasos = new ArrayList<>();

        // PC-03: el envío del sistema anterior (ANTERIOR) también cuenta: si no, aquí diría «Sin enviar» mientras el cliente ve que sí se envió.
        List<Map<String, Object>> salidas = envios.stream().filter(e -> Set.of("CORREO", "WHATSAPP", "ANTERIOR").contains(String.valueOf(e.get("canal")))).toList();
        if (salidas.isEmpty()) {
            pasos.add(new Paso("ENVIADO", "Sin enviar", "Todavía no se ha enviado por WhatsApp ni por correo", "PENDIENTE", null));
        } else {
            Map<String, Object> primera = salidas.get(0);
            if ("ANTERIOR".equals(primera.get("canal"))) {
                pasos.add(new Paso("ENVIADO", "Enviado", "Envío del sistema anterior · se anotó con la fecha de creación del enlace", "HECHO", ((Timestamp) primera.get("fecha")).toLocalDateTime()));
            } else {
                String canales = salidas.stream().filter(e -> !"ANTERIOR".equals(e.get("canal"))).map(e -> "CORREO".equals(e.get("canal")) ? "correo" : "WhatsApp").distinct().reduce((a, b) -> a + " y " + b).orElse("");
                pasos.add(new Paso("ENVIADO", "Enviado por " + canales, primera.get("nombre") + " · " + salidas.stream().map(e -> String.valueOf(e.get("destino"))).distinct().reduce((a, b) -> a + " · " + b).orElse(""),
                        "HECHO", ((Timestamp) primera.get("fecha")).toLocalDateTime()));
            }
        }

        pasos.add(link.getFechaPrimerIngreso() != null
                ? new Paso("ABIERTO", "Entregado y abierto", "Ingresó con su cédula", "HECHO", link.getFechaPrimerIngreso())
                : new Paso("ABIERTO", "Entregado y abierto", "Todavía no ha entrado", "PENDIENTE", null));

        int total = tipo == TipoEnlace.CORTO ? 3 : 5;
        boolean enviado = Boolean.TRUE.equals(link.getUsado());
        int hechas = enviado ? total : seccionesHechas(s, tipo);
        pasos.add(enviado ? new Paso("DILIGENCIANDO", "Diligenciado · " + total + " de " + total + " secciones", "Completo", "HECHO", link.getFechaUso())
                : link.getFechaPrimerIngreso() != null
                    ? new Paso("DILIGENCIANDO", "Diligenciando · " + hechas + " de " + total + " secciones", "Falta terminar y enviar", "ACTUAL", null)
                    : new Paso("DILIGENCIANDO", "Diligenciando · 0 de " + total + " secciones", "Aún no empieza", "PENDIENTE", null));

        pasos.add(enviado ? new Paso("ENVIADO_CANDIDATO", "Enviado por el candidato", "El reloj vuelve a correr y los subprocesos quedan para asignar", "HECHO", link.getFechaUso())
                : new Paso("ENVIADO_CANDIDATO", "Enviado por el candidato", "El reloj vuelve a correr y los subprocesos quedan para asignar", "PENDIENTE", null));

        Map<String, Object> recordatorio = envios.stream().filter(e -> "RECORDATORIO".equals(e.get("canal"))).reduce((a, b) -> b).orElse(null);
        LocalDate vence = referencia(link);
        boolean vencido = !enviado && !salidas.isEmpty() && vence.isBefore(calendario.hoy());
        pasos.add(new Paso("VENCE", vencido ? "Venció" : "Vence", recordatorio != null
                ? "Recordatorio automático · " + ((Timestamp) recordatorio.get("fecha")).toLocalDateTime().format(DateTimeFormatter.ofPattern("d/MM HH:mm"))
                : "Recordatorio automático a las 24 h si no lo abre o no lo termina", enviado ? "HECHO" : "PENDIENTE", vence.atStartOfDay()));
        return pasos;
    }

    /**
     * Lo que el CLIENTE ve del enlace de su candidato (PB-05). PC-03: {@code estado} y {@code texto} los calcula el servidor con una sola regla
     * ({@link EstadoEnlaceCandidato}); las pantallas muestran ese texto y no arman frases con los demás datos, que quedan por si hacen falta.
     */
    public record SeguimientoCliente(String tipoEnlace, LocalDateTime enviadoEn, LocalDateTime abiertoEn, int seccionesHechas, int seccionesTotal,
                                     LocalDateTime recordatorioEn, LocalDate vence, String estado, String texto) {}

    @Transactional(readOnly = true)
    public SeguimientoCliente seguimientoParaCliente(LinkCandidato link) {
        Servicio s = link.getServicio();
        TipoEnlace tipo = evaluadoService.tipoDeEnlace(s);
        int total = tipo == TipoEnlace.CORTO ? 3 : 5;
        Timestamp recordatorio = jdbc.queryForObject("SELECT MAX(fecha) FROM envios_enlace WHERE id_link = ? AND canal = 'RECORDATORIO'", Timestamp.class, link.getId());
        boolean enviadoPorCandidato = Boolean.TRUE.equals(link.getUsado());
        int hechas = enviadoPorCandidato ? total : seccionesHechas(s, tipo);
        LocalDateTime recordatorioEn = recordatorio == null ? null : recordatorio.toLocalDateTime();
        LocalDate vence = referencia(link);
        var estado = EstadoEnlaceCandidato.calcular(new EstadoEnlaceCandidato.Datos(link.getEnviadoEn(), link.getFechaPrimerIngreso(), enviadoPorCandidato, link.getFechaUso(),
                hechas, total, vence, recordatorioEn), calendario.hoy());
        return new SeguimientoCliente(tipo.name(), link.getEnviadoEn(), link.getFechaPrimerIngreso(), hechas, total, recordatorioEn, vence, estado.estado().name(), estado.texto());
    }

    /** Cuántas de las secciones del enlace lleva diligenciadas, según el paso donde va su hoja de vida. */
    private int seccionesHechas(Servicio s, TipoEnlace tipo) {
        if (s.getCandidato() == null) return 0;
        int paso = hojaVidaRepository.findByCandidato_IdCandidato(s.getCandidato().getIdCandidato()).map(HojaVidaEvaluado::getPasoActual).orElse(0);
        if (tipo == TipoEnlace.COMPLETO) return Math.max(0, Math.min(paso, 5));
        int[] visibles = {0, 4, 5};   // datos personales, declaraciones, revisión (la autorización es la pantalla de entrada)
        int n = 0;
        for (int v : visibles) if (v < paso) n++;
        return Math.min(n, 3);
    }

    // ───────────────────────── apoyo ─────────────────────────

    LocalDate referencia(LinkCandidato link) {
        return link.getFechaExpiracion() == null ? calendario.hoy() : link.getFechaExpiracion().toLocalDate();
    }

    String urlDe(LinkCandidato link) {
        return frontendUrl + "/evaluado/link/" + link.getToken();
    }

    void registrar(Long idLink, String canal, String destino, String detalle, Long idUsuario, boolean automatico) {
        registrar(idLink, canal, destino, detalle, idUsuario, automatico, null, null);
    }

    /** idEnvio y vigenciaHasta solo van en las filas de un envío (correo, WhatsApp, copia): agrupan sus canales y dicen hasta cuándo valía ese envío. */
    void registrar(Long idLink, String canal, String destino, String detalle, Long idUsuario, boolean automatico, String idEnvio, LocalDate vigenciaHasta) {
        jdbc.update("INSERT INTO envios_enlace (id_link, canal, destino, detalle, id_usuario, automatico, fecha, id_envio, vigencia_hasta) VALUES (?,?,?,?,?,?,?,?,?)",
                idLink, canal, destino, detalle, idUsuario, automatico, Timestamp.valueOf(LocalDateTime.now()), idEnvio,
                vigenciaHasta == null ? null : java.sql.Date.valueOf(vigenciaHasta));
    }

    private Usuario actor(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.UNAUTHORIZED));
    }

    private LinkCandidato linkAccesible(Long id, Usuario actor) {
        LinkCandidato link = linkRepository.findById(id).orElseThrow(() -> new ApiException("Enlace no encontrado", HttpStatus.NOT_FOUND));
        com.polygraph.erp.shared.utils.AccesoGestor.validar(link.getServicio(), actor);
        return link;
    }

    static String soloDigitos(String v) {
        return v == null ? "" : v.replaceAll("[^0-9]", "");
    }

    private static String nombreCompleto(Candidato c) {
        return (c.getNombres() + (c.getApellidos() != null ? " " + c.getApellidos() : "")).trim();
    }

    private static String nombreCliente(com.polygraph.erp.modules.clientes.entity.Cliente c) {
        if (c.getNombreComercial() != null && !c.getNombreComercial().isBlank()) return c.getNombreComercial();
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        return c.getNombre() == null ? "tu empleador" : c.getNombre();
    }
}
