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
import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import com.polygraph.erp.modules.solicitudes.repository.DocumentoEstudioRepository;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
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
                cancelacionSolicitudService.cambiarEstado(idServicio, new CambioEstadoRequest("PUBLICADO", req.concepto().trim()), userDetails);
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
        Long idDoc = doc.getId();
        // El trabajo se encola DESPUÉS del commit: si no, el despachador podría tomarlo antes de que exista la fila.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                cola.encolar(TipoTrabajo.ESTUDIO_PDF, actor, Map.of("idDocumento", idDoc));
            }
        });
        log.info("Estudio emitido: servicio={}, {} v{}, gestor={}", idServicio, doc.getTipo(), version, actor.getIdUsuario());
        return new EmisionEstudioResponse(version, doc.getTipo(), DocumentoEstudio.GENERANDO);
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

    /** El trabajo terminó: la versión queda lista (o con error, con el motivo). */
    @Transactional
    public void marcarListo(Long idDocumento, String ruta, int paginas) {
        documentoRepository.findById(idDocumento).ifPresent(d -> { d.setEstado(DocumentoEstudio.LISTO); d.setRuta(ruta); d.setPaginas(paginas); });
    }

    @Transactional
    public void marcarError(Long idDocumento, String motivo) {
        documentoRepository.findById(idDocumento).ifPresent(d -> { d.setEstado(DocumentoEstudio.ERROR); d.setDetalle(motivo); });
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
        return s.getEstado() == EstadoServicio.PUBLICADO && s.getPastaEstado() == PastaEstado.EMITIDA;
    }
}
