package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tipos_progreso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoProgreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_progreso")
    private Integer idTipoProgreso;

    @Column(name = "nombre_progreso", nullable = false, length = 100)
    private String nombreProgreso;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "orden")
    private Integer orden;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "valor", precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "minutos_estimados")
    private Integer minutosEstimados;

    /** Qué rol interno ejecuta este subproceso — ANALISTA_INTERNO, POLIGRAFISTA o VISITADOR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol_responsable", nullable = false, length = 30)
    private Rol rolResponsable;
}
