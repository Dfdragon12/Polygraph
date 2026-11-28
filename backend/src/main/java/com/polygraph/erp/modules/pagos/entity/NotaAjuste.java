package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.shared.enums.MotivoNotaAjuste;
import com.polygraph.erp.shared.enums.TipoNotaAjuste;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Nota crédito o débito interna sobre una orden prepago ya cobrada o una factura pospago ya
 *  generada — un ajuste de valor, no una factura electrónica DIAN (eso requiere conectar Factus,
 *  fase 3 todavía sin empezar — ver el aviso en ComprobanteCompra). Referencia exactamente una de
 *  las dos: ordenCompra (prepago) o facturaPospago (pospago), nunca ambas — ver
 *  NotaAjusteService.generar. */
@Entity
@Table(name = "notas_ajuste")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaAjuste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota")
    private Long idNota;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoNotaAjuste tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_compra")
    private OrdenCompra ordenCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_factura_pospago")
    private FacturaPospago facturaPospago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "referencia", nullable = false, unique = true, length = 50)
    private String referencia;

    @Column(name = "monto_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 30)
    private MotivoNotaAjuste motivo;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_generada_por", nullable = false)
    private Usuario generadaPor;
}
