package com.polygraph.erp.modules.tienda.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** LP-01: cambio en la lista de precios pospago (precio manual, volver a automático, ofrecido, restablecer). Solo se inserta. */
@Entity
@Table(name = "catalogo_precio_pospago_historial")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPrecioPospago {

    public enum Accion { PRECIO_MANUAL, VOLVER_AUTO, OFRECIDO_SI, OFRECIDO_NO, RESTABLECER_TODOS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_proceso", nullable = false, updatable = false)
    private Integer idProceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 20, updatable = false)
    private Accion accion;

    @Column(name = "modo_anterior", length = 20, updatable = false)
    private String modoAnterior;

    @Column(name = "modo_nuevo", length = 20, updatable = false)
    private String modoNuevo;

    /** Precio efectivo (sin IVA) antes y después; null = sin precio. */
    @Column(name = "precio_anterior", updatable = false)
    private Long precioAnterior;

    @Column(name = "precio_nuevo", updatable = false)
    private Long precioNuevo;

    @Column(name = "ofrecido_anterior", updatable = false)
    private Boolean ofrecidoAnterior;

    @Column(name = "ofrecido_nuevo", updatable = false)
    private Boolean ofrecidoNuevo;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private Long idUsuario;

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDateTime fecha;
}
