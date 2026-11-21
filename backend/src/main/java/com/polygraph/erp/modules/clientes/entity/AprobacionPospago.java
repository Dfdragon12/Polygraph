package com.polygraph.erp.modules.clientes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Quién aprobó el pospago de un cliente, cuándo, con qué cupo y corte, y la excepción si la hubo (TP-13). Solo se inserta. */
@Entity
@Table(name = "aprobaciones_pospago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionPospago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aprobacion")
    private Long idAprobacion;

    @Column(name = "id_cliente", nullable = false)
    private Integer idCliente;

    @Column(name = "aprobado_por", nullable = false, length = 150)
    private String aprobadoPor;

    @Column(name = "aprobado_en", nullable = false)
    private LocalDateTime aprobadoEn;

    @Column(name = "cupo", precision = 15, scale = 2, nullable = false)
    private BigDecimal cupo;

    @Column(name = "dia_corte", nullable = false)
    private Integer diaCorte;

    @Column(name = "excepcion", nullable = false)
    private Boolean excepcion;

    @Column(name = "motivo_excepcion", length = 1000)
    private String motivoExcepcion;

    /** Copia de lo verificado al aprobar (requisito, si cumplía y de dónde salió). */
    @Column(name = "requisitos", columnDefinition = "TEXT")
    private String requisitos;
}
