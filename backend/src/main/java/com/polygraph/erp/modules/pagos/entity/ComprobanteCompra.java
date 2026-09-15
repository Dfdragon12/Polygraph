package com.polygraph.erp.modules.pagos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Comprobante de compra interno — no es una factura electrónica DIAN válida (ver V136). */
@Entity
@Table(name = "comprobantes_compra")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobanteCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Integer idComprobante;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra", nullable = false, unique = true)
    private OrdenCompra ordenCompra;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;
}
