package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

/** Plan global de la tienda prepago (TP-01): 1, 5, 20, 100 o 200 unidades. vigenciaMeses null = sin vencimiento. */
@Entity
@Table(name = "planes_tienda")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanTienda {

    @Id
    @Column(name = "unidades")
    private Integer unidades;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "visible", nullable = false)
    private Boolean visible;

    /** Solo uno puede estar destacado ("Más solicitado"). */
    @Column(name = "destacado", nullable = false)
    private Boolean destacado;

    @Column(name = "vigencia_meses")
    private Integer vigenciaMeses;
}
