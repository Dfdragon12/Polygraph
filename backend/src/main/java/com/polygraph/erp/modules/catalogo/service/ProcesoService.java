package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.*;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ClasificacionProcesoRepository;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final ClasificacionProcesoRepository clasificacionRepository;
    private final ProcesoTipoProgresoRepository pasoRepository;
    private final TipoProgresoRepository tipoProgresoRepository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<ProcesoResponse> listarTodos() {
        return procesoRepository.findAll().stream()
                .map(p -> toResponse(p, pasoRepository.countByProceso_IdProceso(p.getIdProceso())))
                .toList();
    }

    public ProcesoResponse crear(ProcesoRequest req) {
        if (procesoRepository.existsByNombreProceso(req.nombreProceso())) {
            throw new ApiException("Ya existe un proceso con ese nombre", HttpStatus.CONFLICT);
        }
        ClasificacionProceso clasificacion = findClasificacion(req.idClasificacion());
        Proceso proceso = Proceso.builder()
                .nombreProceso(req.nombreProceso())
                .descripcion(req.descripcion())
                .clasificacion(clasificacion)
                .valor(req.valor())
                .activo(true)
                .build();
        procesoRepository.save(proceso);
        log.info("Proceso creado: {}", proceso.getNombreProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Proceso creado",
                "Se creó el proceso \"" + proceso.getNombreProceso() + "\"");
        return toResponse(proceso, 0L);
    }

    public ProcesoResponse actualizar(Integer id, ProcesoRequest req) {
        Proceso proceso = findProceso(id);
        if (procesoRepository.existsByNombreProcesoAndIdProcesoNot(req.nombreProceso(), id)) {
            throw new ApiException("Ya existe un proceso con ese nombre", HttpStatus.CONFLICT);
        }
        ClasificacionProceso clasificacion = findClasificacion(req.idClasificacion());
        proceso.setNombreProceso(req.nombreProceso());
        proceso.setDescripcion(req.descripcion());
        proceso.setClasificacion(clasificacion);
        proceso.setValor(req.valor());
        log.info("Proceso actualizado: {}", proceso.getNombreProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Proceso actualizado",
                "Se actualizó el proceso \"" + proceso.getNombreProceso() + "\"");
        return toResponse(proceso, pasoRepository.countByProceso_IdProceso(id));
    }

    public ProcesoResponse cambiarEstado(Integer id, boolean activo) {
        Proceso proceso = findProceso(id);
        proceso.setActivo(activo);
        notificacionService.crearParaAdmins("CATALOGO",
                activo ? "Proceso activado" : "Proceso desactivado",
                "El proceso \"" + proceso.getNombreProceso() + "\" fue " + (activo ? "activado" : "desactivado"));
        return toResponse(proceso, pasoRepository.countByProceso_IdProceso(id));
    }

    @Transactional(readOnly = true)
    public List<PasoProcesoResponse> obtenerPasos(Integer idProceso) {
        findProceso(idProceso);
        return pasoRepository.findByProceso_IdProcesoOrderByOrdenEnProcesoAsc(idProceso).stream()
                .map(this::toPasoResponse)
                .toList();
    }

    public PasoProcesoResponse asignarPaso(Integer idProceso, PasoProcesoRequest req) {
        Proceso proceso = findProceso(idProceso);
        TipoProgreso tipo = tipoProgresoRepository.findById(req.idTipoProgreso())
                .orElseThrow(() -> new ApiException("Tipo de progreso no encontrado", HttpStatus.NOT_FOUND));

        if (pasoRepository.existsByProceso_IdProcesoAndTipoProgreso_IdTipoProgreso(idProceso, req.idTipoProgreso())) {
            throw new ApiException("Ese tipo de progreso ya está asignado a este proceso", HttpStatus.CONFLICT);
        }

        ProcesoTipoProgreso paso = ProcesoTipoProgreso.builder()
                .proceso(proceso)
                .tipoProgreso(tipo)
                .ordenEnProceso(req.ordenEnProceso())
                .habilitado(req.habilitado() != null ? req.habilitado() : true)
                .obligatorio(req.obligatorio() != null ? req.obligatorio() : true)
                .build();
        pasoRepository.save(paso);
        log.info("Paso '{}' asignado al proceso '{}'", tipo.getNombreProgreso(), proceso.getNombreProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Progreso asignado",
                "\"" + tipo.getNombreProgreso() + "\" fue asignado al proceso \"" + proceso.getNombreProceso() + "\"");
        return toPasoResponse(paso);
    }

    public PasoProcesoResponse actualizarPaso(Integer pasoId, PasoProcesoRequest req) {
        ProcesoTipoProgreso paso = findPaso(pasoId);
        String nombreProgreso = paso.getTipoProgreso().getNombreProgreso();
        String nombreProceso  = paso.getProceso().getNombreProceso();
        paso.setOrdenEnProceso(req.ordenEnProceso());
        if (req.habilitado() != null) paso.setHabilitado(req.habilitado());
        if (req.obligatorio() != null) paso.setObligatorio(req.obligatorio());
        notificacionService.crearParaAdmins("CATALOGO", "Configuración de progreso actualizada",
                "Se actualizó \"" + nombreProgreso + "\" en el proceso \"" + nombreProceso + "\"");
        return toPasoResponse(paso);
    }

    public void eliminarPaso(Integer pasoId) {
        ProcesoTipoProgreso paso = findPaso(pasoId);
        String nombreProgreso = paso.getTipoProgreso().getNombreProgreso();
        String nombreProceso  = paso.getProceso().getNombreProceso();
        pasoRepository.delete(paso);
        log.info("Paso {} eliminado del proceso '{}'", nombreProgreso, nombreProceso);
        notificacionService.crearParaAdmins("CATALOGO", "Progreso removido",
                "\"" + nombreProgreso + "\" fue removido del proceso \"" + nombreProceso + "\"");
    }

    private ProcesoResponse toResponse(Proceso p, long totalPasos) {
        ClasificacionProcesoResponse clsf = null;
        if (p.getClasificacion() != null) {
            ClasificacionProceso c = p.getClasificacion();
            clsf = new ClasificacionProcesoResponse(
                    c.getIdClasificacion(), c.getCodigo(), c.getNombre(), c.getDescripcion(), c.getActivo()
            );
        }
        BigDecimal valorCalculado = pasoRepository.sumValorHabilitadosByProcesoId(p.getIdProceso());
        return new ProcesoResponse(
                p.getIdProceso(), p.getNombreProceso(), p.getDescripcion(),
                p.getActivo(), totalPasos, clsf, p.getValor(), valorCalculado
        );
    }

    private Proceso findProceso(Integer id) {
        return procesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Proceso no encontrado", HttpStatus.NOT_FOUND));
    }

    private ClasificacionProceso findClasificacion(Integer idClasificacion) {
        return clasificacionRepository.findById(idClasificacion)
                .orElseThrow(() -> new ApiException("Clasificación no encontrada", HttpStatus.NOT_FOUND));
    }

    private ProcesoTipoProgreso findPaso(Integer id) {
        return pasoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Paso no encontrado", HttpStatus.NOT_FOUND));
    }

    private PasoProcesoResponse toPasoResponse(ProcesoTipoProgreso p) {
        TipoProgreso tipo = p.getTipoProgreso();
        return new PasoProcesoResponse(
                p.getId(),
                tipo.getIdTipoProgreso(),
                tipo.getNombreProgreso(),
                tipo.getDescripcion(),
                p.getOrdenEnProceso(),
                p.getHabilitado(),
                p.getObligatorio(),
                Boolean.TRUE.equals(tipo.getActivo())
        );
    }
}
