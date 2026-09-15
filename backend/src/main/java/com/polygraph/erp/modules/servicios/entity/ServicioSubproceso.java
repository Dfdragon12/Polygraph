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
@ToString(exclude = {"servicio", "tipoProgreso", "usuarioAsignado", "programadorResponsable"})
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
}
