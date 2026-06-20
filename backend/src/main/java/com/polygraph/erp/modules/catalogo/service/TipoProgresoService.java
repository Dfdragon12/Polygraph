package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.ImpactoProgresoResponse;
import com.polygraph.erp.modules.catalogo.dto.TipoProgresoRequest;
import com.polygraph.erp.modules.catalogo.dto.TipoProgresoResponse;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
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
public class TipoProgresoService {

    private final TipoProgresoRepository repository;
    private final ProcesoTipoProgresoRepository pasoRepository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<TipoProgresoResponse> listarTodos() {
        return repository.findAllByOrderByOrdenAscNombreProgresoAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImpactoProgresoResponse obtenerImpacto(Integer idTipoProgreso) {
        findById(idTipoProgreso);
        List<String> afectados = pasoRepository.findByTipoProgreso_IdTipoProgreso(idTipoProgreso)
                .stream()
                .map(p -> p.getProceso().getNombreProceso())
                .distinct()
                .sorted()
                .toList();
        return new ImpactoProgresoResponse(afectados, afectados.size());
    }

    public TipoProgresoResponse crear(TipoProgresoRequest req) {
        if (repository.existsByNombreProgreso(req.nombreProgreso())) {
            throw new ApiException("Ya existe un tipo de progreso con ese nombre", HttpStatus.CONFLICT);
        }
        TipoProgreso tipo = TipoProgreso.builder()
                .nombreProgreso(req.nombreProgreso())
                .descripcion(req.descripcion())
                .orden(req.orden() != null ? req.orden() : 0)
                .valor(req.valor())
                .activo(true)
                .build();
        repository.save(tipo);
        log.info("Progreso creado: {}", tipo.getNombreProgreso());
        notificacionService.crearParaAdmins("CATALOGO", "Progreso creado",
                "Se creó el progreso \"" + tipo.getNombreProgreso() + "\"");
        return toResponse(tipo);
    }

    public TipoProgresoResponse actualizar(Integer id, TipoProgresoRequest req) {
        TipoProgreso tipo = findById(id);
        if (repository.existsByNombreProgresoAndIdTipoProgresoNot(req.nombreProgreso(), id)) {
            throw new ApiException("Ya existe un tipo de progreso con ese nombre", HttpStatus.CONFLICT);
        }
        tipo.setNombreProgreso(req.nombreProgreso());
        tipo.setDescripcion(req.descripcion());
        if (req.orden() != null) tipo.setOrden(req.orden());
        tipo.setValor(req.valor());
        log.info("Progreso actualizado: {}", tipo.getNombreProgreso());
        notificacionService.crearParaAdmins("CATALOGO", "Progreso actualizado",
                "Se actualizó el progreso \"" + tipo.getNombreProgreso() + "\"");
        return toResponse(tipo);
    }

    public TipoProgresoResponse cambiarEstado(Integer id, boolean activo) {
        TipoProgreso tipo = findById(id);
        tipo.setActivo(activo);
        if (!activo) {
            pasoRepository.findByTipoProgreso_IdTipoProgreso(id)
                    .forEach(paso -> paso.setHabilitado(false));
            log.info("Progreso '{}' desactivado — pasos en procesos deshabilitados", tipo.getNombreProgreso());
        }
        notificacionService.crearParaAdmins("CATALOGO",
                activo ? "Progreso activado" : "Progreso desactivado",
                "El progreso \"" + tipo.getNombreProgreso() + "\" fue " + (activo ? "activado" : "desactivado"));
        return toResponse(tipo);
    }

    private TipoProgreso findById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException("Progreso no encontrado", HttpStatus.NOT_FOUND));
    }

    private TipoProgresoResponse toResponse(TipoProgreso t) {
        return new TipoProgresoResponse(
                t.getIdTipoProgreso(), t.getNombreProgreso(),
                t.getDescripcion(), t.getOrden(), t.getActivo(), t.getValor()
        );
    }
}
