package com.polygraph.erp.modules.riesgo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Central de riesgo consultada para el indicador de pago (score) de Análisis financiero: su nombre es SOLO interno
 *  (nunca se imprime) y sus bandas dicen en qué rango cae el score ("150 a 400 Muy bajo"). */
@Entity
@Table(name = "centrales_riesgo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CentralRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_central")
    private Integer idCentral;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @OneToMany(mappedBy = "central", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<CentralRiesgoBanda> bandas = new ArrayList<>();
}
