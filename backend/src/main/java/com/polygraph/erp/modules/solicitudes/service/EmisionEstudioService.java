package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.EmisionEstudioResponse;
import com.polygraph.erp.modules.solicitudes.dto.EmitirEstudioRequest;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import com.polygraph.erp.modules.solicitudes.repository.DocumentoEstudioRepository;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PB-03: emisión del estudio completo. El gestor escribe la nota final, puede agregar alertas para el cliente y emite:
 * PRELIMINAR (con pendientes; la pasta queda PRELIMINAR y el PDF lo dice) o FINAL (todos los subprocesos publicados; el servicio pasa a PUBLICADO y
 * la pasta a EMITIDA). Cada emisión crea una versión del PDF único (v1, v2...) que arma la cola de trabajos; la descarga entrega ese archivo guardado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmisionEstudioService {

    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalificacionRiesgoService calificacionRiesgoService;
    private final CancelacionSolicitudService cancelacionSolicitudService;
    private final DocumentoEstudioRepository documentoRepository;
    private final ColaTrabajosService cola;
    private final JdbcTemplate jdbc;
    private final com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos almacen;
    private final NotificacionRepository notificaciones;

    /** PC-01: un PDF que lleva más tiempo que este en GENERANDO se da por fallido (el gestor lo ve y pulsa «Reintentar»). */
    public static final int MINUTOS_MAXIMOS_GENERANDO = 15;

    /** Lo que necesita el armado del PDF, ya leído dentro de una transacción (el trabajo corre sin ella). */
    public record Portada(Integer idServicio, Integer version, String tipo, String nivel, String evaluado, String cedula, String cargo, String cliente,
                          String estudio, String concepto, List<String> alertas, List<String> faltantes, List<Informe> informes, Long idUsuario) {}

    public record Descarga(org.springframework.core.io.Resource archivo, String nombre) {}

    public record Informe(Long idSubproceso, String nombre, LocalDateTime fechaPublicado, boolean conDocumento) {}

    @Transactional
    public EmisionEstudioResponse emitir(Integer idServicio, EmitirEstudioRequest req, UserDetails userDetails) {
        Usuario actor = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(servicio, actor);

        boolean esFinal = "FINAL".equalsIgnoreCase(req.tipo());
        if (!esFinal && !"PRELIMINAR".equalsIgnoreCase(req.tipo())) throw new ApiException("El tipo de emisión es PRELIMINAR o FINAL.", HttpStatus.BAD_REQUEST);
        if (req.concepto() == null || req.concepto().isBlank()) {
            throw new ApiException("Escribe la nota del gestor (concepto final): es obligatoria.", HttpStatus.BAD_REQUEST);
        }
        List<EmitirEstudioRequest.Alerta> alertas = req.alertas() == null ? List.of() : req.alertas();
        for (var a : alertas) {
            if (a.texto() == null || a.texto().isBlank() || a.justificacion() == null || a.justificacion().isBlank()) {
                throw new ApiException("Cada alerta para el cliente lleva su texto y una justificación interna.", HttpStatus.BAD_REQUEST);
            }
        }
        List<ServicioSubproceso> vigentes = subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(idServicio).stream()
                .filter(sp -> sp.getEstado() != EstadoSubproceso.CANCELADO).toList();
        List<String> faltantes = vigentes.stream().filter(sp -> sp.getEstado() != EstadoSubproceso.PUBLICADO)
                .map(sp -> sp.getTipoProgreso().getNombreProgreso()).toList();
        if (vigentes.isEmpty()) throw new ApiException("Este servicio no tiene subprocesos para emitir.", HttpStatus.CONFLICT);
        if (esFinal && !faltantes.isEmpty()) {
            throw new ApiException("No se puede emitir el estudio final: faltan por publicar " + String.join(", ", faltantes)
                    + ". Puedes emitir un preliminar.", HttpStatus.CONFLICT);
        }
        if (!esFinal && vigentes.size() == faltantes.size()) {
            throw new ApiException("Publica al menos un informe antes de emitir un preliminar.", HttpStatus.CONFLICT);
        }

        var riesgo = calificacionRiesgoService.obtener(idServicio, actor);
        if (esFinal && riesgo.requiereRevision() && !req.confirmaRevisionManual()) {
            throw new ApiException("El motor pide revisión manual: confírmala antes de emitir el estudio final.", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime ahora = LocalDateTime.now();
        servicio.setConceptoGestor(req.concepto().trim());
        servicio.setEmitidoPor(actor.getIdUsuario());
        servicio.setEmitidoEn(ahora);
        if (esFinal && riesgo.requiereRevision()) { servicio.setRevisionManualPor(actor.getIdUsuario()); servicio.setRevisionManualEn(ahora); }
        for (var a : alertas) {
            jdbc.update("INSERT INTO alertas_estudio (id_servicio, texto, justificacion, id_usuario, fecha) VALUES (?,?,?,?,?)",
                    idServicio, a.texto().trim(), a.justificacion().trim(), actor.getIdUsuario(), java.sql.Timestamp.valueOf(ahora));
        }

        if (esFinal) {
            // Publica el estudio (servicio PUBLICADO, riesgo bloqueado, aviso al cliente) y deja la pasta EMITIDA.
            // Un estudio ya publicado con el flujo anterior (sin PDF único) se reemite sin volver a publicarlo: solo se crea su versión del PDF.
            if (servicio.getEstado() != EstadoServicio.PUBLICADO) {
                // Sin avisar todavía: el cliente se entera cuando el PDF quede LISTO (marcarListo), no antes.
                cancelacionSolicitudService.publicarEstudioSinAviso(idServicio, new CambioEstadoRequest("PUBLICADO", req.concepto().trim()), userDetails);
            } else {
                servicio.setPastaEstado(PastaEstado.EMITIDA);
            }
        } else {
            servicio.setPastaEstado(PastaEstado.PRELIMINAR);
        }

        documentoRepository.invalidar(idServicio);
        int version = documentoRepository.findFirstByIdServicioOrderByVersionDesc(idServicio).map(d -> d.getVersion() + 1).orElse(1);
        DocumentoEstudio doc = documentoRepository.save(DocumentoEstudio.builder().idServicio(idServicio).version(version)
                .tipo(esFinal ? "FINAL" : "PRELIMINAR").nivel(esFinal ? riesgo.nivelEfectivo() : null).idUsuario(actor.getIdUsuario())
                .detalle(esFinal ? null : "PRELIMINAR – CIERRE PENDIENTE: " + String.join(", ", faltantes)).build());
        encolarPdf(actor, doc.getId());
        log.info("Estudio emitido: servicio={}, {} v{}, gestor={}", idServicio, doc.getTipo(), version, actor.getIdUsuario());
        return new EmisionEstudioResponse(version, doc.getTipo(), DocumentoEstudio.GENERANDO);
    }

    /**
     * El trabajo se encola DESPUÉS del commit (si no, el despachador podría tomarlo antes de que exista la fila) y en una transacción NUEVA:
     * en un afterCommit la transacción original ya terminó y un INSERT que se una a ella nunca se confirma (PB-H-23: el PDF quedaba en GENERANDO
     * para siempre y no había ningún trabajo en la cola).
     */
    private void encolarPdf(Usuario actor, Long idDocumento) {
        Map<String, Object> parametros = Map.of("idDocumento", idDocumento);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cola.encolarTrasCommit(TipoTrabajo.ESTUDIO_PDF, actor, parametros);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                cola.encolarTrasCommit(TipoTrabajo.ESTUDIO_PDF, actor, parametros);
            }
        });
    }

    /**
     * ÚNICA regla de «el estudio está listo para el cliente»: estudio publicado, pasta emitida y el PDF vigente ya armado (LISTO y guardado).
     * La usan el Dashboard, el detalle, la descarga y el aviso al cliente: ninguna pantalla puede decir «listo» por su cuenta.
     */
    @Transactional(readOnly = true)
    public boolean disponibleParaCliente(Servicio s) {
        return EstudioListo.paraCliente(s, documentoRepository.findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(s.getIdServicio()).orElse(null));
    }

    /** El gestor pide armar de nuevo el PDF de la MISMA versión cuando falló (o se quedó atascado). */
    @Transactional
    public EmisionEstudioResponse reintentarPdf(Integer idServicio, UserDetails userDetails) {
        Usuario actor = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Servicio servicio = servicioRepository.findById(idServicio).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(servicio, actor);
        DocumentoEstudio d = documentoRepository.findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(idServicio)
                .orElseThrow(() -> new ApiException("Este estudio todavía no tiene un PDF emitido. Emítelo primero.", HttpStatus.CONFLICT));
        if (DocumentoEstudio.LISTO.equals(d.getEstado())) throw new ApiException("El PDF de la versión " + d.getVersion() + " ya está listo.", HttpStatus.CONFLICT);
        if (DocumentoEstudio.GENERANDO.equals(d.getEstado()) && d.getFecha().isAfter(LocalDateTime.now().minusMinutes(MINUTOS_MAXIMOS_GENERANDO))) {
            throw new ApiException("El PDF se está generando. Espera unos minutos antes de reintentar.", HttpStatus.CONFLICT);
        }
        d.setEstado(DocumentoEstudio.GENERANDO);
        d.setDetalle(null);
        d.setFecha(LocalDateTime.now());
        encolarPdf(actor, d.getId());
        log.info("PDF del estudio reintentado: servicio={}, v{}, por={}", idServicio, d.getVersion(), actor.getIdUsuario());
        return new EmisionEstudioResponse(d.getVersion(), d.getTipo(), DocumentoEstudio.GENERANDO);
    }

    /**
     * PC-07 · Reparación: un PDF FINAL que nunca se armó (sigue en GENERANDO o en ERROR y no tiene archivo; es el del #42, que nació cuando el trabajo no entraba a la cola)
     * se reemplaza por una versión NUEVA que sí va por la cola. La anterior queda en el historial —no vigente y en ERROR, con el motivo—, nunca se borra. El cliente recibe el
     * aviso «El PDF de tu estudio se actualizó» cuando la versión nueva esté lista. Devuelve el número de la versión nueva, o 0 si no hacía falta (ya está listo, tiene
     * archivo, no es el vigente o no se sabe quién lo emitió).
     */
    @Transactional
    public int regenerarComoNuevaVersion(Long idDocumento) {
        DocumentoEstudio d = documentoRepository.findById(idDocumento).orElse(null);
        if (d == null || d.getRuta() != null || DocumentoEstudio.LISTO.equals(d.getEstado()) || !"FINAL".equals(d.getTipo()) || !Boolean.TRUE.equals(d.getVigente())) return 0;
        Usuario actor = d.getIdUsuario() == null ? null : usuarioRepository.findById(d.getIdUsuario()).orElse(null);
        if (actor == null) return 0;
        int version = documentoRepository.findFirstByIdServicioOrderByVersionDesc(d.getIdServicio()).map(x -> x.getVersion() + 1).orElse(d.getVersion() + 1);
        d.setVigente(false);
        d.setEstado(DocumentoEstudio.ERROR);
        d.setDetalle("Reemplazado por la versión " + version + " (reparación PC-07): este PDF nunca llegó a armarse.");
        DocumentoEstudio nuevo = documentoRepository.save(DocumentoEstudio.builder().idServicio(d.getIdServicio()).version(version).tipo("FINAL").nivel(d.getNivel())
                .idUsuario(d.getIdUsuario()).build());
        encolarPdf(actor, nuevo.getId());
        log.info("PDF del estudio {} regenerado como versión {} (reparación PC-07)", d.getIdServicio(), version);
        return version;
    }

    /**
     * «Vista previa del PDF completo»: arma el mismo PDF con la nota y las alertas que el gestor lleva escritas, con la marca «VISTA PREVIA», sin emitir ni
     * guardar versión. Va por la cola (202); el resultado es un archivo temporal del propio trabajo.
     */
    @Transactional
    public TrabajoResponse vistaPrevia(Integer idServicio, EmitirEstudioRequest req, UserDetails userDetails) {
        Usuario actor = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Servicio servicio = servicioRepository.findById(idServicio).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(servicio, actor);
        boolean hayPublicados = subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(idServicio).stream()
                .anyMatch(sp -> sp.getEstado() == EstadoSubproceso.PUBLICADO);
        if (!hayPublicados) throw new ApiException("Publica al menos un informe para ver la vista previa.", HttpStatus.CONFLICT);
        List<String> alertas = req.alertas() == null ? List.of() : req.alertas().stream().map(EmitirEstudioRequest.Alerta::texto)
                .filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
        Map<String, Object> parametros = Map.of("idServicio", idServicio, "vistaPrevia", true,
                "concepto", req.concepto() == null ? "" : req.concepto().trim(), "alertas", alertas);
        return cola.aRespuesta(cola.encolar(TipoTrabajo.ESTUDIO_PDF, actor, parametros));
    }

    /** Portada de la vista previa: lo que llevaría el PDF si se emitiera ahora (sin versión ni registro). */
    @Transactional(readOnly = true)
    public Portada portadaVistaPrevia(Integer idServicio, String concepto, List<String> alertas, Usuario actor) {
        Servicio s = servicioRepository.findById(idServicio).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        List<ServicioSubproceso> sps = subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(idServicio);
        List<Informe> informes = sps.stream().filter(sp -> sp.getEstado() == EstadoSubproceso.PUBLICADO)
                .map(sp -> new Informe(sp.getId(), sp.getTipoProgreso().getNombreProgreso(), sp.getFechaPublicado(),
                        Boolean.TRUE.equals(sp.getTipoProgreso().getRequiereDocumento()))).toList();
        List<String> faltantes = sps.stream().filter(sp -> sp.getEstado() != EstadoSubproceso.PUBLICADO && sp.getEstado() != EstadoSubproceso.CANCELADO)
                .map(sp -> sp.getTipoProgreso().getNombreProgreso()).toList();
        boolean completo = faltantes.isEmpty();
        String nivel = null;
        if (completo) {
            try { nivel = calificacionRiesgoService.obtener(idServicio, actor).nivelEfectivo(); } catch (RuntimeException e) { log.debug("Vista previa sin nivel: {}", e.getMessage()); }
        }
        var c = s.getCandidato();
        String cliente = s.getCliente() == null ? "" : (s.getCliente().getRazonSocial() != null ? s.getCliente().getRazonSocial()
                : ((s.getCliente().getNombre() == null ? "" : s.getCliente().getNombre()) + " " + (s.getCliente().getApellido() == null ? "" : s.getCliente().getApellido())).trim());
        return new Portada(idServicio, 0, completo ? "FINAL" : "PRELIMINAR", nivel,
                c == null ? "" : ((c.getNombres() == null ? "" : c.getNombres()) + " " + (c.getApellidos() == null ? "" : c.getApellidos())).trim(),
                c == null ? "" : c.getCedula(), s.getCargo(), cliente, s.getProceso() == null ? "" : s.getProceso().getNombreProceso(),
                concepto == null ? "" : concepto, alertas == null ? List.of() : alertas, faltantes, informes, actor.getIdUsuario());
    }

    /** Estado del PDF vigente del estudio (null si no hay uno disponible para el cliente). */
    @Transactional(readOnly = true)
    public DocumentoEstudio vigente(Integer idServicio) {
        return documentoRepository.findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(idServicio).orElse(null);
    }

    /** Lo que lleva la portada del PDF, leído aquí para que el trabajo (sin transacción) no toque entidades perezosas. */
    @Transactional(readOnly = true)
    public Portada portada(Long idDocumento) {
        DocumentoEstudio d = documentoRepository.findById(idDocumento).orElseThrow(() -> new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND));
        Servicio s = servicioRepository.findById(d.getIdServicio()).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        var c = s.getCandidato();
        List<ServicioSubproceso> sps = subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(d.getIdServicio());
        List<Informe> informes = sps.stream().filter(sp -> sp.getEstado() == EstadoSubproceso.PUBLICADO)
                .map(sp -> new Informe(sp.getId(), sp.getTipoProgreso().getNombreProgreso(), sp.getFechaPublicado(),
                        Boolean.TRUE.equals(sp.getTipoProgreso().getRequiereDocumento()))).toList();
        List<String> faltantes = sps.stream().filter(sp -> sp.getEstado() != EstadoSubproceso.PUBLICADO && sp.getEstado() != EstadoSubproceso.CANCELADO)
                .map(sp -> sp.getTipoProgreso().getNombreProgreso()).toList();
        List<String> alertas = jdbc.queryForList("SELECT texto FROM alertas_estudio WHERE id_servicio = ? ORDER BY id", String.class, d.getIdServicio());
        String cliente = s.getCliente() == null ? "" : (s.getCliente().getRazonSocial() != null ? s.getCliente().getRazonSocial()
                : ((s.getCliente().getNombre() == null ? "" : s.getCliente().getNombre()) + " " + (s.getCliente().getApellido() == null ? "" : s.getCliente().getApellido())).trim());
        return new Portada(d.getIdServicio(), d.getVersion(), d.getTipo(), d.getNivel(),
                c == null ? "" : ((c.getNombres() == null ? "" : c.getNombres()) + " " + (c.getApellidos() == null ? "" : c.getApellidos())).trim(),
                c == null ? "" : c.getCedula(), s.getCargo(), cliente, s.getProceso() == null ? "" : s.getProceso().getNombreProceso(),
                s.getConceptoGestor(), alertas, faltantes, informes, d.getIdUsuario());
    }

    /** El trabajo empezó (o se reintenta): el PDF vuelve a GENERANDO y su reloj de 15 minutos arranca de nuevo. */
    @Transactional
    public void marcarGenerando(Long idDocumento) {
        documentoRepository.findById(idDocumento).ifPresent(d -> {
            if (DocumentoEstudio.LISTO.equals(d.getEstado())) return;   // ya está armado: un reintento duplicado no lo deshace
            d.setEstado(DocumentoEstudio.GENERANDO);
            d.setDetalle(null);
            d.setFecha(LocalDateTime.now());
        });
    }

    /** El trabajo terminó: la versión queda lista y, si es el estudio FINAL, el cliente se entera AHORA (no al emitir). */
    @Transactional
    public void marcarListo(Long idDocumento, String ruta, int paginas) {
        documentoRepository.findById(idDocumento).ifPresent(d -> {
            boolean yaEstabaListo = DocumentoEstudio.LISTO.equals(d.getEstado());
            d.setEstado(DocumentoEstudio.LISTO);
            d.setRuta(ruta);
            d.setPaginas(paginas);
            d.setDetalle(null);
            if (!yaEstabaListo && "FINAL".equals(d.getTipo()) && Boolean.TRUE.equals(d.getVigente())) avisarAlCliente(d);
        });
    }

    /** Falló de verdad (sin más reintentos): el PDF queda en ERROR y el gestor del cliente recibe el aviso para reintentar. */
    @Transactional
    public void marcarError(Long idDocumento, String motivo) {
        documentoRepository.findById(idDocumento).ifPresent(d -> {
            boolean yaEstabaEnError = DocumentoEstudio.ERROR.equals(d.getEstado());
            d.setEstado(DocumentoEstudio.ERROR);
            d.setDetalle(motivo);
            if (!yaEstabaEnError) avisarAlGestor(d);
        });
    }

    /** Pasa a ERROR los PDF que llevan más de {@link #MINUTOS_MAXIMOS_GENERANDO} minutos en GENERANDO. Devuelve cuántos. */
    @Transactional
    public int vencerLosQueTardan() {
        var vencidos = documentoRepository.findByEstadoAndFechaBefore(DocumentoEstudio.GENERANDO, LocalDateTime.now().minusMinutes(MINUTOS_MAXIMOS_GENERANDO));
        for (DocumentoEstudio d : vencidos) {
            log.warn("PDF del estudio {} v{} lleva más de {} min en GENERANDO: se marca como fallido", d.getIdServicio(), d.getVersion(), MINUTOS_MAXIMOS_GENERANDO);
            marcarError(d.getId(), "No terminó en " + MINUTOS_MAXIMOS_GENERANDO + " minutos. Reintenta.");
        }
        return vencidos.size();
    }

    private void avisarAlCliente(DocumentoEstudio d) {
        Servicio s = servicioRepository.findById(d.getIdServicio()).orElse(null);
        if (s == null || s.getCliente() == null) return;
        String evaluado = s.getCandidato() != null ? ((s.getCandidato().getNombres() == null ? "" : s.getCandidato().getNombres()) + " "
                + (s.getCandidato().getApellidos() == null ? "" : s.getCandidato().getApellidos())).trim() : "tu solicitud #" + s.getIdServicio();
        boolean actualizado = d.getVersion() != null && d.getVersion() > 1;
        String titulo = actualizado ? "El PDF de tu estudio se actualizó" : "Tu informe está listo";
        String mensaje = actualizado ? "El estudio de " + evaluado + " tiene un PDF nuevo (versión " + d.getVersion() + ") para descargar."
                : "El estudio de " + evaluado + " ya está listo para descargar.";
        usuarioRepository.findByIdCliente(s.getCliente().getIdCliente()).stream().filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .forEach(u -> notificaciones.save(Notificacion.builder().usuario(u).tipo("SOLICITUD_ESTADO").titulo(titulo).mensaje(mensaje).leida(false)
                        .fechaCreacion(LocalDateTime.now()).referenciaTipo("SERVICIO").referenciaId(s.getIdServicio().longValue()).build()));
    }

    private void avisarAlGestor(DocumentoEstudio d) {
        Servicio s = servicioRepository.findById(d.getIdServicio()).orElse(null);
        if (s == null || s.getCliente() == null || s.getCliente().getIdGestor() == null) return;
        usuarioRepository.findById(s.getCliente().getIdGestor()).filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .ifPresent(g -> notificaciones.save(Notificacion.builder().usuario(g).tipo("ESTUDIO_PDF_FALLIDO")
                        .titulo("No se pudo generar el PDF del estudio #" + s.getIdServicio())
                        .mensaje("El PDF de la versión " + d.getVersion() + " no se armó. Entra a Solicitudes y pulsa «Reintentar»; el cliente todavía no lo ve como listo.")
                        .leida(false).fechaCreacion(LocalDateTime.now()).referenciaTipo("SERVICIO").referenciaId(s.getIdServicio().longValue()).build()));
    }

    /** Entrega el PDF guardado (nunca lo regenera). El cliente solo lo recibe con el estudio publicado y la pasta emitida; el gestor y el admin, también el preliminar. */
    @Transactional(readOnly = true)
    public Descarga descargar(Integer idServicio, UserDetails userDetails) {
        Usuario actor = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Servicio s = servicioRepository.findById(idServicio).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        boolean cliente = actor.getRol() == Rol.ADMIN_CLIENTE || actor.getRol() == Rol.ANALISTA_CLIENTE;
        if (cliente && !actor.getIdCliente().equals(s.getCliente().getIdCliente())) {
            throw new ApiException("Esta solicitud no pertenece a tu cuenta.", HttpStatus.FORBIDDEN);
        }
        if (actor.getRol() == Rol.ANALISTA_CLIENTE) throw new ApiException("Lo descarga el administrador de tu empresa.", HttpStatus.FORBIDDEN);
        AccesoGestor.validar(s, actor);
        DocumentoEstudio d = documentoRepository.findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(idServicio)
                .orElseThrow(() -> new ApiException("El estudio completo todavía no está disponible.", HttpStatus.CONFLICT));
        if (cliente && !estudioPublicado(s)) throw new ApiException("El estudio completo todavía no está disponible.", HttpStatus.CONFLICT);
        if (!DocumentoEstudio.LISTO.equals(d.getEstado()) || d.getRuta() == null) {
            throw new ApiException(DocumentoEstudio.ERROR.equals(d.getEstado())
                    ? "No se pudo armar el PDF del estudio. Intenta emitirlo de nuevo." : "Estamos armando el PDF del estudio; intenta en un momento.", HttpStatus.CONFLICT);
        }
        return new Descarga(new org.springframework.core.io.FileSystemResource(almacen.resolver(d.getRuta())), "Estudio_" + idServicio + "_v" + d.getVersion() + ".pdf");
    }

    public boolean estudioPublicado(Servicio s) {
        return EstudioListo.estudioPublicado(s);
    }
}
