package com.polygraph.erp.modules.riesgo.service;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.repository.CalificacionRiesgoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * SE-06, una sola vez al arrancar: antes de esta regla, un estudio al que le faltaba la criticidad del cargo quedaba «CALCULADO» con
 * puntaje 0 (se veía «BAJO 0»). Se recalculan los que todavía no están bloqueados por una publicación: con la regla nueva vuelven a
 * «EN_PROCESO» («Sin calificar»). Idempotente: si ya no queda ninguno, no hace nada. Los bloqueados (publicados) no se tocan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevisionRiesgoSinCalcular {

    private final CalificacionRiesgoRepository calificacionRepository;
    private final CalificacionRiesgoService calificacionRiesgoService;
    private final PlatformTransactionManager transacciones;

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            revisar();
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar el riesgo calculado en cero: {}", e.getMessage(), e);
        }
    }

    /** Devuelve los ids de los servicios que se recalcularon. */
    public List<Integer> revisar() {
        List<Integer> ids = new TransactionTemplate(transacciones).execute(st ->
                calificacionRepository.findByVigenteTrueAndEstadoAndBloqueadoFalse(EstadoResultado.CALCULADO).stream()
                        .filter(c -> c.getPuntajeFinal() != null && c.getPuntajeFinal().compareTo(BigDecimal.ZERO) == 0)
                        .map(c -> c.getServicio().getIdServicio()).toList());
        if (ids == null || ids.isEmpty()) return List.of();
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        for (Integer id : ids) {
            try {
                tx.executeWithoutResult(st -> calificacionRiesgoService.recalcular(id, null));
            } catch (RuntimeException e) {
                log.warn("No se pudo recalcular el riesgo del servicio {}: {}", id, e.getMessage());
            }
        }
        log.info("Riesgo en cero revisado (SE-06): {} servicios recalculados {}", ids.size(), ids);
        return ids;
    }
}
