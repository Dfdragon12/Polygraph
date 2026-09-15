package com.polygraph.erp.modules.pagos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.modules.pagos.service.AuditoriaPagoService;
import com.polygraph.erp.modules.pagos.service.OrdenCompraService;
import com.polygraph.erp.modules.pagos.service.PagoWompiWebhookService;
import com.polygraph.erp.modules.pagos.service.WompiSignatureService;
import com.polygraph.erp.shared.enums.EstadoPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc standalone (sin ApplicationContext ni filtros de seguridad): la ruta permitAll ya se
 * declara en SecurityConfig, así que aquí se prueba el controller + PagoWompiWebhookService +
 * WompiSignatureService reales, con los repositorios mockeados. El checksum de cada request se
 * calcula con el mismo algoritmo de Wompi, de forma independiente al código de producción.
 *
 * Esta es la ÚNICA URL de webhook de Wompi en el sistema (/api/v1/pagos/wompi/webhook, la
 * registrada en el panel de Wompi) — procesa tanto pagos del flujo genérico (PagoWompi, facturas
 * pospago) como del carrito prepago (OrdenCompra), ver los casos de "fallback" más abajo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WompiWebhookController — único webhook de Wompi (PagoWompi + OrdenCompra)")
class WompiWebhookControllerTest {

    private static final String EVENTS_SECRET = "test_events_secret_webhook";
    private static final String REFERENCIA = "PS-TEST-REF-001";
    private static final String TRANSACCION_ID = "wompi-trx-001";
    private static final long MONTO_REGISTRADO = 5_000_000L;

    @Mock
    private PagoWompiRepository pagoWompiRepository;

    @Mock
    private AuditoriaPagoService auditoriaPagoService;

    @Mock
    private OrdenCompraService ordenCompraService;

    private MockMvc mockMvc;
    private PagoWompi pago;
    private String ultimoChecksumGenerado;

    @BeforeEach
    void setUp() {
        WompiProperties wompiProperties = new WompiProperties();
        wompiProperties.setEventsSecret(EVENTS_SECRET);
        WompiSignatureService signatureService = new WompiSignatureService(wompiProperties);

        PagoWompiWebhookService webhookService = new PagoWompiWebhookService(
                new ObjectMapper(), signatureService, pagoWompiRepository, event -> { }, auditoriaPagoService, ordenCompraService);

        mockMvc = MockMvcBuilders.standaloneSetup(new WompiWebhookController(webhookService)).build();

        Cliente cliente = Cliente.builder().idCliente(7).build();
        pago = PagoWompi.builder()
                .id(UUID.randomUUID())
                .referencia(REFERENCIA)
                .montoEnCentavos(MONTO_REGISTRADO)
                .moneda("COP")
                .estado(EstadoPago.PENDIENTE)
                .cliente(cliente)
                .build();
    }

    @Test
    @DisplayName("Checksum válido y monto correcto: aprueba el pago y responde 200")
    void checksum_valido_procesa_y_aprueba() throws Exception {
        when(pagoWompiRepository.findByTransaccionWompiId(TRANSACCION_ID)).thenReturn(Optional.empty());
        when(pagoWompiRepository.findByReferencia(REFERENCIA)).thenReturn(Optional.of(pago));

        String body = construirEventoJson("APPROVED", MONTO_REGISTRADO, TRANSACCION_ID, REFERENCIA);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        verify(pagoWompiRepository).save(pago);
        verifyNoInteractions(ordenCompraService);
    }

    @Test
    @DisplayName("Checksum inválido: responde 401 y no toca ningún repositorio (no procesa nada)")
    void checksum_invalido_responde_401_y_no_procesa() throws Exception {
        String body = construirEventoJson("APPROVED", MONTO_REGISTRADO, TRANSACCION_ID, REFERENCIA)
                .replace("\"checksum\": \"" + ultimoChecksumGenerado + "\"", "\"checksum\": \"" + "0".repeat(64) + "\"");

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(pagoWompiRepository);
        verifyNoInteractions(ordenCompraService);
    }

    @Test
    @DisplayName("Evento duplicado (mismo transaccionWompiId ya en estado final): responde 200 sin reprocesar")
    void evento_duplicado_no_reprocesa() throws Exception {
        pago.setTransaccionWompiId(TRANSACCION_ID);
        pago.setEstado(EstadoPago.APROBADO);
        when(pagoWompiRepository.findByTransaccionWompiId(TRANSACCION_ID)).thenReturn(Optional.of(pago));

        String body = construirEventoJson("APPROVED", MONTO_REGISTRADO, TRANSACCION_ID, REFERENCIA);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(pagoWompiRepository, never()).findByReferencia(any());
        verify(pagoWompiRepository, never()).save(any());
        verifyNoInteractions(ordenCompraService);
    }

    @Test
    @DisplayName("Monto del evento distinto al registrado: responde 200 pero marca el pago en ERROR, nunca lo aprueba")
    void monto_alterado_marca_error_y_no_aprueba() throws Exception {
        when(pagoWompiRepository.findByTransaccionWompiId(TRANSACCION_ID)).thenReturn(Optional.empty());
        when(pagoWompiRepository.findByReferencia(REFERENCIA)).thenReturn(Optional.of(pago));

        long montoAlterado = MONTO_REGISTRADO + 1; // no coincide con lo registrado en la BD
        String body = construirEventoJson("APPROVED", montoAlterado, TRANSACCION_ID, REFERENCIA);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.ERROR);
        verify(pagoWompiRepository).save(pago);
    }

    @Test
    @DisplayName("Referencia no es de PagoWompi (es una orden de compra del carrito prepago): cae al flujo de OrdenCompraService")
    void referencia_de_orden_compra_delega_en_ordenCompraService() throws Exception {
        String referenciaOrden = "ORD-000123";
        when(pagoWompiRepository.findByTransaccionWompiId(TRANSACCION_ID)).thenReturn(Optional.empty());
        when(pagoWompiRepository.findByReferencia(referenciaOrden)).thenReturn(Optional.empty());
        when(ordenCompraService.aplicarEventoTransaccion(any(JsonNode.class), eq(TRANSACCION_ID), eq(referenciaOrden), eq("APPROVED"), any()))
                .thenReturn(true);

        String body = construirEventoJson("APPROVED", MONTO_REGISTRADO, TRANSACCION_ID, referenciaOrden);

        mockMvc.perform(post("/api/v1/pagos/wompi/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(ordenCompraService).aplicarEventoTransaccion(any(JsonNode.class), eq(TRANSACCION_ID), eq(referenciaOrden), eq("APPROVED"), any());
        verify(pagoWompiRepository, never()).save(any());
    }

    // ── Helper de firma (cálculo independiente del código de producción) ──────────

    private String construirEventoJson(String estado, long montoEnCentavos, String transaccionId, String referencia) throws Exception {
        String timestamp = "1700000000";
        // Mismo orden que "properties" más abajo: id, status, amount_in_cents.
        String cadena = transaccionId + estado + montoEnCentavos + timestamp + EVENTS_SECRET;
        String checksum = sha256Hex(cadena);
        ultimoChecksumGenerado = checksum;

        return """
                {
                  "event": "transaction.updated",
                  "data": {
                    "transaction": {
                      "id": "%s",
                      "reference": "%s",
                      "status": "%s",
                      "amount_in_cents": %d,
                      "payment_method_type": "CARD"
                    }
                  },
                  "signature": {
                    "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"],
                    "checksum": "%s"
                  },
                  "timestamp": "%s"
                }
                """.formatted(transaccionId, referencia, estado, montoEnCentavos, checksum, timestamp);
    }

    private String sha256Hex(String valor) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
