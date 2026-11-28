package com.polygraph.erp.modules.pagos.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.modules.pagos.service.AuditoriaPagoService;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import com.polygraph.erp.modules.pagos.service.PagoWompiWebhookService;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
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
 * Red de seguridad para cuando un webhook de Wompi se pierde (o nunca se configuró en el panel de
 * Wompi — no depende de eso para funcionar): cada 15 minutos revisa los pagos que llevan PENDIENTE
 * demasiado tiempo, tanto del flujo genérico (PagoWompi — facturas pospago) como del carrito
 * prepago (OrdenCompra), y consulta directamente a Wompi su estado real — primero por el id de
 * transacción si ya se conoce, y si no (el webhook nunca llegó ni una vez), por la referencia, que
 * sí conocemos desde que se creó el pago. Usa la misma lógica de aplicación que el webhook
 * ({@link PagoWompiWebhookService#aplicarEstadoTransaccion} / {@link OrdenCompraService#aplicarEventoTransaccion})
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
    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraService ordenCompraService;
    private final AuditoriaPagoService auditoriaPagoService;
    private final WompiProperties wompiProperties;
    private final RestClient wompiRestClient;
    private final NotificacionService notificacionService;

    @Scheduled(cron = "0 */15 * * * *")
    public void conciliar() {
        conciliarPagosGenericos();
        conciliarOrdenesCompra();
    }

    // ---------- Flujo genérico (PagoWompi — facturas pospago) ----------

    private void conciliarPagosGenericos() {
        LocalDateTime limiteAntiguedad = LocalDateTime.now().minus(ANTIGUEDAD_MINIMA_PENDIENTE);
        List<PagoWompi> pendientes = pagoWompiRepository.findByEstadoAndFechaCreacionBefore(EstadoPago.PENDIENTE, limiteAntiguedad);

        if (pendientes.isEmpty()) {
            log.info("Conciliación de pagos Wompi: sin pagos genéricos PENDIENTE vencidos");
            return;
        }

        log.info("Conciliación de pagos Wompi: {} pago(s) genérico(s) PENDIENTE con más de {} minutos — consultando estado remoto",
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
            JsonNode transaccion = consultarPorReferencia(pago.getReferencia());
            if (transaccion == null) {
                marcarErrorSiExcedeLimiteSinTransaccion(pago);
                return;
            }
            aplicarTransaccionAPago(pago, transaccion);
            return;
        }

        JsonNode transaccion = consultarPorId(pago.getTransaccionWompiId());
        if (transaccion == null) {
            return; // fallo de red/HTTP ya logueado en consultarPorId; se reintenta en el próximo ciclo
        }
        aplicarTransaccionAPago(pago, transaccion);
    }

    private void aplicarTransaccionAPago(PagoWompi pago, JsonNode transaccion) {
        String idTransaccionWompi = transaccion.path("id").asText(null);
        String estadoWompi = transaccion.path("status").asText(null);
        Long montoRemoto = transaccion.hasNonNull("amount_in_cents") ? transaccion.path("amount_in_cents").asLong() : null;
        String metodoPago = transaccion.hasNonNull("payment_method_type") ? transaccion.path("payment_method_type").asText() : null;

        pagoWompiWebhookService.aplicarEstadoTransaccion(
                pago, idTransaccionWompi, estadoWompi, montoRemoto, metodoPago, transaccion.toString(), null, null);
    }

    private void marcarErrorSiExcedeLimiteSinTransaccion(PagoWompi pago) {
        Duration antiguedad = Duration.between(pago.getFechaCreacion(), LocalDateTime.now());
        if (antiguedad.compareTo(LIMITE_SIN_TRANSACCION) > 0) {
            EstadoPago estadoAnterior = pago.getEstado();
            pago.setEstado(EstadoPago.ERROR);
            pagoWompiRepository.save(pago);
            log.warn("Conciliación de pagos Wompi: referencia={} lleva más de {}h PENDIENTE sin encontrarse en Wompi — marcado ERROR",
                    pago.getReferencia(), LIMITE_SIN_TRANSACCION.toHours());
            auditoriaPagoService.registrarCambioEstado(pago, estadoAnterior, EstadoPago.ERROR, null, null,
                    "Sin transacción encontrada en Wompi tras más de " + LIMITE_SIN_TRANSACCION.toHours() + "h en PENDIENTE");
            // Antes esto solo quedaba en logs/auditoría — ningún admin se enteraba salvo que
            // revisara la tabla a mano.
            notificacionService.crearParaAdmins("FACTURACION", "Pago sin conciliar",
                    "La referencia " + pago.getReferencia() + " lleva más de " + LIMITE_SIN_TRANSACCION.toHours()
                            + "h sin encontrarse en Wompi — quedó en ERROR, revisar manualmente.");
        }
    }

    // ---------- Flujo del carrito prepago (OrdenCompra) ----------

    private void conciliarOrdenesCompra() {
        LocalDateTime limiteAntiguedad = LocalDateTime.now().minus(ANTIGUEDAD_MINIMA_PENDIENTE);
        List<OrdenCompra> pendientes = ordenCompraRepository.findByEstadoAndFechaCreacionBefore(EstadoOrdenCompra.PENDIENTE, limiteAntiguedad);

        if (pendientes.isEmpty()) {
            log.info("Conciliación de pagos Wompi: sin órdenes de compra PENDIENTE vencidas");
            return;
        }

        log.info("Conciliación de pagos Wompi: {} orden(es) de compra PENDIENTE con más de {} minutos — consultando estado remoto",
                pendientes.size(), ANTIGUEDAD_MINIMA_PENDIENTE.toMinutes());

        for (OrdenCompra orden : pendientes) {
            try {
                conciliarOrdenUna(orden.getIdOrdenCompra());
            } catch (Exception e) {
                log.error("Conciliación de pagos Wompi: fallo procesando orden referencia={} — {}",
                        orden.getReferencia(), e.getClass().getSimpleName());
            }
        }
    }

    @Transactional
    public void conciliarOrdenUna(Long idOrdenCompra) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrdenCompra).orElse(null);
        if (orden == null || orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            return;
        }

        JsonNode transaccion = orden.getIdTransaccionPasarela() != null
                ? consultarPorId(orden.getIdTransaccionPasarela())
                : consultarPorReferencia(orden.getReferencia());
        if (transaccion == null) {
            return; // sin rastro en Wompi todavía (o fallo de red ya logueado) — se reintenta en el próximo ciclo
        }

        String idTransaccionWompi = transaccion.path("id").asText(null);
        String estadoWompi = transaccion.path("status").asText(null);
        if (idTransaccionWompi == null) {
            return;
        }

        boolean aplicado = ordenCompraService.aplicarEventoTransaccion(
                transaccion, idTransaccionWompi, orden.getReferencia(), estadoWompi, transaccion.toString());
        if (aplicado) {
            log.info("Conciliación de pagos Wompi: orden={} reconciliada por consulta directa, estado remoto={}",
                    orden.getIdOrdenCompra(), estadoWompi);
        }
    }

    // ---------- Consultas a Wompi (nunca loguean la llave privada ni el body de la respuesta) ----------

    private JsonNode consultarPorId(String transaccionWompiId) {
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

    /** Recuperación cuando nunca llegó ningún webhook — no conocemos el id de Wompi, solo la referencia que nosotros generamos. */
    private JsonNode consultarPorReferencia(String referencia) {
        try {
            JsonNode respuesta = wompiRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/transactions").queryParam("reference", referencia).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + wompiProperties.getPrivateKey())
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null) return null;

            // Puede haber varias transacciones para la misma referencia (reintentos del usuario en
            // el checkout) — se prioriza una ya aprobada; si ninguna, la más reciente de la lista.
            JsonNode aprobada = null;
            JsonNode primera = null;
            for (JsonNode transaccion : respuesta.path("data")) {
                if (primera == null) primera = transaccion;
                if ("APPROVED".equals(transaccion.path("status").asText(null))) {
                    aprobada = transaccion;
                    break;
                }
            }
            return aprobada != null ? aprobada : primera;
        } catch (RestClientResponseException e) {
            log.error("Conciliación de pagos Wompi: Wompi respondió {} consultando referencia {}",
                    e.getStatusCode().value(), referencia);
            return null;
        } catch (RestClientException e) {
            log.error("Conciliación de pagos Wompi: fallo de red consultando referencia {} — {}",
                    referencia, e.getClass().getSimpleName());
            return null;
        }
    }
}
