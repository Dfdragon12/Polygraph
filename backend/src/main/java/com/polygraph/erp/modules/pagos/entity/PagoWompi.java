package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.shared.enums.EstadoPago;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de un cobro vía Wompi. Solo guarda el nombre del método de pago
 * (CARD, NEQUI, PSE, BANCOLOMBIA_TRANSFER) — nunca número de tarjeta, CVV
 * ni ningún otro dato sensible del medio de pago.
 */
@Entity
@Table(name = "pagos_wompi")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoWompi {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "referencia", nullable = false, unique = true, length = 100)
    private String referencia;

    @Column(name = "transaccion_wompi_id", unique = true, length = 100)
    private String transaccionWompiId;

    @Column(name = "monto_en_centavos", nullable = false)
    private Long montoEnCentavos;

    @Builder.Default
    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda = "COP";

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "factura_o_concepto_id")
    private Long facturaOConceptoId;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "payload_ultimo_evento", columnDefinition = "TEXT")
    private String payloadUltimoEvento;

    @PrePersist
    protected void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        fechaCreacion = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        fechaActualizacion = LocalDateTime.now();
    }
}
