package com.polygraph.erp.modules.gestor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Cada minuto avisa (una sola vez por cita) las citas que quedaron «Por confirmar» (SE-07). */
@Service
@RequiredArgsConstructor
@Slf4j
public class CitasPorConfirmarTareas {

    private final CitaSubprocesoService citaService;

    @Scheduled(fixedRate = 60_000, initialDelay = 90_000)
    public void avisarPorConfirmar() {
        try {
            citaService.avisarPorConfirmar();
        } catch (RuntimeException e) {
            log.error("No se pudieron avisar las citas por confirmar: {}", e.getMessage(), e);
        }
    }
}
