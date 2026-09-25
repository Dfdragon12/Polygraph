package com.polygraph.erp.modules.riesgo.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Parámetros de las reglas de piso del motor — una sola fila (id = 1). */
@Entity
@Table(name = "parametros_riesgo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "actualizadoPor")
public class ParametrosRiesgo {

    public static final int ID_UNICO = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    /** Regla A: algún subproceso con puntaje bruto >= este valor. */
    @Column(name = "umbral_servicio_alto", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralServicioAlto;

    /** Regla B: cantidadServiciosModerados o más subprocesos con puntaje bruto > este valor. */
    @Column(name = "umbral_servicio_moderado", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralServicioModerado;

    @Column(name = "cantidad_servicios_moderados", nullable = false)
    private Integer cantidadServiciosModerados;

    @Column(name = "version", nullable = false)
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por")
    private Usuario actualizadoPor;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
