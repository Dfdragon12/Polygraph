package com.polygraph.erp.modules.solicitudes.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "historial_solicitudes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"solicitud", "usuario"})
public class HistorialSolicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private Solicitud solicitud;

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
