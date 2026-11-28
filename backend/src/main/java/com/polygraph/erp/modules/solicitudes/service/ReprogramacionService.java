package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.SolicitudReprogramacion;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudReprogramacionRepository;
import com.polygraph.erp.modules.servicios.service.NotificacionesTiempoService;
import com.polygraph.erp.modules.servicios.service.SemaforoTiempoService;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.ImputableA;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.utils.AccesoGestor;
import com.polygraph.erp.shared.utils.FechasLegibles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Reprogramar una solicitud: cambiar su fecha de compromiso. Es la ÚNICA forma de hacerlo. Exige la nueva fecha (un día hábil que no sea pasado),
 * el motivo y de quién es la causa (Polygraph, candidato o cliente). La solicitud pasa por REPROGRAMADO, queda el historial (fecha anterior,
 * nueva, motivo, responsable y quién) y se avisa al cliente. La primera fecha prometida se conserva como compromiso original.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReprogramacionService {

    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final SolicitudReprogramacionRepository reprogramacionRepository;
    private final HistorialEstadoServicioRepository historialRepository;
    private final SemaforoTiempoService semaforoTiempoService;
    private final NotificacionesTiempoService avisos;
    private final CalendarioHabilService calendario;

    @Transactional
    public SolicitudReprogramacion reprogramar(Integer idServicio, LocalDate fechaNueva, String motivo, ImputableA responsable, String emailActor) {
        Servicio s = servicioRepository.findById(idServicio).orElseThrow(() -> new ApiException("Solicitud no encontrada", HttpStatus.NOT_FOUND));
        Usuario actor = usuarioRepository.findByEmail(emailActor).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(s, actor);
        if (s.getEstado() == EstadoServicio.PUBLICADO || s.getEstado() == EstadoServicio.CANCELADO) {
            throw new ApiException("Una solicitud " + (s.getEstado() == EstadoServicio.PUBLICADO ? "ya entregada" : "cancelada") + " no se puede reprogramar.", HttpStatus.CONFLICT);
        }
        if (fechaNueva == null) throw new ApiException("Indica la nueva fecha de compromiso.", HttpStatus.BAD_REQUEST);
        if (motivo == null || motivo.isBlank()) throw new ApiException("Cuéntanos el motivo de la reprogramación.", HttpStatus.BAD_REQUEST);
        if (motivo.trim().length() > 500) throw new ApiException("El motivo no puede superar 500 caracteres.", HttpStatus.BAD_REQUEST);
        if (responsable == null) throw new ApiException("Indica de quién es la causa: Polygraph, el candidato o el cliente.", HttpStatus.BAD_REQUEST);
        if (fechaNueva.isBefore(calendario.hoy())) throw new ApiException("La nueva fecha no puede ser anterior a hoy.", HttpStatus.BAD_REQUEST);
        if (!calendario.esDiaHabil(fechaNueva)) {
            throw new ApiException("El " + FechasLegibles.larga(fechaNueva) + " no es un día hábil. Elige un día de lunes a viernes que no sea festivo.", HttpStatus.BAD_REQUEST);
        }
        LocalDate anterior = s.getFechaEntregaEstimada();
        if (fechaNueva.equals(anterior)) throw new ApiException("La nueva fecha es la misma que ya tiene la solicitud.", HttpStatus.BAD_REQUEST);

        EstadoServicio etapaAnterior = s.getEstado();
        if (s.getFechaCompromisoOriginal() == null) s.setFechaCompromisoOriginal(anterior);
        s.setFechaEntregaEstimada(fechaNueva);
        s.setEstado(EstadoServicio.REPROGRAMADO);
        SolicitudReprogramacion r = reprogramacionRepository.save(SolicitudReprogramacion.builder().servicio(s).fechaAnterior(anterior).fechaNueva(fechaNueva)
                .motivo(motivo.trim()).responsable(responsable).usuario(actor.getEmail()).build());
        reprogramacionRepository.flush();
        historialRepository.save(HistorialEstadoServicio.builder().servicio(s).estadoAnterior(etapaAnterior).estadoNuevo(EstadoServicio.REPROGRAMADO).usuario(actor)
                .fechaCambio(calendario.ahora().toLocalDateTime())
                .observacion("Reprogramada del " + FechasLegibles.larga(anterior) + " al " + FechasLegibles.larga(fechaNueva) + " (causa: " + responsable + "). " + motivo.trim())
                .build());
        semaforoTiempoService.recalcular(s);
        avisos.alReprogramar(s, anterior, fechaNueva, motivo.trim());
        log.info("Solicitud {} reprogramada del {} al {} por {} (causa {})", idServicio, anterior, fechaNueva, actor.getEmail(), responsable);
        return r;
    }
}
