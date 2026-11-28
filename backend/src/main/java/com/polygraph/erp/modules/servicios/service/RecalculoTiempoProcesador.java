package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.service.ProcesadorTrabajo;
import com.polygraph.erp.modules.trabajos.service.ResultadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** El trabajo RECALCULO_TIEMPO: vuelve a calcular el estado de tiempo de todas las solicitudes en curso. Idempotente. Si alguna solicitud falla,
 *  el trabajo termina con error (visible en Admin → Trabajos en segundo plano, con la lista de solicitudes) y se reintenta; las que sí se
 *  pudieron calcular ya quedaron guardadas. */
@Service
@RequiredArgsConstructor
public class RecalculoTiempoProcesador implements ProcesadorTrabajo {

    private final SemaforoTiempoService semaforoTiempoService;

    @Override
    public TipoTrabajo tipo() {
        return TipoTrabajo.RECALCULO_TIEMPO;
    }

    @Override
    public ResultadoTrabajo procesar(TrabajoAsincrono trabajo, Map<String, Object> parametros, ContextoTrabajo contexto) {
        SemaforoTiempoService.Resumen r = semaforoTiempoService.recalcularEnCurso();
        if (!r.errores().isEmpty()) {
            String detalle = String.join("; ", r.errores().stream().limit(20).toList());
            throw new IllegalStateException("El recálculo de tiempo falló en " + r.errores().size() + " de " + r.procesadas() + " solicitudes: " + detalle);
        }
        return ResultadoTrabajo.datos(Map.of("procesadas", r.procesadas(), "cambiadas", r.cambiadas(), "motivo", parametros.getOrDefault("motivo", "")));
    }
}
