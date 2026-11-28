package com.polygraph.erp.modules.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Una fila por cada cambio de estado o de marca de un subproceso (SE-03). De SOLO INSERCIÓN: la base de datos rechaza
 * actualizar o borrar. Sin relaciones JPA a propósito: el registro no depende de que el subproceso o el usuario sigan existiendo.
 */
@Entity
@Table(name = "registro_cambios_subproceso")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RegistroCambioSubproceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_servicio", nullable = false, updatable = false)
    private Integer idServicio;

    @Column(name = "id_servicio_subproceso", nullable = false, updatable = false)
    private Long idServicioSubproceso;

    @Column(name = "id_tipo_progreso", updatable = false)
    private Integer idTipoProgreso;

    @Column(name = "tipo_subproceso", length = 150, updatable = false)
    private String tipoSubproceso;

    @Column(name = "estado_anterior", length = 20, updatable = false)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 20, updatable = false)
    private String estadoNuevo;

    /** DEVUELTO, REPROGRAMADA, ASIGNADA_NO_REALIZADA, CANCELADA o CANDIDATO_DESISTIO (no son estados). */
    @Column(name = "marca", length = 30, updatable = false)
    private String marca;

    @Column(name = "motivo", length = 400, updatable = false)
    private String motivo;

    @Column(name = "texto_adicional", columnDefinition = "TEXT", updatable = false)
    private String textoAdicional;

    /** Quien tenía el subproceso asignado al momento del cambio. */
    @Column(name = "id_responsable", updatable = false)
    private Long idResponsable;

    /** Quien hizo el cambio. */
    @Column(name = "id_usuario", updatable = false)
    private Long idUsuario;

    /** El instante del cambio, con zona. */
    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private OffsetDateTime fechaHora;

    /** La misma hora vista en Colombia (America/Bogota): es la que se muestra y con la que se corta el día. */
    @Column(name = "fecha_bogota", nullable = false, updatable = false)
    private LocalDateTime fechaBogota;

    /** EN_VIVO = lo escribió el sistema al ocurrir; MIGRADO = reconstruido del historial de asignaciones (V226). */
    @Builder.Default
    @Column(name = "origen", nullable = false, length = 10, updatable = false)
    private String origen = "EN_VIVO";

    @Column(name = "id_historial_asignacion", updatable = false)
    private Long idHistorialAsignacion;
}
