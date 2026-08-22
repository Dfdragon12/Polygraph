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
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
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
    private final TramoPrecioProcesoRepository tramoRepository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<ProcesoResponse> listarTodos() {
        return procesoRepository.findAll().stream()
                .map(p -> toResponse(p, pasoRepository.countByProceso_IdProceso(p.getIdProceso())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcesoPublicoResponse> listarActivos() {
        return procesoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .map(p -> new ProcesoPublicoResponse(
                        p.getIdProceso(),
                        p.getNombreProceso(),
                        p.getDescripcion(),
                        p.getClasificacion() != null ? p.getClasificacion().getNombre() : null,
                        p.getValor(),
                        p.getDiasHabilesEntrega(),
                        listarTramos(p.getIdProceso())))
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
                .diasHabilesEntrega(req.diasHabilesEntrega() != null ? req.diasHabilesEntrega() : 5)
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
        if (req.diasHabilesEntrega() != null) proceso.setDiasHabilesEntrega(req.diasHabilesEntrega());
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
        return pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(idProceso).stream()
                .map(this::toPasoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PasoProcesoPublicoResponse> obtenerPasosPublicos(Integer idProceso) {
        findProceso(idProceso);
        return pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(idProceso).stream()
                .filter(p -> Boolean.TRUE.equals(p.getHabilitado()) && Boolean.TRUE.equals(p.getTipoProgreso().getActivo()))
                .map(p -> new PasoProcesoPublicoResponse(
                        p.getTipoProgreso().getNombreProgreso(),
                        p.getTipoProgreso().getDescripcion(),
                        p.getTipoProgreso().getOrden(),
                        p.getObligatorio()))
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
                p.getActivo(), totalPasos, clsf, p.getValor(), valorCalculado,
                p.getDiasHabilesEntrega(), listarTramos(p.getIdProceso())
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

    // ---------- Tramos de precio por volumen ----------

    @Transactional(readOnly = true)
    public List<TramoPrecioResponse> listarTramos(Integer idProceso) {
        return tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaAsc(idProceso).stream()
                .map(this::toTramoResponse)
                .toList();
    }

    public TramoPrecioResponse agregarTramo(Integer idProceso, TramoPrecioRequest req) {
        Proceso proceso = findProceso(idProceso);
        if (tramoRepository.existsByProceso_IdProcesoAndCantidadMinima(idProceso, req.cantidadMinima())) {
            throw new ApiException("Ya existe un tramo para esa cantidad mínima", HttpStatus.CONFLICT);
        }
        validarValorTramo(proceso, req.valorUnitario());
        TramoPrecioProceso tramo = TramoPrecioProceso.builder()
                .proceso(proceso)
                .cantidadMinima(req.cantidadMinima())
                .valorUnitario(req.valorUnitario())
                .build();
        tramoRepository.save(tramo);
        log.info("Tramo de precio agregado: proceso={}, cantidadMinima={}, valorUnitario={}",
                proceso.getNombreProceso(), req.cantidadMinima(), req.valorUnitario());
        return toTramoResponse(tramo);
    }

    public TramoPrecioResponse actualizarTramo(Integer idTramo, TramoPrecioRequest req) {
        TramoPrecioProceso tramo = findTramo(idTramo);
        Integer idProceso = tramo.getProceso().getIdProceso();
        if (tramoRepository.existsByProceso_IdProcesoAndCantidadMinimaAndIdTramoNot(idProceso, req.cantidadMinima(), idTramo)) {
            throw new ApiException("Ya existe un tramo para esa cantidad mínima", HttpStatus.CONFLICT);
        }
        validarValorTramo(tramo.getProceso(), req.valorUnitario());
        tramo.setCantidadMinima(req.cantidadMinima());
        tramo.setValorUnitario(req.valorUnitario());
        log.info("Tramo de precio actualizado: id={}, proceso={}", idTramo, tramo.getProceso().getNombreProceso());
        return toTramoResponse(tramo);
    }

    public void eliminarTramo(Integer idTramo) {
        TramoPrecioProceso tramo = findTramo(idTramo);
        log.info("Tramo de precio eliminado: id={}, proceso={}", idTramo, tramo.getProceso().getNombreProceso());
        tramoRepository.delete(tramo);
    }

    private void validarValorTramo(Proceso proceso, BigDecimal valorUnitario) {
        if (proceso.getValor() != null && valorUnitario.compareTo(proceso.getValor()) >= 0) {
            throw new ApiException(
                    "El valor del tramo debe ser menor al valor base del proceso (" + proceso.getValor() + ")",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private TramoPrecioProceso findTramo(Integer id) {
        return tramoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Tramo de precio no encontrado", HttpStatus.NOT_FOUND));
    }

    private TramoPrecioResponse toTramoResponse(TramoPrecioProceso t) {
        return new TramoPrecioResponse(t.getIdTramo(), t.getCantidadMinima(), t.getValorUnitario());
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
                tipo.getOrden(),
                p.getHabilitado(),
                p.getObligatorio(),
                Boolean.TRUE.equals(tipo.getActivo())
        );
    }
}
