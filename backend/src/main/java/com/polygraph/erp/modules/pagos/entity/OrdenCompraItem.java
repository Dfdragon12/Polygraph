package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Ítem de una orden de compra. valorUnitario/subtotal son un snapshot del precio al momento de comprar. */
@Entity
@Table(name = "orden_compra_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenCompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item")
    private Long idItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    /** Precio unitario antes de aplicar descuentos (base o por tramo de volumen) — para mostrar el desglose. */
    @Column(name = "valor_original", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorOriginal;

    @Column(name = "valor_unitario", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "subtotal", precision = 15, scale = 2, nullable = false)
    private BigDecimal subtotal;

    /** TP-02: plan comprado (unidades del paquete), cuántos paquetes y cobertura. NULL en compras por cantidad libre. */
    @Column(name = "unidades_plan")
    private Integer unidadesPlan;

    @Column(name = "cantidad_paquetes")
    private Integer cantidadPaquetes;

    @Column(name = "cobertura", length = 20)
    private String cobertura;

    /** TP-07: vigencia en meses que se le mostró al cliente (null = sin vencimiento o interruptor apagado). Rige aunque el plan cambie después. */
    @Column(name = "vigencia_meses")
    private Integer vigenciaMeses;

    /** TP-03: IVA de esta línea (sobre el subtotal), redondeado a pesos. 0 si el cobro con IVA estaba apagado o el servicio no aplica IVA. */
    @Builder.Default
    @Column(name = "monto_iva", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoIva = BigDecimal.ZERO;

    /** Nombre del descuento (automático o cupón) que ganó el mejor precio para este ítem — snapshot, null si no aplicó ninguno. */
    @Column(name = "nombre_descuento_aplicado", length = 150)
    private String nombreDescuentoAplicado;
}
