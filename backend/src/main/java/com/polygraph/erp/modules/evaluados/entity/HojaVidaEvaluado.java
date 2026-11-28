package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "hoja_vida_evaluado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"candidato", "educacion", "experienciaLaboral", "inactividades", "referencias", "documentos", "respuestas"})
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

    // Opcional — referencia al catálogo de barrios (ver BarrioService); reemplaza el texto libre
    // de `barrio` como dato "vivo" hacia adelante, sin dejar de guardarlo.
    @Column(name = "id_barrio")
    private Integer idBarrio;

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

    /** Teléfono fijo y/o número de WhatsApp (con código de país: +57 300 123 4567). */
    @Column(name = "telefono_fijo", length = 20)
    private String telefonoFijo;

    /** Complemento de la dirección: número de casa, apartamento, torre... */
    @Column(name = "direccion_adicional", length = 150)
    private String direccionAdicional;

    /** null = aún no responde. El número (`visa`/`pasaporte`) solo se guarda si respondió Sí. */
    @Column(name = "tiene_visa")
    private Boolean tieneVisa;

    @Column(name = "visa_pais", length = 100)
    private String visaPais;

    @Column(name = "tiene_pasaporte")
    private Boolean tienePasaporte;

    /** El candidato marcó "No tengo estudios para registrar": la lista de educación va vacía. */
    @Column(name = "sin_estudios")
    private Boolean sinEstudios;

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

    /** Declaraciones: respuestas a las preguntas que administra el admin (ItemHojaVida PREGUNTA). */
    @OneToMany(mappedBy = "hojaVidaEvaluado", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @Builder.Default
    private List<RespuestaHojaVida> respuestas = new ArrayList<>();
}
