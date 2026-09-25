package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "procesos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Proceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proceso")
    private Integer idProceso;

    @Column(name = "nombre_proceso", unique = true, nullable = false, length = 100)
    private String nombreProceso;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "activo")
    private Boolean activo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_clasificacion", nullable = false)
    private ClasificacionProceso clasificacion;

    @Column(name = "valor", precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "dias_habiles_entrega")
    private Integer diasHabilesEntrega;

    @Column(name = "aplica_precio_ciudad", nullable = false)
    private Boolean aplicaPrecioCiudad;

    /** Puntos clave cortos para la tarjeta de la tienda — un punto por línea. */
    @Column(name = "puntos_clave", columnDefinition = "TEXT")
    private String puntosClave;

    // ── Calificación de riesgo (ver docs/modelo-calificacion-riesgo.md §2) ──

    /** % mínimo de peso que debe quedar realizado para no mandar el caso a revisión manual. */
    @Builder.Default
    @Column(name = "cobertura_minima", nullable = false, precision = 5, scale = 2)
    private BigDecimal coberturaMinima = new BigDecimal("100");

    /** Criticidad del cargo con la que se califica por defecto; el gestor la ajusta por caso. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "criticidad_defecto", nullable = false, length = 10)
    private CriticidadCargo criticidadDefecto = CriticidadCargo.ESTANDAR;

    /** Sube con cada cambio de pesos/obligatorios/cobertura/criticidad — cada cálculo guarda con
     *  qué versión se hizo. */
    @Builder.Default
    @Column(name = "version_calificacion", nullable = false)
    private Integer versionCalificacion = 1;
}
