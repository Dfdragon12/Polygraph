package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "inactividad_laboral_evaluado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "hojaVidaEvaluado")
public class InactividadLaboralEvaluado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_hoja_vida", nullable = false)
    private HojaVidaEvaluado hojaVidaEvaluado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "dias_inactivo", nullable = false)
    private Integer diasInactivo;

    @Column(name = "justificacion", columnDefinition = "TEXT")
    private String justificacion;

    /** Qué hizo para cubrir sus gastos (estudios, trabajo independiente, emprendimiento…). */
    @Column(name = "actividad", length = 60)
    private String actividad;

    /** Quién o qué documento puede confirmarlo. */
    @Column(name = "quien_confirma", columnDefinition = "TEXT")
    private String quienConfirma;

    @Column(name = "requiere_cuestionario", nullable = false)
    private Boolean requiereCuestionario;
}
