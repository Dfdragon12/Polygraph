package com.polygraph.erp.modules.riesgo.entity;

import jakarta.persistence.*;
import lombok.*;

/** Una banda del indicador de pago de una central: de desde a hasta (ambos incluidos) se llama nombre. */
@Entity
@Table(name = "centrales_riesgo_bandas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "central")
public class CentralRiesgoBanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_banda")
    private Integer idBanda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_central", nullable = false)
    private CentralRiesgo central;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "desde", nullable = false)
    private Integer desde;

    @Column(name = "hasta", nullable = false)
    private Integer hasta;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;
}
