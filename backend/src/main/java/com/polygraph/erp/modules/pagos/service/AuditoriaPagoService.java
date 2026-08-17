package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.pagos.dto.AuditoriaPagoResponse;
import com.polygraph.erp.modules.pagos.entity.AuditoriaPago;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.AuditoriaPagoRepository;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.EventoAuditoriaPago;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Único punto de escritura de la bitácora de auditoría de pagos (creación, webhook,
 * conciliación) — así la forma de auditar no diverge entre los distintos orígenes del evento.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaPagoService {

    private final AuditoriaPagoRepository auditoriaPagoRepository;

    @Transactional
    public void registrarCreacion(PagoWompi pago, Usuario generadoPor) {
        auditoriaPagoRepository.save(AuditoriaPago.builder()
                .referencia(pago.getReferencia())
                .pago(pago)
                .tipoEvento(EventoAuditoriaPago.CREACION)
                .generadoPor(generadoPor)
                .montoEnCentavos(pago.getMontoEnCentavos())
                .estadoNuevo(pago.getEstado())
                .fechaEvento(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void registrarChecksumInvalido(String referencia, String ipOrigen, String motivo) {
        auditoriaPagoRepository.save(AuditoriaPago.builder()
                .referencia(referencia != null ? referencia : "DESCONOCIDA")
                .tipoEvento(EventoAuditoriaPago.WEBHOOK_CHECKSUM_INVALIDO)
                .ipOrigen(ipOrigen)
                .checksumValido(false)
                .detalle(motivo)
                .fechaEvento(LocalDateTime.now())
                .build());
    }

    /** ipOrigen/checksumValido no nulos => evento de webhook; nulos => conciliación automática. */
    @Transactional
    public void registrarCambioEstado(PagoWompi pago, EstadoPago estadoAnterior, EstadoPago estadoNuevo,
                                       String ipOrigen, Boolean checksumValido, String detalle) {
        auditoriaPagoRepository.save(AuditoriaPago.builder()
                .referencia(pago.getReferencia())
                .pago(pago)
                .tipoEvento(ipOrigen != null
                        ? EventoAuditoriaPago.WEBHOOK_ESTADO_ACTUALIZADO
                        : EventoAuditoriaPago.CONCILIACION_ESTADO_ACTUALIZADO)
                .montoEnCentavos(pago.getMontoEnCentavos())
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(estadoNuevo)
                .ipOrigen(ipOrigen)
                .checksumValido(checksumValido)
                .detalle(detalle)
                .fechaEvento(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaPagoResponse> listar(String referencia, Pageable pageable) {
        Page<AuditoriaPago> resultado = (referencia != null && !referencia.isBlank())
                ? auditoriaPagoRepository.findByReferenciaOrderByFechaEventoDesc(referencia, pageable)
                : auditoriaPagoRepository.findAllByOrderByFechaEventoDesc(pageable);
        return resultado.map(this::mapear);
    }

    private AuditoriaPagoResponse mapear(AuditoriaPago a) {
        return new AuditoriaPagoResponse(
                a.getReferencia(),
                a.getTipoEvento().name(),
                a.getGeneradoPor() != null ? a.getGeneradoPor().getEmail() : null,
                a.getMontoEnCentavos(),
                a.getEstadoAnterior() != null ? a.getEstadoAnterior().name() : null,
                a.getEstadoNuevo() != null ? a.getEstadoNuevo().name() : null,
                a.getIpOrigen(),
                a.getChecksumValido(),
                a.getDetalle(),
                a.getFechaEvento());
    }
}
