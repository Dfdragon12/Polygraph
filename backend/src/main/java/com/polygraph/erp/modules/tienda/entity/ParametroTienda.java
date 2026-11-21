package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Parámetro global o interruptor de la tienda y el pospago (TP-01). El valor viaja como texto; `tipo` dice cómo leerlo. */
@Entity
@Table(name = "parametros_tienda")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametroTienda {

    @Id
    @Column(name = "clave", length = 60)
    private String clave;

    @Column(name = "valor", nullable = false, length = 200)
    private String valor;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Column(name = "actualizado_por", length = 150)
    private String actualizadoPor;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
