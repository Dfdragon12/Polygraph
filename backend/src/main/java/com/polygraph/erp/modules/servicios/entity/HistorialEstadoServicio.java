package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "historial_estados_servicio")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"servicio", "usuario"})
public class HistorialEstadoServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio", nullable = false)
    private Servicio servicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 40)
    private EstadoServicio estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 40)
    private EstadoServicio estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_cambio")
    private LocalDateTime fechaCambio;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;
}
