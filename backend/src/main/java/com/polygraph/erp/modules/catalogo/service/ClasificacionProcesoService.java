package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.ClasificacionProcesoRequest;
import com.polygraph.erp.modules.catalogo.dto.ClasificacionProcesoResponse;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.repository.ClasificacionProcesoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClasificacionProcesoService {

    private final ClasificacionProcesoRepository repository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<ClasificacionProcesoResponse> listarTodas() {
        return repository.findAllByOrderByCodigoAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ClasificacionProcesoResponse crear(ClasificacionProcesoRequest req) {
        String codigoUpper = req.codigo().toUpperCase();
        if (repository.existsByCodigo(codigoUpper)) {
            throw new ApiException("Ya existe una clasificación con ese código", HttpStatus.CONFLICT);
        }
        if (repository.existsByNombre(req.nombre())) {
            throw new ApiException("Ya existe una clasificación con ese nombre", HttpStatus.CONFLICT);
        }
        ClasificacionProceso clasificacion = ClasificacionProceso.builder()
                .codigo(codigoUpper)
                .nombre(req.nombre())
                .descripcion(req.descripcion())
                .activo(true)
                .build();
        repository.save(clasificacion);
        log.info("Clasificación de proceso creada: {} - {}", clasificacion.getCodigo(), clasificacion.getNombre());
        notificacionService.crearParaAdmins("CATALOGO", "Clasificación creada",
                "Se creó la clasificación \"" + clasificacion.getNombre() + "\"");
        return toResponse(clasificacion);
    }

    public ClasificacionProcesoResponse actualizar(Integer id, ClasificacionProcesoRequest req) {
        ClasificacionProceso clasificacion = findById(id);
        String codigoUpper = req.codigo().toUpperCase();
        if (repository.existsByCodigoAndIdClasificacionNot(codigoUpper, id)) {
            throw new ApiException("Ya existe una clasificación con ese código", HttpStatus.CONFLICT);
        }
        if (repository.existsByNombreAndIdClasificacionNot(req.nombre(), id)) {
            throw new ApiException("Ya existe una clasificación con ese nombre", HttpStatus.CONFLICT);
        }
        clasificacion.setCodigo(codigoUpper);
        clasificacion.setNombre(req.nombre());
        clasificacion.setDescripcion(req.descripcion());
        log.info("Clasificación actualizada: {}", clasificacion.getNombre());
        notificacionService.crearParaAdmins("CATALOGO", "Clasificación actualizada",
                "Se actualizó la clasificación \"" + clasificacion.getNombre() + "\"");
        return toResponse(clasificacion);
    }

    public ClasificacionProcesoResponse cambiarEstado(Integer id, boolean activo) {
        ClasificacionProceso clasificacion = findById(id);
        clasificacion.setActivo(activo);
        notificacionService.crearParaAdmins("CATALOGO",
                activo ? "Clasificación activada" : "Clasificación desactivada",
                "La clasificación \"" + clasificacion.getNombre() + "\" fue " + (activo ? "activada" : "desactivada"));
        return toResponse(clasificacion);
    }

    public ClasificacionProcesoResponse findByIdPublic(Integer id) {
        return toResponse(findById(id));
    }

    public ClasificacionProceso findById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException("Clasificación no encontrada", HttpStatus.NOT_FOUND));
    }

    private ClasificacionProcesoResponse toResponse(ClasificacionProceso c) {
        return new ClasificacionProcesoResponse(
                c.getIdClasificacion(), c.getCodigo(), c.getNombre(), c.getDescripcion(), c.getActivo()
        );
    }
}
