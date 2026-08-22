package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.EventoAuditoriaPago;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bitácora de auditoría del módulo de pagos: quién generó cada cobro, cada cambio de estado
 * (creación, webhook, conciliación), la IP de origen de cada webhook y si su checksum fue
 * válido. Es un log de solo-inserción — nunca se actualiza ni se borra una fila existente.
 */
@Entity
@Table(name = "auditoria_pagos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "referencia", nullable = false, length = 100)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pago")
    private PagoWompi pago;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private EventoAuditoriaPago tipoEvento;

    /** Quién generó el cobro — solo se llena en el evento CREACION; los eventos de webhook/conciliación son del sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_generado_por")
    private Usuario generadoPor;

    @Column(name = "monto_en_centavos")
    private Long montoEnCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoPago estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", length = 20)
    private EstadoPago estadoNuevo;

    /** Solo aplica a eventos originados por webhook. */
    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    /** Solo aplica a eventos originados por webhook (null en conciliación/creación). */
    @Column(name = "checksum_valido")
    private Boolean checksumValido;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;

    @PrePersist
    protected void alCrear() {
        if (fechaEvento == null) {
            fechaEvento = LocalDateTime.now();
        }
    }
}
