package com.polygraph.erp.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Una aceptación (o revocación) de un texto legal. Solo se inserta: es evidencia histórica. */
@Entity
@Table(name = "aceptaciones_legales")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AceptacionLegal {

    public enum Tipo { TERMINOS, PRIVACIDAD, MARKETING, AUTORIZACION_CANDIDATO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario", updatable = false)
    private Long idUsuario;

    @Column(length = 150, updatable = false)
    private String email;

    /** Dato libre para ubicar el caso, p. ej. "candidato:12;servicio:40". */
    @Column(length = 100, updatable = false)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private Tipo tipo;

    @Column(name = "version_texto", nullable = false, length = 20, updatable = false)
    private String versionTexto;

    @Column(nullable = false, updatable = false)
    private boolean aceptado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "ip_hash", length = 64, updatable = false)
    private String ipHash;
}
