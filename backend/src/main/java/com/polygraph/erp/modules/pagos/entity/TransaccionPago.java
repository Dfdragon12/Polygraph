package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.shared.enums.EstadoTransaccionPago;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Bitácora de eventos de pago (webhook Wompi o simulador) — usada también para deduplicar por id_transaccion_wompi. */
@Entity
@Table(name = "transacciones_pago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transaccion")
    private Long idTransaccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTransaccionPago estado;

    @Column(name = "id_transaccion_wompi", length = 100)
    private String idTransaccionWompi;

    @Column(name = "origen", nullable = false, length = 20)
    private String origen;

    @Column(name = "payload_raw", columnDefinition = "TEXT")
    private String payloadRaw;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
