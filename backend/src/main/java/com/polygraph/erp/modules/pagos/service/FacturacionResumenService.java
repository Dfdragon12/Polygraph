package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.dto.DocumentoFacturacionResponse;
import com.polygraph.erp.modules.pagos.dto.PendienteFacturarPospagoResponse;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.repository.ComprobanteCompraRepository;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.NotaAjusteRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.shared.enums.TipoNotaAjuste;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Vista unificada de "documentos de facturación" para la tabla de Facturacion.jsx — mezcla
 *  órdenes de compra prepago y facturas pospago (dos conceptos de negocio distintos, ver
 *  OrdenCompraService y ClientePospagoService) bajo un estado común, ordenados por fecha, como si
 *  fuera un solo listado. Trae hasta LIMITE_POR_TIPO de cada tipo (los más recientes) y pagina en
 *  memoria — a este volumen (cientos de registros, no decenas de miles) es simple y suficiente;
 *  si el negocio crece mucho más, esto se puede reemplazar por una consulta nativa con UNION. */
@Service
@RequiredArgsConstructor
public class FacturacionResumenService {

    private static final int LIMITE_POR_TIPO = 500;

    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturaPospagoRepository facturaPospagoRepository;
    private final ComprobanteCompraRepository comprobanteCompraRepository;
    private final NotaAjusteRepository notaAjusteRepository;
    private final ClientePospagoService clientePospagoService;

    @Transactional(readOnly = true)
    public Page<DocumentoFacturacionResponse> listarDocumentos(
            String tipo, String estado, String q, boolean soloPorFacturar, boolean soloFacturadas, Pageable pageable) {
        List<DocumentoFacturacionResponse> documentos = new ArrayList<>();

        if (tipo == null || tipo.isBlank() || tipo.equalsIgnoreCase("PREPAGO")) {
            for (OrdenCompra o : ordenCompraRepository.findAllByOrderByFechaCreacionDesc(PageRequest.of(0, LIMITE_POR_TIPO))) {
                documentos.add(mapearOrden(o));
            }
        }
        if (tipo == null || tipo.isBlank() || tipo.equalsIgnoreCase("POSPAGO")) {
            for (FacturaPospago f : facturaPospagoRepository.findAllByOrderByFechaGeneracionDesc(PageRequest.of(0, LIMITE_POR_TIPO))) {
                documentos.add(mapearFactura(f));
            }
            // Consumo pospago que todavía no tiene ninguna factura generada — sin esto, esos
            // servicios pendientes por facturar eran invisibles en esta tabla (solo se veían
            // desde el cliente puntual). Se muestran como una fila "PENDIENTE" por cliente.
            for (PendienteFacturarPospagoResponse p : clientePospagoService.listarPendientesGlobal()) {
                documentos.add(mapearPendiente(p));
            }
        }

        String estadoFiltro = estado != null && !estado.isBlank() ? estado.toUpperCase() : null;
        String qFiltro = q != null && !q.isBlank() ? q.trim().toLowerCase() : null;

        List<DocumentoFacturacionResponse> filtrados = documentos.stream()
                .filter(d -> estadoFiltro == null || d.estado().equals(estadoFiltro))
                .filter(d -> !soloPorFacturar || d.puedeGenerarFactura() || d.esConsumoPendiente())
                .filter(d -> !soloFacturadas || esFacturado(d))
                .filter(d -> qFiltro == null
                        || d.nombreCliente().toLowerCase().contains(qFiltro)
                        || d.referencia().toLowerCase().contains(qFiltro))
                .sorted(Comparator.comparing(DocumentoFacturacionResponse::fechaEmision).reversed())
                .toList();

        int desde = (int) pageable.getOffset();
        if (desde >= filtrados.size()) {
            return new PageImpl<>(List.of(), pageable, filtrados.size());
        }
        int hasta = Math.min(desde + pageable.getPageSize(), filtrados.size());
        return new PageImpl<>(filtrados.subList(desde, hasta), pageable, filtrados.size());
    }

    private DocumentoFacturacionResponse mapearOrden(OrdenCompra o) {
        Integer idComprobante = comprobanteCompraRepository.findByOrdenCompra_IdOrdenCompra(o.getIdOrdenCompra())
                .map(c -> c.getIdComprobante()).orElse(null);
        boolean puedeGenerarFactura = o.getEstado().name().equals("APROBADA") && idComprobante == null;
        // Una orden APROBADA ya está paga — pero si todavía no tiene comprobante generado, no se
        // marca como "PAGADO" (se confundiría con un documento ya completo): se marca como
        // "POR_FACTURAR", que es justamente lo que el filtro/chip "Por facturar" de la tabla busca.
        String estado = switch (o.getEstado()) {
            case APROBADA -> puedeGenerarFactura ? "POR_FACTURAR" : "PAGADO";
            case PENDIENTE -> "PENDIENTE";
            case RECHAZADA -> "RECHAZADO";
            case EXPIRADA -> "EXPIRADA";
        };
        // Las notas crédito/débito solo se pueden generar sobre órdenes ya APROBADA (ver
        // NotaAjusteService.generarNotaOrden) — para el resto ni vale la pena consultar.
        boolean puedeTenerNotas = o.getEstado().name().equals("APROBADA");
        BigDecimal montoNotasCredito = puedeTenerNotas
                ? notaAjusteRepository.sumMontoByOrdenCompraAndTipo(o.getIdOrdenCompra(), TipoNotaAjuste.CREDITO)
                : BigDecimal.ZERO;
        BigDecimal montoNotasDebito = puedeTenerNotas
                ? notaAjusteRepository.sumMontoByOrdenCompraAndTipo(o.getIdOrdenCompra(), TipoNotaAjuste.DEBITO)
                : BigDecimal.ZERO;
        return new DocumentoFacturacionResponse(
                "PREPAGO", o.getIdOrdenCompra(), null, o.getReferencia(), idComprobante, o.getCliente().getIdCliente(),
                o.getCliente().getNit(), nombreCliente(o.getCliente()),
                o.getFechaCreacion(), null, o.getMontoTotal(), montoNotasCredito, montoNotasDebito, estado, puedeGenerarFactura, false);
    }

    private DocumentoFacturacionResponse mapearFactura(FacturaPospago f) {
        String estado = switch (f.getEstado()) {
            case PAGADA -> "PAGADO";
            case PENDIENTE -> "PENDIENTE";
            case VENCIDA -> "VENCIDO";
        };
        BigDecimal montoNotasCredito = notaAjusteRepository.sumMontoByFacturaPospagoAndTipo(f.getIdFactura(), TipoNotaAjuste.CREDITO);
        BigDecimal montoNotasDebito = notaAjusteRepository.sumMontoByFacturaPospagoAndTipo(f.getIdFactura(), TipoNotaAjuste.DEBITO);
        return new DocumentoFacturacionResponse(
                "POSPAGO", null, f.getIdFactura(), f.getReferencia(), null, f.getCliente().getIdCliente(),
                f.getCliente().getNit(), nombreCliente(f.getCliente()),
                f.getFechaGeneracion(), f.getFechaVencimiento(), f.getMontoTotal(), montoNotasCredito, montoNotasDebito, estado, false, false);
    }

    private DocumentoFacturacionResponse mapearPendiente(PendienteFacturarPospagoResponse p) {
        return new DocumentoFacturacionResponse(
                "POSPAGO", null, null, "Consumo sin facturar (" + p.cantidadServicios() + ")", null,
                p.idCliente(), p.nit(), p.nombreCliente(), p.primerMovimientoPendiente(), null, p.montoTotal(), BigDecimal.ZERO, BigDecimal.ZERO,
                "PENDIENTE", false, true);
    }

    /** Ya tiene un documento de factura real emitido — prepago con comprobante generado (estado
     *  "PAGADO", que desde el fix de "POR_FACTURAR" ya no incluye lo pagado-sin-comprobante), o
     *  cualquier factura pospago ya generada (esConsumoPendiente=false), sin importar si esa
     *  factura pospago ya se pagó o sigue pendiente/vencida — lo que importa acá es si el
     *  documento existe, no si ya se cobró. */
    private boolean esFacturado(DocumentoFacturacionResponse d) {
        if ("PREPAGO".equals(d.tipo())) {
            return "PAGADO".equals(d.estado());
        }
        return !d.esConsumoPendiente();
    }

    private String nombreCliente(Cliente c) {
        if (c.getTipoPersona() != null && "NATURAL".equals(c.getTipoPersona().name())) {
            String nombre = ((c.getNombre() != null ? c.getNombre() : "") + " " + (c.getApellido() != null ? c.getApellido() : "")).trim();
            return nombre.isBlank() ? c.getEmailPrincipal() : nombre;
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }
}
