package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.service.MotivoSubprocesoService;
import com.polygraph.erp.modules.gestor.service.AsignacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.DesistimientoRequest;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoMotivo;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.AlDesistir;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Cancelar la solicitud completa y desistimiento del candidato (SE-02). Va aparte de SolicitudService porque arrastra a los
 * subprocesos (AsignacionService), y AsignacionService ya depende de SolicitudService de forma indirecta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelacionSolicitudService {

    private final SolicitudService solicitudService;
    private final AsignacionService asignacionService;
    private final MotivoSubprocesoService motivoService;
    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final UsuarioRepository usuarioRepository;

    /** Punto único de cambio de estado del servicio: si es CANCELADO arrastra a los subprocesos, lo demás lo valida SolicitudService. */
    @Transactional
    public void cambiarEstado(Integer idServicio, CambioEstadoRequest request, UserDetails userDetails) {
        if (!"CANCELADO".equalsIgnoreCase(request.estado())) {
            solicitudService.cambiarEstado(idServicio, request, userDetails);
            return;
        }
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        boolean interno = usuario.getRol() == Rol.GESTOR || usuario.getRol() == Rol.ADMIN_POLYGRAPH;

        List<ServicioSubproceso> vigentes = vigentes(idServicio);
        boolean conIniciados = vigentes.stream().anyMatch(sp -> noSePuedeCancelar(sp));
        if (conIniciados && usuario.getRol() != Rol.ADMIN_POLYGRAPH) {
            throw new ApiException("Esta solicitud ya tiene subprocesos iniciados o terminados: no se puede cancelar completa. "
                    + "Cancela uno a uno los que no han iniciado, o registra el desistimiento del candidato.", HttpStatus.CONFLICT);
        }
        // El gestor y el administrador eligen el motivo de la lista; el cliente escribe el suyo (el servicio lo exige).
        String motivo = interno
                ? motivoService.resolver(request.idMotivo(), TipoMotivo.CANCELACION, request.observacion()).descripcion()
                : request.observacion();

        // Primero el servicio: valida el permiso y que la solicitud sea de quien la pide. Si algo falla, todo se revierte.
        solicitudService.cambiarEstado(idServicio, new CambioEstadoRequest("CANCELADO", motivo, request.idMotivo()), userDetails);
        for (ServicioSubproceso sp : vigentes) {
            if (!noSePuedeCancelar(sp)) asignacionService.cancelarPorCancelacionDeLaSolicitud(sp, motivo, usuario);
        }
        log.info("Solicitud {} cancelada por {}: {} subprocesos cancelados", idServicio, usuario.getEmail(),
                vigentes.stream().filter(sp -> !noSePuedeCancelar(sp)).count());
    }

    /**
     * El candidato desistió. Si ningún subproceso está iniciado, todos se cancelan y la solicitud queda cancelada. Si alguno ya
     * inició, ese sigue hasta publicarse, los demás se cancelan y el servicio lleva la marca CANDIDATO_DESISTIO.
     */
    @Transactional
    public void registrarDesistimiento(Integer idServicio, DesistimientoRequest req, UserDetails userDetails) {
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getRol() != Rol.GESTOR && usuario.getRol() != Rol.ADMIN_POLYGRAPH) {
            throw new ApiException("Solo el gestor o el administrador registran el desistimiento del candidato.", HttpStatus.FORBIDDEN);
        }
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(servicio, usuario);
        if (servicio.getEstado() == EstadoServicio.CANCELADO) {
            throw new ApiException("La solicitud ya está cancelada.", HttpStatus.CONFLICT);
        }
        List<ServicioSubproceso> vigentes = vigentes(idServicio);
        if (vigentes.isEmpty()) throw new ApiException("La solicitud no tiene subprocesos vigentes.", HttpStatus.CONFLICT);

        String motivo = motivoService.resolver(req.idMotivo(), TipoMotivo.CANCELACION, req.texto()).descripcion();
        List<Boolean> iniciadosQueSiguen = new ArrayList<>();
        for (ServicioSubproceso sp : vigentes) {
            boolean iniciado = Boolean.TRUE.equals(sp.getIniciado());
            if (MaquinaEstadosSubproceso.alDesistir(sp.getEstado(), iniciado) == AlDesistir.CANCELAR) {
                asignacionService.cancelarPorCancelacionDeLaSolicitud(sp, "Candidato desistió: " + motivo, usuario);
            } else {
                iniciadosQueSiguen.add(true);
            }
        }
        if (MaquinaEstadosSubproceso.llevaMarcaDeDesistimiento(iniciadosQueSiguen)) {
            servicio.setCandidatoDesistio(true);
            servicioRepository.save(servicio);
        } else {
            solicitudService.cambiarEstado(idServicio, new CambioEstadoRequest("CANCELADO", "Candidato desistió: " + motivo, req.idMotivo()), userDetails);
        }
        log.info("Desistimiento del candidato en la solicitud {}: {} subprocesos siguen, registrado por {}", idServicio,
                iniciadosQueSiguen.size(), usuario.getEmail());
    }

    private List<ServicioSubproceso> vigentes(Integer idServicio) {
        return subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(idServicio).stream()
                .filter(sp -> sp.getEstado() != EstadoSubproceso.CANCELADO)
                .toList();
    }

    /** Lo iniciado o terminado no se cancela. */
    private static boolean noSePuedeCancelar(ServicioSubproceso sp) {
        return Boolean.TRUE.equals(sp.getIniciado()) || sp.getEstado() == EstadoSubproceso.FINALIZADO || sp.getEstado() == EstadoSubproceso.PUBLICADO;
    }
}
