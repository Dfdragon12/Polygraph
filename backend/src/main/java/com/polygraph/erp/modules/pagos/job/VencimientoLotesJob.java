package com.polygraph.erp.modules.pagos.job;

import com.polygraph.erp.modules.pagos.service.VencimientoLotesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TP-07: cada hora (hora de Bogotá) pasa a «vencidas» las unidades de los lotes que ya vencieron y avisa los que están por vencer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VencimientoLotesJob {

    private final VencimientoLotesService servicio;

    @Scheduled(cron = "0 10 * * * *", zone = "America/Bogota")
    public void ejecutar() {
        int unidades = servicio.ejecutar();
        if (unidades > 0) log.info("Vencimiento de lotes: {} unidad(es) pasaron a vencidas", unidades);
    }
}
