package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.dto.GenerarNotaAjusteRequest;
import com.polygraph.erp.modules.pagos.dto.NotaAjusteResponse;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.entity.NotaAjuste;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.NotaAjusteRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import com.polygraph.erp.shared.enums.MotivoNotaAjuste;
import com.polygraph.erp.shared.enums.TipoNotaAjuste;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Notas crédito/débito internas — un ajuste de valor sobre una orden prepago ya cobrada o una
 * factura pospago ya generada. NO son facturas electrónicas DIAN (eso requiere conectar Factus,
 * fase 3 todavía sin empezar — mismo aviso que ComprobanteCompra), pero sí mueven plata real
 * dentro del sistema:
 * <p>
 * - Nota crédito prepago: si el valor de la nota es exactamente el total de la orden (una nota
 *   que cubre el saldo completo, no una parcial), se le devuelven al cliente las unidades de esa
 *   orden a su bolsa (ver SaldoServicioCliente) — se bloquea si ya consumió alguna. Una nota
 *   parcial (menor al total) queda solo como registro financiero, porque no hay forma segura de
 *   saber qué unidades específicas corresponden a un valor parcial en dinero.
 * - Nota crédito pospago: siempre restituye crédito disponible por el valor exacto de la nota
 *   (acá sí es seguro para montos parciales, es una sola cifra en dinero, no unidades).
 * - Nota débito (prepago o pospago): solo queda como registro financiero, no mueve saldo ni cupo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotaAjusteService {

    private final NotaAjusteRepository notaAjusteRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraItemRepository ordenCompraItemRepository;
    private final SaldoServicioClienteRepository saldoServicioClienteRepository;
    private final com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository lotePrepagoRepository;
    private final FacturaPospagoRepository facturaPospagoRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    @Transactional
    public NotaAjusteResponse generarNotaOrden(Long idOrdenCompra, TipoNotaAjuste tipo, GenerarNotaAjusteRequest req, String emailAdmin) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrdenCompra)
                .orElseThrow(() -> new ApiException("Orden no encontrada", HttpStatus.NOT_FOUND));
        if (orden.getEstado() != EstadoOrdenCompra.APROBADA) {
            throw new ApiException("Solo se pueden generar notas sobre órdenes ya aprobadas", HttpStatus.BAD_REQUEST);
        }
        Usuario admin = resolverAdmin(emailAdmin);
        MotivoNotaAjuste motivo = resolverMotivo(req.motivo());

        if (tipo == TipoNotaAjuste.CREDITO) {
            BigDecimal yaAcreditado = notaAjusteRepository.sumMontoByOrdenCompraAndTipo(idOrdenCompra, TipoNotaAjuste.CREDITO);
            if (yaAcreditado.add(req.monto()).compareTo(orden.getMontoTotal()) > 0) {
                throw new ApiException("El valor de la nota supera lo que queda por acreditar de esta orden", HttpStatus.CONFLICT);
            }
            if (req.monto().compareTo(orden.getMontoTotal()) == 0) {
                revertirSaldoOrden(orden);
            }
        }

        NotaAjuste nota = NotaAjuste.builder()
                .tipo(tipo)
                .ordenCompra(orden)
                .cliente(orden.getCliente())
                .referencia(generarReferencia(tipo))
                .montoTotal(req.monto())
                .motivo(motivo)
                .observaciones(req.observaciones())
                .fechaGeneracion(LocalDateTime.now())
                .generadaPor(admin)
                .build();
        notaAjusteRepository.save(nota);

        notificar(tipo, nota, orden.getCliente());
        return mapear(nota);
    }

    @Transactional
    public NotaAjusteResponse generarNotaFactura(Long idFactura, TipoNotaAjuste tipo, GenerarNotaAjusteRequest req, String emailAdmin) {
        FacturaPospago factura = facturaPospagoRepository.findById(idFactura)
                .orElseThrow(() -> new ApiException("Factura no encontrada", HttpStatus.NOT_FOUND));
        Usuario admin = resolverAdmin(emailAdmin);
        MotivoNotaAjuste motivo = resolverMotivo(req.motivo());

        if (tipo == TipoNotaAjuste.CREDITO) {
            BigDecimal yaAcreditado = notaAjusteRepository.sumMontoByFacturaPospagoAndTipo(idFactura, TipoNotaAjuste.CREDITO);
            if (yaAcreditado.add(req.monto()).compareTo(factura.getMontoTotal()) > 0) {
                throw new ApiException("El valor de la nota supera lo que queda por acreditar de esta factura", HttpStatus.CONFLICT);
            }
            ClientePospago pospago = clientePospagoRepository.findByIdCliente(factura.getCliente().getIdCliente())
                    .orElseThrow(() -> new ApiException("Este cliente no tiene cupo pospago configurado", HttpStatus.CONFLICT));
            BigDecimal disponible = pospago.getCreditoDisponible() != null ? pospago.getCreditoDisponible() : BigDecimal.ZERO;
            pospago.setCreditoDisponible(disponible.add(req.monto()));
            clientePospagoRepository.save(pospago);
        }

        NotaAjuste nota = NotaAjuste.builder()
                .tipo(tipo)
                .facturaPospago(factura)
                .cliente(factura.getCliente())
                .referencia(generarReferencia(tipo))
                .montoTotal(req.monto())
                .motivo(motivo)
                .observaciones(req.observaciones())
                .fechaGeneracion(LocalDateTime.now())
                .generadaPor(admin)
                .build();
        notaAjusteRepository.save(nota);

        notificar(tipo, nota, factura.getCliente());
        return mapear(nota);
    }

    @Transactional(readOnly = true)
    public List<NotaAjusteResponse> listarPorOrden(Long idOrdenCompra) {
        return notaAjusteRepository.findByOrdenCompra_IdOrdenCompraOrderByFechaGeneracionDesc(idOrdenCompra).stream()
                .map(this::mapear).toList();
    }

    @Transactional(readOnly = true)
    public List<NotaAjusteResponse> listarPorFactura(Long idFactura) {
        return notaAjusteRepository.findByFacturaPospago_IdFacturaOrderByFechaGeneracionDesc(idFactura).stream()
                .map(this::mapear).toList();
    }

    /** Le devuelve al cliente, en su bolsa de servicios, exactamente las unidades que traía esta
     *  orden — solo se llama cuando la nota crédito cubre el 100% del valor original. Se bloquea
     *  si ya consumió alguna de esas unidades, para no dejar cantidadDisponible en negativo. */
    private void revertirSaldoOrden(OrdenCompra orden) {
        List<OrdenCompraItem> items = ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra());
        for (OrdenCompraItem item : items) {
            SaldoServicioCliente saldo = saldoServicioClienteRepository
                    .findByCliente_IdClienteAndProceso_IdProceso(orden.getCliente().getIdCliente(), item.getProceso().getIdProceso())
                    .orElseThrow(() -> new ApiException(
                            "El cliente ya no tiene saldo de '" + item.getProceso().getNombreProceso() + "' — probablemente ya consumió estas unidades",
                            HttpStatus.CONFLICT));
            if (saldo.getCantidadDisponible() < item.getCantidad()) {
                throw new ApiException(
                        "El cliente ya consumió unidades de '" + item.getProceso().getNombreProceso() + "' de esta orden — no se puede generar la nota crédito completa",
                        HttpStatus.CONFLICT);
            }
            // El lote de esta compra también pierde las unidades; si la bolsa ya gastó unidades de este lote (FIFO), no se puede devolver completo
            var lote = lotePrepagoRepository.findByItem_IdItem(item.getIdItem()).orElse(null);
            if (lote != null) {
                if (lote.restante() < item.getCantidad()) {
                    throw new ApiException("Ya se usaron unidades de '" + item.getProceso().getNombreProceso() + "' de esta compra — no se puede generar la nota crédito completa",
                            HttpStatus.CONFLICT);
                }
                lote.setCantidadComprada(lote.getCantidadComprada() - item.getCantidad());
                if (lote.getCantidadComprada() > 0) lotePrepagoRepository.save(lote); else lotePrepagoRepository.delete(lote);
            }
            saldo.setCantidadDisponible(saldo.getCantidadDisponible() - item.getCantidad());
            saldo.setCantidadComprada(saldo.getCantidadComprada() - item.getCantidad());
            saldo.setFechaActualizacion(LocalDateTime.now());
            saldoServicioClienteRepository.save(saldo);
        }
    }

    private void notificar(TipoNotaAjuste tipo, NotaAjuste nota, Cliente cliente) {
        String nombreCliente = "NATURAL".equals(cliente.getTipoPersona() != null ? cliente.getTipoPersona().name() : null)
                ? ((cliente.getNombre() != null ? cliente.getNombre() : "") + " " + (cliente.getApellido() != null ? cliente.getApellido() : "")).trim()
                : (cliente.getRazonSocial() != null ? cliente.getRazonSocial() : cliente.getNombreComercial());
        String tipoLabel = tipo == TipoNotaAjuste.CREDITO ? "Nota crédito" : "Nota débito";
        log.info("{} generada: id={}, referencia={}, cliente={}, monto={}",
                tipoLabel, nota.getIdNota(), nota.getReferencia(), cliente.getIdCliente(), nota.getMontoTotal());
        notificacionService.crearParaAdmins("FACTURACION", tipoLabel + " generada",
                tipoLabel + " " + nota.getReferencia() + " por " + nota.getMontoTotal() + " a " + nombreCliente);
        // Antes esto solo le llegaba a administración — el cliente nunca se enteraba de que le
        // ajustaron una orden/factura, tenía que notar el cambio de saldo por su cuenta.
        notificacionService.crearParaCliente(cliente.getIdCliente(), "NOTA_AJUSTE", tipoLabel + " aplicada",
                tipoLabel + " " + nota.getReferencia() + " por " + nota.getMontoTotal() + ".");
    }

    private Usuario resolverAdmin(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private MotivoNotaAjuste resolverMotivo(String motivo) {
        try {
            return MotivoNotaAjuste.valueOf(motivo);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Motivo inválido", HttpStatus.BAD_REQUEST);
        }
    }

    private String generarReferencia(TipoNotaAjuste tipo) {
        String prefijo = tipo == TipoNotaAjuste.CREDITO ? "NC" : "ND";
        String corto = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefijo + "-" + corto;
    }

    private NotaAjusteResponse mapear(NotaAjuste n) {
        String generadaPorNombre = n.getGeneradaPor() != null
                ? (n.getGeneradaPor().getNombre() + (n.getGeneradaPor().getApellido() != null ? " " + n.getGeneradaPor().getApellido() : ""))
                : null;
        return new NotaAjusteResponse(
                n.getIdNota(), n.getTipo().name(), n.getReferencia(), n.getMontoTotal(), n.getMotivo().name(),
                n.getObservaciones(), n.getFechaGeneracion(), generadaPorNombre);
    }
}
