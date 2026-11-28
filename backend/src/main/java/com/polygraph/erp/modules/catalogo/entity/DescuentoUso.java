package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Un cliente usó un cupón — solo aplica a descuentos con código. La UNIQUE(id_descuento,
 * id_cliente) en BD es lo que realmente impide reusarlo, esto es solo la vista del dato. */
@Entity
@Table(name = "descuentos_usos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DescuentoUso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_uso")
    private Integer idUso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_descuento", nullable = false)
    private Descuento descuento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra")
    private OrdenCompra ordenCompra;

    @Column(name = "fecha_uso", nullable = false)
    private LocalDateTime fechaUso;
}
