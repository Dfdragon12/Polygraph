package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de la tabla unificada de Facturacion.jsx — mezcla órdenes de compra prepago y
 *  facturas pospago bajo un mismo estado ("PAGADO"/"POR_FACTURAR"/"PENDIENTE"/"VENCIDO"/
 *  "RECHAZADO"/"EXPIRADA") para poder mostrarlas juntas, ordenadas por fecha, como una sola tabla
 *  de "documentos de facturación" — ver FacturacionResumenService.listarDocumentos. "POR_FACTURAR"
 *  es una orden prepago ya pagada por el cliente pero a la que todavía no se le ha generado el
 *  comprobante — se separa de "PAGADO" para que no se confunda con un documento ya completo. */
public record DocumentoFacturacionResponse(
        String tipo,               // "PREPAGO" | "POSPAGO"
        Long idOrdenCompra,        // solo prepago — para "Generar factura" y ver el detalle
        Long idFactura,            // solo pospago — para ver el detalle
        String referencia,
        Integer idComprobante,     // solo prepago, y solo si ya se generó el comprobante — con esto
                                    // el frontend arma el mismo número "CP-000123" que ya se ve en
                                    // el detalle, en vez de la referencia interna (PYG-...) de la orden
        Integer idCliente,
        String nit,
        String nombreCliente,
        LocalDateTime fechaEmision,
        LocalDate fechaVencimiento, // null para prepago (una orden no vence, solo expira sin pagarse)
        BigDecimal montoTotal,
        BigDecimal montoNotasCredito, // suma de notas crédito ya generadas sobre este documento
                                        // puntual (0 si no tiene ninguna) — el frontend resta esto
                                        // de montoTotal para mostrar el valor neto en la tabla
        BigDecimal montoNotasDebito,  // igual, pero de notas débito — el frontend SUMA esto al
                                        // valor neto (una nota débito aumenta lo que debe el cliente)
        String estado,             // unificado — ver mapeo en FacturacionResumenService
        boolean puedeGenerarFactura, // true solo si es prepago APROBADA sin comprobante todavía
        boolean esConsumoPendiente  // true solo para el pospago que TODAVÍA no tiene factura — el
                                     // botón de generar acá pide fecha de vencimiento en vez de
                                     // disparar directo (ver ClientePospagoService.generarFactura)
) {}
