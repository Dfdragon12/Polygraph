package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hoja_vida_evaluado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"candidato", "educacion", "experienciaLaboral", "inactividades", "referencias", "documentos"})
public class HojaVidaEvaluado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_candidato", nullable = false, unique = true)
    private Candidato candidato;

    @Column(name = "autorizacion_datos", nullable = false)
    private Boolean autorizacionDatos;

    @Column(name = "fecha_autorizacion")
    private LocalDateTime fechaAutorizacion;

    @Column(name = "completado", nullable = false)
    private Boolean completado;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    @Column(name = "progreso_porcentaje")
    private Integer progresoPorcentaje;

    @Column(name = "paso_actual")
    private Integer pasoActual;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "lugar_nacimiento", length = 200)
    private String lugarNacimiento;

    @Column(name = "estado_civil", length = 50)
    private String estadoCivil;

    @Column(name = "nivel_educativo", length = 50)
    private String nivelEducativo;

    @Column(name = "direccion", columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "barrio", length = 100)
    private String barrio;

    @Column(name = "estrato", length = 1)
    private String estrato;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "celular", length = 20)
    private String celular;

    @Column(name = "rh", length = 5)
    private String rh;

    @Column(name = "libreta_militar", length = 50)
    private String libretaMilitar;

    @Column(name = "visa", length = 50)
    private String visa;

    @Column(name = "pasaporte", length = 50)
    private String pasaporte;

    @Column(name = "fondo_pensiones", length = 100)
    private String fondoPensiones;

    @Column(name = "eps", length = 100)
    private String eps;

    @Column(name = "telefono_fijo", length = 20)
    private String telefonoFijo;

    @OneToMany(mappedBy = "hojaVidaEvaluado", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EducacionEvaluado> educacion = new ArrayList<>();

    @OneToMany(mappedBy = "hojaVidaEvaluado", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExperienciaLaboralEvaluado> experienciaLaboral = new ArrayList<>();

    @OneToMany(mappedBy = "hojaVidaEvaluado", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InactividadLaboralEvaluado> inactividades = new ArrayList<>();

    @OneToMany(mappedBy = "hojaVidaEvaluado", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReferenciaPersonalEvaluado> referencias = new ArrayList<>();

    @OneToMany(mappedBy = "hojaVidaEvaluado", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentoEvaluado> documentos = new ArrayList<>();
}
