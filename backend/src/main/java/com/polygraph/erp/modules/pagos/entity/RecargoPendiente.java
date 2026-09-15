package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.shared.enums.EstadoRecargoPendiente;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Recargo por ciudad generado al crear una solicitud (el candidato queda en una ciudad con
 * recargo para ese proceso) — no se cobra en ese momento: queda pendiente y se suma
 * automáticamente al total de la siguiente compra del cliente en Comprar Servicios.
 */
@Entity
@Table(name = "recargos_pendientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecargoPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recargo_pendiente")
    private Integer idRecargoPendiente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_candidato", nullable = false)
    private Candidato candidato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ciudad", nullable = false)
    private Ciudad ciudad;

    /** Positivo = recargo a cobrar; negativo = crédito (ej. corrección de ciudad de un recargo ya pagado). */
    @Column(name = "monto", precision = 15, scale = 2, nullable = false)
    private BigDecimal monto;

    /** Por qué se generó esta fila, cuando no es el registro simple al crear la solicitud (ej. "Crédito por corrección de ciudad"). */
    @Column(name = "motivo", length = 255)
    private String motivo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoRecargoPendiente estado = EstadoRecargoPendiente.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra")
    private OrdenCompra ordenCompra;

    @Column(name = "fecha_generado", nullable = false)
    private LocalDateTime fechaGenerado;

    @Column(name = "fecha_pagado")
    private LocalDateTime fechaPagado;
}
