package com.polygraph.erp.modules.riesgo.entity;

import com.polygraph.erp.shared.enums.CriticidadCargo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Umbrales de nivel para una criticidad: BAJO si puntaje <= limiteBajo; MEDIO si <= limiteMedio;
 *  ALTO si es mayor. minimoMedio: puntaje al que sube la regla de piso. */
@Entity
@Table(name = "umbrales_riesgo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmbralRiesgo {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "criticidad", length = 10)
    private CriticidadCargo criticidad;

    @Column(name = "limite_bajo", nullable = false, precision = 5, scale = 2)
    private BigDecimal limiteBajo;

    @Column(name = "limite_medio", nullable = false, precision = 5, scale = 2)
    private BigDecimal limiteMedio;

    @Column(name = "minimo_medio", nullable = false, precision = 5, scale = 2)
    private BigDecimal minimoMedio;
}
