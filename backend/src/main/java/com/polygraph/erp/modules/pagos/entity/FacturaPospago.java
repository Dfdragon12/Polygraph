package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Corte/extracto de crédito pospago — junta los {@link MovimientoCreditoPospago} sin facturar de
 * un cliente al momento de generarla (siempre manual, la genera un admin — igual que el
 * comprobante de una orden de compra prepago). Se paga vía el mismo pipeline genérico de Wompi
 * que PagoWompi (ver {@code TipoConceptoPago.FACTURA_POSPAGO}).
 */
@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "facturas_pospago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaPospago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Long idFactura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "referencia", nullable = false, unique = true, length = 100)
    private String referencia;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoFacturaPospago estado;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_generada_por", nullable = false)
    private Usuario generadaPor;
}
