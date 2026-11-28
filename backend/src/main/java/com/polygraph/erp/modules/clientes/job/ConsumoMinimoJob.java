package com.polygraph.erp.modules.clientes.job;

import com.polygraph.erp.modules.clientes.service.ConsumoMinimoService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TP-14: el día 1 de cada mes (hora de Bogotá) revisa el consumo de los meses completos anteriores. Solo avisa; no cambia nada. */
@Component
@RequiredArgsConstructor
public class ConsumoMinimoJob {

    private final ConsumoMinimoService servicio;

    @Scheduled(cron = "0 0 7 1 * *", zone = "America/Bogota")
    public void ejecutar() {
        servicio.ejecutar();
    }
}
