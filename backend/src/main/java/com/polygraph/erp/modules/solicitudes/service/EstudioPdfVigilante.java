package com.polygraph.erp.modules.solicitudes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** PC-01: un PDF del estudio que lleva más de {@link EmisionEstudioService#MINUTOS_MAXIMOS_GENERANDO} minutos en GENERANDO pasa a ERROR: el gestor lo ve y reintenta. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EstudioPdfVigilante {

    private final EmisionEstudioService emision;

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 90 * 1000)
    public void vigilar() {
        try {
            int n = emision.vencerLosQueTardan();
            if (n > 0) log.warn("{} PDF(s) del estudio pasaron a ERROR por tardar demasiado", n);
        } catch (Exception e) {
            log.error("No se pudo revisar los PDF del estudio que tardan: {}", e.getMessage(), e);
        }
    }
}
