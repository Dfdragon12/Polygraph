package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Registro de solo inserción de cada cambio de un parámetro (regla 10). */
@Entity
@Table(name = "parametros_tienda_auditoria")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametroTiendaAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "clave", nullable = false, length = 60)
    private String clave;

    @Column(name = "valor_anterior", length = 200)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = 200)
    private String valorNuevo;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
