package com.polygraph.erp.modules.tienda.entity;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import jakarta.persistence.*;
import lombok.*;

/** Beneficio de un servicio: aparece en todos los planes con unidades >= desdeUnidades (TP-01). */
@Entity
@Table(name = "beneficios_plan")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "proceso")
public class BeneficioPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_beneficio")
    private Integer idBeneficio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @Column(name = "texto", nullable = false, length = 120)
    private String texto;

    /** 1, 5, 20, 100 o 200. */
    @Column(name = "desde_unidades", nullable = false)
    private Integer desdeUnidades;

    @Column(name = "orden", nullable = false)
    private Integer orden;
}
