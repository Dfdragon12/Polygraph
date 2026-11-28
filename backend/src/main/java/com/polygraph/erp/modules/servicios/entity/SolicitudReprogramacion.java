package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.ImputableA;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un cambio de la fecha de compromiso de una solicitud: de cuál a cuál, por qué, de quién es la causa y quién lo hizo. */
@Entity
@Table(name = "solicitud_reprogramacion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "servicio")
public class SolicitudReprogramacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Servicio servicio;

    @Column(name = "fecha_anterior", nullable = false)
    private LocalDate fechaAnterior;

    @Column(name = "fecha_nueva", nullable = false)
    private LocalDate fechaNueva;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsable", nullable = false, length = 10)
    private ImputableA responsable;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @PrePersist
    void alCrear() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
