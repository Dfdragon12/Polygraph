package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Qué cambió la limpieza del catálogo (TP-10), quién la confirmó y cuándo. Solo se inserta. */
@Entity
@Table(name = "limpieza_catalogo_auditoria")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimpiezaCatalogoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "paso", nullable = false, length = 40)
    private String paso;

    @Column(name = "id_proceso")
    private Integer idProceso;

    @Column(name = "detalle", length = 500)
    private String detalle;
}
