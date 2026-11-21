package com.polygraph.erp.modules.solicitudes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PB-03: una versión del PDF único del estudio completo (v1, v2...). Solo la vigente se entrega al cliente. Si un subproceso deja de estar
 * publicado, la vigente deja de estarlo y, al emitir de nuevo, se crea la versión siguiente.
 */
@Entity
@Table(name = "documentos_estudio")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoEstudio {

    public static final String GENERANDO = "GENERANDO";
    public static final String LISTO = "LISTO";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(nullable = false)
    private Integer version;

    /** PRELIMINAR o FINAL. */
    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(length = 10)
    private String nivel;

    @Builder.Default
    @Column(nullable = false, length = 12)
    private String estado = GENERANDO;

    @Builder.Default
    @Column(nullable = false)
    private Boolean vigente = true;

    private String ruta;

    private Integer paginas;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
