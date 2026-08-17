package com.polygraph.erp.modules.pagos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.dto.WompiEventDTO;
import com.polygraph.erp.modules.pagos.dto.WompiEventSignatureDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WompiSignatureService — Firma de integridad y validación de checksum de eventos")
class WompiSignatureServiceTest {

    // Valores ficticios de configuración (no son credenciales reales de Wompi).
    private static final String INTEGRITY_SECRET = "test_integrity_secret";
    private static final String EVENTS_SECRET = "test_events_secret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WompiSignatureService signatureService;

    @BeforeEach
    void setUp() {
        WompiProperties properties = new WompiProperties();
        properties.setIntegritySecret(INTEGRITY_SECRET);
        properties.setEventsSecret(EVENTS_SECRET);
        signatureService = new WompiSignatureService(properties);
    }

    // ── generarFirmaIntegridad ───────────────────────────────────

    @Test
    @DisplayName("Genera el hash SHA-256 esperado concatenando referencia + monto + moneda + secreto")
    void genera_firma_integridad_esperada() {
        // sha256("PYG-TEST1235000000COPtest_integrity_secret") calculado independientemente
        String esperado = "4f7e9bac81d49c392cfb9d4d5d620473f5f889a9843b4a364d7ad8e5482834eb";

        String firma = signatureService.generarFirmaIntegridad("PYG-TEST123", 5_000_000L, "COP");

        assertThat(firma).isEqualTo(esperado);
    }

    @Test
    @DisplayName("La firma de integridad se devuelve en hexadecimal minúsculas de 64 caracteres")
    void firma_integridad_formato_hex() {
        String firma = signatureService.generarFirmaIntegridad("PYG-ABC", 100L, "COP");

        assertThat(firma).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Cambiar cualquier componente de entrada cambia la firma resultante")
    void firma_integridad_cambia_con_la_entrada() {
        String firmaOriginal = signatureService.generarFirmaIntegridad("PYG-TEST123", 5_000_000L, "COP");
        String firmaMontoDistinto = signatureService.generarFirmaIntegridad("PYG-TEST123", 5_000_001L, "COP");

        assertThat(firmaMontoDistinto).isNotEqualTo(firmaOriginal);
    }

    // ── validarChecksumEvento ─────────────────────────────────────

    @Test
    @DisplayName("Valida como correcto un checksum calculado con el mismo algoritmo de Wompi")
    void valida_checksum_correcto() throws Exception {
        // sha256("trx-123APPROVED50000001700000000test_events_secret") calculado independientemente
        String checksumValido = "eac967c1aed4960cd9f6a66ab5ff9dbdd8dbde5dd8cf5ff54702480329aee6cf";
        WompiEventDTO evento = construirEvento(checksumValido);

        boolean valido = signatureService.validarChecksumEvento(evento);

        assertThat(valido).isTrue();
    }

    @Test
    @DisplayName("Rechaza un checksum que no coincide con el calculado")
    void rechaza_checksum_incorrecto() throws Exception {
        WompiEventDTO evento = construirEvento("0000000000000000000000000000000000000000000000000000000000000");

        boolean valido = signatureService.validarChecksumEvento(evento);

        assertThat(valido).isFalse();
    }

    @Test
    @DisplayName("Resuelve las propiedades en el orden en que vienen listadas, no en un orden fijo")
    void resuelve_propiedades_en_el_orden_declarado() throws Exception {
        // Mismos valores que construirEvento(), pero con las propiedades en otro orden:
        // el checksum válido para ESE orden es distinto al de "id, status, amount_in_cents".
        JsonNode data = objectMapper.readTree("""
                {"transaction": {"id": "trx-123", "status": "APPROVED", "amount_in_cents": 5000000}}
                """);
        WompiEventSignatureDTO firmaOrdenOriginal = new WompiEventSignatureDTO(
                List.of("transaction.status", "transaction.id", "transaction.amount_in_cents"),
                "eac967c1aed4960cd9f6a66ab5ff9dbdd8dbde5dd8cf5ff54702480329aee6cf"); // checksum del otro orden
        WompiEventDTO evento = new WompiEventDTO("transaction.updated", data, firmaOrdenOriginal, "1700000000");

        boolean valido = signatureService.validarChecksumEvento(evento);

        assertThat(valido).isFalse();
    }

    @Test
    @DisplayName("Una ruta de propiedad inexistente en el JSON se resuelve como cadena vacía, sin lanzar excepción")
    void ruta_inexistente_no_lanza_excepcion() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"transaction": {"id": "trx-123"}}
                """);
        WompiEventSignatureDTO firma = new WompiEventSignatureDTO(
                List.of("transaction.id", "transaction.campo_que_no_existe"), "cualquier-checksum");
        WompiEventDTO evento = new WompiEventDTO("transaction.updated", data, firma, "1700000000");

        boolean valido = signatureService.validarChecksumEvento(evento);

        assertThat(valido).isFalse();
    }

    @Test
    @DisplayName("Un evento sin signature se rechaza sin lanzar excepción")
    void evento_sin_signature_se_rechaza() {
        WompiEventDTO evento = new WompiEventDTO("transaction.updated", objectMapper.createObjectNode(), null, "1700000000");

        boolean valido = signatureService.validarChecksumEvento(evento);

        assertThat(valido).isFalse();
    }

    private WompiEventDTO construirEvento(String checksum) throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"transaction": {"id": "trx-123", "status": "APPROVED", "amount_in_cents": 5000000}}
                """);
        WompiEventSignatureDTO firma = new WompiEventSignatureDTO(
                List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"), checksum);
        return new WompiEventDTO("transaction.updated", data, firma, "1700000000");
    }
}
