package com.polygraph.erp.modules.solicitudes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Llave de idempotencia de un envío de solicitud (NS-03): (usuario, clave) es única; guarda los servicios que creó el primer envío. */
@Entity
@Table(name = "solicitudes_idempotencia", uniqueConstraints = @UniqueConstraint(name = "ux_solicitudes_idempotencia", columnNames = {"id_usuario", "clave"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "clave", nullable = false, length = 80)
    private String clave;

    /** ids de los servicios creados, separados por coma. */
    @Column(name = "ids_servicios", length = 500)
    private String idsServicios;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;
}
