package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "links_candidato")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"servicio", "generadoPor"})
public class LinkCandidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio", nullable = false)
    private Servicio servicio;

    @Column(name = "token", unique = true, nullable = false, length = 200)
    private String token;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "usado", nullable = false)
    private Boolean usado;

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "fecha_primer_ingreso")
    private LocalDateTime fechaPrimerIngreso;

    @Column(name = "intentos_fallidos", nullable = false)
    @Builder.Default
    private Short intentosFallidos = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generado_por_id")
    private Usuario generadoPor;
}
