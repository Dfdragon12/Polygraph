package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Cambio de precio (o de costo) de un servicio: solo se inserta (regla 10). */
@Entity
@Table(name = "historial_precios_proceso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPrecioProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @Column(name = "id_proceso", nullable = false)
    private Integer idProceso;

    @Column(name = "campo", nullable = false, length = 80)
    private String campo;

    @Column(name = "valor_anterior", length = 60)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = 60)
    private String valorNuevo;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
