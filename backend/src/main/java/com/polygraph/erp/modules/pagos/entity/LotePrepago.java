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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item", nullable = false, unique = true)
    private OrdenCompraItem item;

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

    public int restante() {
        return cantidadComprada - cantidadConsumida;
    }
}
