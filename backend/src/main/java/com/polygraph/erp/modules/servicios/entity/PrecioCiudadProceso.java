package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.NivelCiudad;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Monto adicional que se suma al precio de un proceso según el nivel logístico de ciudad del
 * evaluado — independiente del valor base o del tramo de volumen aplicado. Solo aplica si
 * proceso.aplicaPrecioCiudad. */
@Entity
@Table(name = "precios_ciudad_proceso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrecioCiudadProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_precio_ciudad")
    private Integer idPrecioCiudad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_ciudad", nullable = false, length = 30)
    private NivelCiudad nivelCiudad;

    @Column(name = "valor", precision = 15, scale = 2, nullable = false)
    private BigDecimal valor;
}
