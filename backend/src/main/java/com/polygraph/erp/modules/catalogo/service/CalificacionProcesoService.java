package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.CalificacionProcesoResponse;
import com.polygraph.erp.modules.catalogo.dto.GuardarCalificacionProcesoRequest;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pesos de calificación de riesgo por subproceso dentro de cada proceso (catálogo), más la
 * cobertura mínima y la criticidad por defecto — ver docs/modelo-calificacion-riesgo.md §2.
 * Reglas: la suma de los subprocesos que cuentan (habilitados y activos) no puede pasar de 100;
 * el proceso solo califica cuando suma exactamente 100 y ninguno está sin configurar (null).
 * Peso 0 = el subproceso se hace pero no califica.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalificacionProcesoService {

    static final BigDecimal CIEN = new BigDecimal("100.00");

    private final ProcesoRepository procesoRepository;
    private final ProcesoTipoProgresoRepository pasoRepository;
    private final NotificacionService notificacionService;

    /** Resumen para la lista del catálogo (etiqueta "✓ 100 %" / "⚠ 80 %"). */
    public record ResumenPesos(BigDecimal suma, boolean califica, String motivo) {}

    @Transactional(readOnly = true)
    public CalificacionProcesoResponse obtener(Integer idProceso) {
        Proceso proceso = proceso(idProceso);
        return toResponse(proceso, pasos(idProceso));
    }

    @Transactional
    public CalificacionProcesoResponse guardar(Integer idProceso, GuardarCalificacionProcesoRequest req) {
        Proceso proceso = proceso(idProceso);
        CriticidadCargo criticidad;
        try {
            criticidad = CriticidadCargo.valueOf(req.criticidadDefecto());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Criticidad no válida.", HttpStatus.BAD_REQUEST);
        }

        List<ProcesoTipoProgreso> pasos = pasos(idProceso);
        Map<Integer, ProcesoTipoProgreso> porId = pasos.stream()
                .collect(Collectors.toMap(ProcesoTipoProgreso::getId, Function.identity()));

        boolean cambio = proceso.getCriticidadDefecto() != criticidad
                || proceso.getCoberturaMinima().compareTo(req.coberturaMinima()) != 0;
        for (GuardarCalificacionProcesoRequest.PesoSubproceso item : req.subprocesos()) {
            ProcesoTipoProgreso paso = porId.get(item.idPaso());
            if (paso == null) {
                throw new ApiException("Uno de los subprocesos no pertenece a este proceso.", HttpStatus.BAD_REQUEST);
            }
            BigDecimal peso = item.peso() != null ? item.peso().setScale(2, RoundingMode.HALF_UP) : null;
            if (!mismoPeso(paso.getPeso(), peso)) { paso.setPeso(peso); cambio = true; }
            if (item.obligatorio() != null && !item.obligatorio().equals(paso.getObligatorio())) {
                paso.setObligatorio(item.obligatorio());
                cambio = true;
            }
        }

        BigDecimal suma = suma(pasos);
        if (suma.compareTo(CIEN) > 0) {
            throw new ApiException("La suma de pesos quedaría en " + formato(suma) + " % — no puede pasar de 100 %.",
                    HttpStatus.BAD_REQUEST);
        }

        proceso.setCriticidadDefecto(criticidad);
        proceso.setCoberturaMinima(req.coberturaMinima().setScale(2, RoundingMode.HALF_UP));
        if (cambio) {
            proceso.setVersionCalificacion(proceso.getVersionCalificacion() + 1);
            ResumenPesos r = resumen(pasos);
            notificacionService.crearParaAdmins("CATALOGO", "Calificación de riesgo actualizada",
                    "\"" + proceso.getNombreProceso() + "\": pesos " + formato(r.suma()) + " %"
                            + (r.califica() ? " (califica)." : " — " + r.motivo() + "."));
            log.info("Calificación del proceso {} actualizada: suma={} califica={} version={}",
                    idProceso, r.suma(), r.califica(), proceso.getVersionCalificacion());
        }
        return toResponse(proceso, pasos);
    }

    @Transactional(readOnly = true)
    public ResumenPesos resumen(Integer idProceso) {
        return resumen(pasos(idProceso));
    }

    /** Lo llama ProcesoService cuando se agrega, quita, habilita o deshabilita un subproceso: la
     *  composición cambió, así que cambia la versión de calificación; y si al habilitar uno la
     *  suma pasa de 100, se rechaza el cambio. */
    @Transactional
    public void trasCambioDeComposicion(Proceso proceso) {
        BigDecimal suma = suma(pasos(proceso.getIdProceso()));
        if (suma.compareTo(CIEN) > 0) {
            throw new ApiException("Con este cambio los pesos sumarían " + formato(suma)
                    + " % — ajusta los pesos del proceso primero (máximo 100 %).", HttpStatus.BAD_REQUEST);
        }
        proceso.setVersionCalificacion(proceso.getVersionCalificacion() + 1);
    }

    // ───────────────────────────── Cálculo ─────────────────────────────

    static boolean cuenta(ProcesoTipoProgreso p) {
        return Boolean.TRUE.equals(p.getHabilitado()) && p.getTipoProgreso() != null
                && Boolean.TRUE.equals(p.getTipoProgreso().getActivo());
    }

    static BigDecimal suma(List<ProcesoTipoProgreso> pasos) {
        return pasos.stream().filter(CalificacionProcesoService::cuenta)
                .map(ProcesoTipoProgreso::getPeso).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    static ResumenPesos resumen(List<ProcesoTipoProgreso> pasos) {
        List<ProcesoTipoProgreso> cuentan = pasos.stream().filter(CalificacionProcesoService::cuenta).toList();
        BigDecimal suma = suma(pasos);
        if (cuentan.isEmpty()) return new ResumenPesos(suma, false, "No tiene subprocesos habilitados");
        long sinPeso = cuentan.stream().filter(p -> p.getPeso() == null).count();
        if (sinPeso > 0) {
            return new ResumenPesos(suma, false, sinPeso == 1 ? "1 subproceso sin peso" : sinPeso + " subprocesos sin peso");
        }
        if (suma.compareTo(CIEN) != 0) {
            return new ResumenPesos(suma, false, "Los pesos suman " + formato(suma) + " % (deben sumar 100 %)");
        }
        return new ResumenPesos(suma, true, null);
    }

    private CalificacionProcesoResponse toResponse(Proceso proceso, List<ProcesoTipoProgreso> pasos) {
        ResumenPesos r = resumen(pasos);
        return new CalificacionProcesoResponse(
                proceso.getIdProceso(), proceso.getNombreProceso(), proceso.getCriticidadDefecto().name(),
                proceso.getCoberturaMinima(), proceso.getVersionCalificacion(), r.suma(), r.califica(), r.motivo(),
                pasos.stream().map(p -> new CalificacionProcesoResponse.SubprocesoPeso(
                        p.getId(), p.getTipoProgreso().getIdTipoProgreso(), p.getTipoProgreso().getNombreProgreso(),
                        p.getPeso(), Boolean.TRUE.equals(p.getObligatorio()), Boolean.TRUE.equals(p.getHabilitado()),
                        Boolean.TRUE.equals(p.getTipoProgreso().getActivo()), cuenta(p))).toList());
    }

    private List<ProcesoTipoProgreso> pasos(Integer idProceso) {
        return pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(idProceso);
    }

    private Proceso proceso(Integer id) {
        return procesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Proceso no encontrado", HttpStatus.NOT_FOUND));
    }

    private static boolean mismoPeso(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** 95.00 -> "95"; 33.33 -> "33,33". */
    static String formato(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
