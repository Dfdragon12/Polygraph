package com.polygraph.erp.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Un parámetro del cálculo de tiempos (zona_horaria, hora_corte, umbral_por_vencer_dias). */
@Entity
@Table(name = "parametros_tiempo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametroTiempo {

    @Id
    @Column(name = "clave", length = 50)
    private String clave;

    @Column(name = "valor", nullable = false, length = 100)
    private String valor;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    @Column(name = "modificado_por", length = 150)
    private String modificadoPor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
