package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Quién importó precios y textos, cuándo y qué (TP-05). Solo se inserta. */
@Entity
@Table(name = "importaciones_catalogo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportacionCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_importacion")
    private Long idImportacion;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "total_servicios", nullable = false)
    private Integer totalServicios;

    @Column(name = "ids_proceso", nullable = false, length = 500)
    private String idsProceso;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;
}
