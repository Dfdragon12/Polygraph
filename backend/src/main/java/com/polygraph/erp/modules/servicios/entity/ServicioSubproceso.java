package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "servicio_subprocesos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"servicio", "tipoProgreso", "usuarioAsignado", "programadorResponsable", "usuarioNoRealizado"})
public class ServicioSubproceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio", nullable = false)
    private Servicio servicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_progreso", nullable = false)
    private TipoProgreso tipoProgreso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_asignado")
    private Usuario usuarioAsignado;

    /** Solo aplica a subprocesos de poligrafía/visita (rolResponsable POLIGRAFISTA/VISITADOR) —
     *  a qué PROGRAMADOR le encargó el GESTOR coordinarlo. Ese programador sigue siendo quien
     *  decide el poligrafista/visitador puntual y la fecha (usuarioAsignado, arriba) — este campo
     *  es solo de quién quedó a cargo, no reemplaza esa asignación final. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programador_responsable")
    private Usuario programadorResponsable;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoAsignacion estado;

    @Column(name = "fecha_programada")
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    /** Cuándo el gestor/programador aprobó este subproceso (pasó de COMPLETADO a PUBLICADO) — ver
     *  AsignacionService.aprobar(). Distinto de fechaCompletado: esa es cuando quien lo diligenció
     *  terminó, esta es cuando alguien más lo revisó y lo aprobó, casi nunca el mismo momento. */
    @Column(name = "fecha_publicado")
    private LocalDateTime fechaPublicado;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    /** Duración real de este subproceso puntual — si es null, se usa el estimado del catálogo
     *  (tipoProgreso.minutosEstimados). El tiempo real varía caso a caso. */
    @Column(name = "duracion_minutos")
    private Integer duracionMinutos;

    /** Confirmación de asistencia — para poligrafía/visita, si ya se llamó al candidato para
     *  confirmar que va a estar antes de que el poligrafista/visitador se desplace. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_confirmacion", length = 20, nullable = false)
    private EstadoConfirmacionAsistencia estadoConfirmacion = EstadoConfirmacionAsistencia.SIN_LLAMAR;

    @Column(name = "notas_confirmacion", columnDefinition = "TEXT")
    private String notasConfirmacion;

    /** Versión de TipoProgreso.versionPlantilla vigente cuando se creó este subproceso — se
     *  compara contra el valor actual de tipoProgreso.versionPlantilla para saber si la plantilla
     *  cambió (admin agregó/quitó campos) después de que este subproceso ya existía. */
    @Builder.Default
    @Column(name = "version_plantilla", nullable = false)
    private Integer versionPlantilla = 1;

    /** Ruta del .docx (FormularioPlantillaConfig.plantillaWordRuta) que estaba activo cuando este
     *  subproceso se completó — null mientras sigue ASIGNADO (usa siempre el Word vigente, igual
     *  que con las secciones) y también null en subprocesos completados ANTES de que existiera
     *  esta columna. Se estampa una sola vez, en AsignacionService.completar(), para que
     *  generar/descargar el documento de un subproceso ya cerrado siempre use el mismo archivo con
     *  el que se completó, sin importar que después se suba un Word nuevo. */
    @Column(name = "ruta_word_congelada")
    private String rutaWordCongelada;

    /** Lo marcó quien coordina como "No realizado" al resolver una novedad (ej. el candidato no
     *  aceptó la poligrafía). El subproceso queda CANCELADO; para el motor de riesgo todo
     *  cancelado cuenta como NO_REALIZADO — esta marca solo registra que fue por esa razón. */
    @Builder.Default
    @Column(name = "no_realizado", nullable = false)
    private Boolean noRealizado = false;

    @Column(name = "motivo_no_realizado", columnDefinition = "TEXT")
    private String motivoNoRealizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_no_realizado")
    private Usuario usuarioNoRealizado;

    @Column(name = "fecha_no_realizado")
    private LocalDateTime fechaNoRealizado;
}
