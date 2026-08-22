package com.polygraph.erp.modules.pagos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.pagos.dto.WompiEventDTO;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.event.PagoAprobadoEvent;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.shared.enums.EstadoPago;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Procesa eventos de webhook de Wompi para el módulo de pagos (PagoWompi). El checksum es
 * la ÚNICA fuente de verdad de autenticidad — nunca se confía en el redirect del navegador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoWompiWebhookService {

    private static final Set<EstadoPago> ESTADOS_FINALES =
            EnumSet.of(EstadoPago.APROBADO, EstadoPago.RECHAZADO, EstadoPago.ANULADO, EstadoPago.ERROR);

    private final ObjectMapper objectMapper;
    private final WompiSignatureService wompiSignatureService;
    private final PagoWompiRepository pagoWompiRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditoriaPagoService auditoriaPagoService;

    /** @return false únicamente cuando la firma es inválida (o el body no se puede parsear) — el controller responde 401 en ese caso. */
    @Transactional
    public boolean procesarEvento(String rawBody, String ipOrigen) {
        WompiEventDTO evento;
        try {
            evento = objectMapper.readValue(rawBody, WompiEventDTO.class);
        } catch (Exception e) {
            log.warn("Webhook de Wompi con cuerpo no parseable — ip={}", ipOrigen);
            auditoriaPagoService.registrarChecksumInvalido(null, ipOrigen, "Cuerpo de webhook no parseable");
            return false;
        }

        // Se extrae la referencia ANTES de validar el checksum solo para poder auditar el intento
        // (no se actúa sobre ningún dato del evento hasta que el checksum sea válido).
        JsonNode transaccionSinValidar = evento.data() != null ? evento.data().path("transaction") : null;
        String referenciaSinValidar = textoOrNull(transaccionSinValidar, "reference");

        if (!wompiSignatureService.validarChecksumEvento(evento)) {
            log.warn("Webhook de Wompi con checksum inválido — ip={}", ipOrigen);
            auditoriaPagoService.registrarChecksumInvalido(referenciaSinValidar, ipOrigen, "Checksum de webhook inválido");
            return false;
        }

        JsonNode transaccion = transaccionSinValidar;
        String idTransaccionWompi = textoOrNull(transaccion, "id");
        String referencia = referenciaSinValidar;
        String estadoWompi = textoOrNull(transaccion, "status");
        String metodoPago = textoOrNull(transaccion, "payment_method_type");
        Long montoEvento = (transaccion != null && transaccion.hasNonNull("amount_in_cents"))
                ? transaccion.path("amount_in_cents").asLong()
                : null;

        if (idTransaccionWompi != null && esDuplicadoFinal(idTransaccionWompi)) {
            log.info("Evento de Wompi duplicado ignorado: transaccionWompiId={}", idTransaccionWompi);
            return true;
        }

        if (referencia == null) {
            log.warn("Webhook de Wompi sin referencia de transacción — se ignora. ip={}", ipOrigen);
            return true;
        }

        PagoWompi pago = pagoWompiRepository.findByReferencia(referencia).orElse(null);
        if (pago == null) {
            log.error("Webhook de Wompi con referencia desconocida: {}", referencia);
            return true;
        }

        aplicarEstadoTransaccion(pago, idTransaccionWompi, estadoWompi, montoEvento, metodoPago, rawBody, ipOrigen, true);
        return true;
    }

    /**
     * Único lugar donde se aplica el estado remoto de una transacción Wompi a nuestro registro:
     * valida el monto, mapea el estado, persiste, audita el payload y el cambio de estado, y
     * dispara el post-procesamiento de aprobación (evento -> PagoAplicacionService). La usan
     * tanto el webhook como {@code ConciliacionPagosJob}, para no duplicar la regla de negocio.
     * ipOrigen/checksumValido van no-nulos solo cuando el origen es un webhook; en la conciliación
     * automática (consulta directa a Wompi) van null.
     */
    @Transactional
    public void aplicarEstadoTransaccion(PagoWompi pago, String idTransaccionWompi, String estadoWompi,
                                          Long montoRemoto, String metodoPago, String payloadCrudo,
                                          String ipOrigen, Boolean checksumValido) {
        EstadoPago estadoAnterior = pago.getEstado();

        pago.setPayloadUltimoEvento(payloadCrudo);
        if (idTransaccionWompi != null) {
            pago.setTransaccionWompiId(idTransaccionWompi);
        }
        if (metodoPago != null) {
            pago.setMetodoPago(metodoPago);
        }

        if (montoRemoto == null || !montoRemoto.equals(pago.getMontoEnCentavos())) {
            pago.setEstado(EstadoPago.ERROR);
            pagoWompiRepository.save(pago);
            log.error("ALERTA: monto reportado por Wompi ({}) no coincide con el registrado ({}) para referencia={} "
                            + "— pago marcado en ERROR para revisión manual, NO se aprueba nada",
                    montoRemoto, pago.getMontoEnCentavos(), pago.getReferencia());
            auditoriaPagoService.registrarCambioEstado(pago, estadoAnterior, EstadoPago.ERROR, ipOrigen, checksumValido,
                    "Monto reportado por Wompi (" + montoRemoto + ") no coincide con el registrado (" + pago.getMontoEnCentavos() + ")");
            return;
        }

        EstadoPago estadoNuevo = mapearEstado(estadoWompi);
        pago.setEstado(estadoNuevo);
        pagoWompiRepository.save(pago);
        log.info("Pago Wompi actualizado: referencia={}, estado={}", pago.getReferencia(), estadoNuevo);
        auditoriaPagoService.registrarCambioEstado(pago, estadoAnterior, estadoNuevo, ipOrigen, checksumValido,
                "Estado remoto en Wompi: " + estadoWompi);

        if (estadoNuevo == EstadoPago.APROBADO) {
            eventPublisher.publishEvent(new PagoAprobadoEvent(pago.getId(), pago.getCliente().getIdCliente()));
        }
    }

    private boolean esDuplicadoFinal(String idTransaccionWompi) {
        Optional<PagoWompi> existente = pagoWompiRepository.findByTransaccionWompiId(idTransaccionWompi);
        return existente.isPresent() && ESTADOS_FINALES.contains(existente.get().getEstado());
    }

    private EstadoPago mapearEstado(String estadoWompi) {
        if (estadoWompi == null) {
            return EstadoPago.PENDIENTE;
        }
        return switch (estadoWompi) {
            case "APPROVED" -> EstadoPago.APROBADO;
            case "DECLINED" -> EstadoPago.RECHAZADO;
            case "VOIDED" -> EstadoPago.ANULADO;
            case "ERROR" -> EstadoPago.ERROR;
            default -> EstadoPago.PENDIENTE; // p.ej. PENDING — todavía no es un estado final
        };
    }

    private String textoOrNull(JsonNode nodo, String campo) {
        if (nodo == null) {
            return null;
        }
        return nodo.hasNonNull(campo) ? nodo.path(campo).asText() : null;
    }
}
