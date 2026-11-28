package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.AccionHistorialAsignacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bitácora de cada cambio de asignación de un subproceso: quién estaba asignado antes, quién
 * queda ahora, y quién hizo el cambio. Log de solo-inserción — nunca se actualiza ni se borra
 * una fila existente. Existe para no perder el rastro de quién estuvo asignado cuando se
 * desasigna (antes esa información se pisaba con NULL sin dejar rastro).
 */
@Entity
@Table(name = "historial_asignaciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialAsignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio_subproceso", nullable = false)
    private ServicioSubproceso servicioSubproceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 20)
    private AccionHistorialAsignacion accion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_anterior")
    private Usuario usuarioAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_nuevo")
    private Usuario usuarioNuevo;

    @Column(name = "fecha_programada_anterior")
    private LocalDateTime fechaProgramadaAnterior;

    @Column(name = "fecha_programada_nueva")
    private LocalDateTime fechaProgramadaNueva;

    /** Quién ejecutó la acción (el gestor/programador logueado), no a quién se asignó. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actor")
    private Usuario actor;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;

    @PrePersist
    protected void alCrear() {
        if (fechaEvento == null) {
            fechaEvento = LocalDateTime.now();
        }
    }
}
