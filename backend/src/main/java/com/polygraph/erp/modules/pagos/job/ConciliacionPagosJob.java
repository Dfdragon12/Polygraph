package com.polygraph.erp.modules.pagos.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.modules.pagos.service.AuditoriaPagoService;
import com.polygraph.erp.modules.pagos.service.PagoWompiWebhookService;
import com.polygraph.erp.shared.enums.EstadoPago;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Red de seguridad para cuando un webhook de Wompi se pierde: cada 15 minutos revisa los pagos
 * que llevan PENDIENTE demasiado tiempo y consulta directamente a Wompi su estado real, usando
 * la misma lógica de aplicación que el webhook ({@link PagoWompiWebhookService#aplicarEstadoTransaccion})
 * para no duplicar reglas de negocio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConciliacionPagosJob {

    private static final Duration ANTIGUEDAD_MINIMA_PENDIENTE = Duration.ofMinutes(20);
    private static final Duration LIMITE_SIN_TRANSACCION = Duration.ofHours(24);

    private final PagoWompiRepository pagoWompiRepository;
    private final PagoWompiWebhookService pagoWompiWebhookService;
    private final AuditoriaPagoService auditoriaPagoService;
    private final WompiProperties wompiProperties;
    private final RestClient wompiRestClient;

    @Scheduled(cron = "0 */15 * * * *")
    public void conciliar() {
        LocalDateTime limiteAntiguedad = LocalDateTime.now().minus(ANTIGUEDAD_MINIMA_PENDIENTE);
        List<PagoWompi> pendientes = pagoWompiRepository.findByEstadoAndFechaCreacionBefore(EstadoPago.PENDIENTE, limiteAntiguedad);

        if (pendientes.isEmpty()) {
            log.info("Conciliación de pagos Wompi: sin pagos PENDIENTE vencidos");
            return;
        }

        log.info("Conciliación de pagos Wompi: {} pago(s) PENDIENTE con más de {} minutos — consultando estado remoto",
                pendientes.size(), ANTIGUEDAD_MINIMA_PENDIENTE.toMinutes());

        for (PagoWompi pago : pendientes) {
            try {
                conciliarUno(pago.getId());
            } catch (Exception e) {
                log.error("Conciliación de pagos Wompi: fallo procesando referencia={} — {}",
                        pago.getReferencia(), e.getClass().getSimpleName());
            }
        }
    }

    /** Se procesa cada pago en su propia transacción: que uno falle no debe afectar a los demás. */
    @Transactional
    public void conciliarUno(UUID idPago) {
        PagoWompi pago = pagoWompiRepository.findById(idPago).orElse(null);
        if (pago == null || pago.getEstado() != EstadoPago.PENDIENTE) {
            return; // ya cambió de estado (p. ej. el webhook llegó justo antes que este job)
        }

        if (pago.getTransaccionWompiId() == null) {
            marcarErrorSiExcedeLimiteSinTransaccion(pago);
            return;
        }

        JsonNode transaccion = consultarTransaccion(pago.getTransaccionWompiId());
        if (transaccion == null) {
            return; // fallo de red/HTTP ya logueado en consultarTransaccion; se reintenta en el próximo ciclo
        }

        String estadoWompi = transaccion.path("status").asText(null);
        Long montoRemoto = transaccion.hasNonNull("amount_in_cents") ? transaccion.path("amount_in_cents").asLong() : null;
        String metodoPago = transaccion.hasNonNull("payment_method_type") ? transaccion.path("payment_method_type").asText() : null;

        pagoWompiWebhookService.aplicarEstadoTransaccion(
                pago, pago.getTransaccionWompiId(), estadoWompi, montoRemoto, metodoPago, transaccion.toString(), null, null);
    }

    private void marcarErrorSiExcedeLimiteSinTransaccion(PagoWompi pago) {
        Duration antiguedad = Duration.between(pago.getFechaCreacion(), LocalDateTime.now());
        if (antiguedad.compareTo(LIMITE_SIN_TRANSACCION) > 0) {
            EstadoPago estadoAnterior = pago.getEstado();
            pago.setEstado(EstadoPago.ERROR);
            pagoWompiRepository.save(pago);
            log.warn("Conciliación de pagos Wompi: referencia={} lleva más de {}h PENDIENTE sin transaccionWompiId — marcado ERROR",
                    pago.getReferencia(), LIMITE_SIN_TRANSACCION.toHours());
            auditoriaPagoService.registrarCambioEstado(pago, estadoAnterior, EstadoPago.ERROR, null, null,
                    "Sin transaccionWompiId tras más de " + LIMITE_SIN_TRANSACCION.toHours() + "h en PENDIENTE");
        }
    }

    /** Nunca loguea la llave privada ni el body de la respuesta de Wompi, solo metadatos de la llamada. */
    private JsonNode consultarTransaccion(String transaccionWompiId) {
        try {
            JsonNode respuesta = wompiRestClient.get()
                    .uri("/transactions/{id}", transaccionWompiId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + wompiProperties.getPrivateKey())
                    .retrieve()
                    .body(JsonNode.class);
            return respuesta != null ? respuesta.path("data") : null;
        } catch (RestClientResponseException e) {
            log.error("Conciliación de pagos Wompi: Wompi respondió {} consultando transacción {}",
                    e.getStatusCode().value(), transaccionWompiId);
            return null;
        } catch (RestClientException e) {
            log.error("Conciliación de pagos Wompi: fallo de red consultando transacción {} — {}",
                    transaccionWompiId, e.getClass().getSimpleName());
            return null;
        }
    }
}
