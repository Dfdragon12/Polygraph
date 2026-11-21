package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un lote de la bolsa prepago (NS-02): las unidades de UNA compra aprobada (un ítem de la orden) con su valor unitario.
 * Las solicitudes descuentan del lote más antiguo con unidades (FIFO) y guardan el lote y el valor; el valor del lote no cambia nunca.
 */
@Entity
@Table(name = "lotes_prepago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"cliente", "proceso", "ordenCompra", "item"})
public class LotePrepago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Long idLote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    /** Null en el lote «Saldo migrado» (no viene de una compra concreta). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra")
    private OrdenCompra ordenCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item")
    private OrdenCompraItem item;

    /** Cuál de los paquetes del ítem es este lote (0, 1, 2…): cada paquete comprado es su propio lote. */
    @Builder.Default
    @Column(name = "indice_paquete", nullable = false)
    private Integer indicePaquete = 0;

    /** TP-07: LOCAL o NACIONAL (TP-08). */
    @Builder.Default
    @Column(name = "cobertura", nullable = false, length = 20)
    private String cobertura = "LOCAL";

    /** TP-07: fin del último día de vigencia (23:59:59, hora de Bogotá). Null = nunca vence. */
    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;

    /** Unidades que vencieron sin usarse: ya no se pueden usar y se reportan aparte. */
    @Builder.Default
    @Column(name = "cantidad_vencida", nullable = false)
    private Integer cantidadVencida = 0;

    @Column(name = "fecha_vencida")
    private LocalDateTime fechaVencida;

    /** Transacción de pago que creó el lote (con el ítem es único: un aviso repetido no crea otro). */
    @Column(name = "id_transaccion_pago", length = 100)
    private String idTransaccionPago;

    /** COMPRA o SALDO_MIGRADO. */
    @Builder.Default
    @Column(name = "origen", nullable = false, length = 20)
    private String origen = "COMPRA";

    @Column(name = "aviso_vencimiento_en")
    private LocalDateTime avisoVencimientoEn;

    @Column(name = "cantidad_comprada", nullable = false)
    private Integer cantidadComprada;

    @Column(name = "cantidad_consumida", nullable = false)
    private Integer cantidadConsumida;

    @Column(name = "valor_unitario", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_original", precision = 15, scale = 2)
    private BigDecimal valorOriginal;

    @Column(name = "nombre_descuento_aplicado", length = 150)
    private String nombreDescuentoAplicado;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDateTime fechaCompra;

    /** Unidades que todavía se pueden usar (no consumidas ni vencidas). */
    public int restante() {
        return cantidadComprada - cantidadConsumida - (cantidadVencida == null ? 0 : cantidadVencida);
    }
}
