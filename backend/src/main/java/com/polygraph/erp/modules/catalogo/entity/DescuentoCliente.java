package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import jakarta.persistence.*;
import lombok.*;

/** Cliente elegible para un descuento cuyo publicoObjetivo es CLIENTES_ESPECIFICOS. */
@Entity
@Table(name = "descuentos_clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DescuentoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_descuento_cliente")
    private Integer idDescuentoCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_descuento", nullable = false)
    private Descuento descuento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;
}
