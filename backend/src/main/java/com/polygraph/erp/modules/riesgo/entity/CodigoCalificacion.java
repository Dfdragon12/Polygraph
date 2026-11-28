package com.polygraph.erp.modules.riesgo.entity;

import jakarta.persistence.*;
import lombok.*;

/** Un código del catálogo de calificación de un subservicio (ej. LEG-09 de Antecedentes legales,
 *  ver V202 y docs/antecedentes/motor_ant.py). Es configuración: el cálculo lee los puntos de aquí,
 *  nunca los trae escritos en el código. grupo = el de tipos_progreso.grupo_calificacion.
 *  Crítico = puntos null; lleva el servicio a 100 y a ALTO. */
@Entity
@Table(name = "codigos_calificacion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodigoCalificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "grupo", nullable = false, length = 30)
    private String grupo;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    /** 0–100; null solo cuando es crítico. */
    @Column(name = "puntos")
    private Integer puntos;

    @Builder.Default
    @Column(name = "critico", nullable = false)
    private Boolean critico = false;

    /** Suma una vez por cada fila (proceso) en que aparece; si no, cuenta una sola vez. */
    @Builder.Default
    @Column(name = "por_proceso", nullable = false)
    private Boolean porProceso = false;

    /** Solo suma si el cargo implica conducción. */
    @Builder.Default
    @Column(name = "solo_conduccion", nullable = false)
    private Boolean soloConduccion = false;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    /** Alerta (ALR-…): informativa, 0 puntos — sale en la tabla de hallazgos pero no suma (V205). */
    @Builder.Default
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(name = "es_alerta", nullable = false)
    private Boolean esAlerta = false;
}
