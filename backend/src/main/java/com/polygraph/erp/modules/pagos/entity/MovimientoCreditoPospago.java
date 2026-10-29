package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cargo a la tarjeta de crédito pospago generado por una solicitud — snapshot del costo del
 * servicio en el momento de solicitarlo (proceso + recargo por ciudad si aplica). Queda sin
 * facturar (idFactura null) hasta que el admin genera el corte que lo incluye.
 */
@Entity
@Table(name = "movimientos_credito_pospago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoCreditoPospago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio", nullable = false)
    private Servicio servicio;

    @Column(name = "monto_base", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoBase;

    @Column(name = "monto_recargo_ciudad", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoRecargoCiudad;

    @Column(name = "monto_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @Column(name = "fecha_generado", nullable = false)
    private LocalDateTime fechaGenerado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_factura")
    private FacturaPospago factura;

    /** NS-04: cargo de una solicitud cancelada — no entra en la factura (no se borra). */
    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;
}
