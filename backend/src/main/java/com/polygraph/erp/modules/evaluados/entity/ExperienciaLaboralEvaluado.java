package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "experiencia_laboral_evaluado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "hojaVidaEvaluado")
public class ExperienciaLaboralEvaluado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_hoja_vida", nullable = false)
    private HojaVidaEvaluado hojaVidaEvaluado;

    @Column(name = "empresa", nullable = false, length = 200)
    private String empresa;

    @Column(name = "cargo", nullable = false, length = 150)
    private String cargo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "labora_actualmente")
    private Boolean laboraActualmente;

    @Column(name = "ciudad", length = 150)
    private String ciudad;

    @Column(name = "telefono_empresa", length = 20)
    private String telefonoEmpresa;

    @Column(name = "motivo_retiro", columnDefinition = "TEXT")
    private String motivoRetiro;

    @Column(name = "nombre_jefe", length = 150)
    private String nombreJefe;

    @Column(name = "cargo_jefe", length = 100)
    private String cargoJefe;
}
