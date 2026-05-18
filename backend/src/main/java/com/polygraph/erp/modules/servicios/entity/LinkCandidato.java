package com.polygraph.erp.modules.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "links_candidato")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "servicio")
public class LinkCandidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio")
    private Servicio servicio;

    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Column(name = "token", unique = true, nullable = false, length = 200)
    private String token;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "usado", nullable = false)
    private Boolean usado;
}
