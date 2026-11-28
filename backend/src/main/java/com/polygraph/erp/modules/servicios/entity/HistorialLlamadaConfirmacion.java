package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bitácora de cada llamada de confirmación de asistencia (poligrafía/visita) — quién llamó, qué
 * resultado tuvo y cuándo. Log de solo-inserción, igual que HistorialAsignacion: no reemplaza el
 * estado actual en ServicioSubproceso.estadoConfirmacion, solo deja el rastro de cada intento.
 */
@Entity
@Table(name = "historial_llamadas_confirmacion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialLlamadaConfirmacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio_subproceso", nullable = false)
    private ServicioSubproceso servicioSubproceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_confirmacion", nullable = false, length = 20)
    private EstadoConfirmacionAsistencia estadoConfirmacion;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actor", nullable = false)
    private Usuario actor;

    @Column(name = "fecha_llamada", nullable = false)
    private LocalDateTime fechaLlamada;

    @PrePersist
    protected void alCrear() {
        if (fechaLlamada == null) {
            fechaLlamada = LocalDateTime.now();
        }
    }
}
