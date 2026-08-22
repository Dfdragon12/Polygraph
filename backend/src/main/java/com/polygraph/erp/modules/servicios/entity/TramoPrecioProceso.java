package com.polygraph.erp.modules.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Tramo de descuento por volumen: si la cantidad comprada de un proceso alcanza cantidadMinima, se cobra valorUnitario en vez del precio base. */
@Entity
@Table(name = "tramos_precio_proceso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TramoPrecioProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tramo")
    private Integer idTramo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @Column(name = "cantidad_minima", nullable = false)
    private Integer cantidadMinima;

    @Column(name = "valor_unitario", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorUnitario;
}
