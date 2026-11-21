package com.polygraph.erp.modules.pagos.job;

import com.polygraph.erp.modules.pagos.service.MoraService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TP-14: cada día (hora de Bogotá), después de marcar las facturas vencidas, recalcula el estado de mora de todos los clientes pospago.
 * Solo con el interruptor moraAutomatica encendido. Nunca suspende: la suspensión es una decisión manual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MoraAutomaticaJob {

    private final MoraService moraService;
    private final ParametrosTiendaService parametros;

    @Scheduled(cron = "0 15 6 * * *", zone = "America/Bogota")
    public void ejecutar() {
        if (!parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)) return;
        int n = moraService.sincronizarTodos();
        log.info("Mora diaria: {} cliente(s) pospago recalculados", n);
    }
}
