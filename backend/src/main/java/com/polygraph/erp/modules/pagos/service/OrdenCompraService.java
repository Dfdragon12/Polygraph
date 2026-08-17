package com.polygraph.erp.modules.pagos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.dto.*;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.TransaccionPago;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.repository.TransaccionPagoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import com.polygraph.erp.shared.enums.EstadoTransaccionPago;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraItemRepository ordenCompraItemRepository;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ProcesoRepository procesoRepository;
    private final TramoPrecioProcesoRepository tramoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final WompiPasarelaPago pasarelaPago;
    private final SaldoServicioClienteService saldoServicioClienteService;
    private final ObjectMapper objectMapper;

    public OrdenCompraResponse crearOrden(CrearOrdenRequest request, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);

        if (cliente.getTipoCliente() != TipoCliente.PREPAGO) {
            throw new ApiException(
                    "Tu cuenta opera con crédito pospago. Usa 'Nueva Solicitud' para solicitar servicios; no necesitas comprarlos por adelantado.",
                    HttpStatus.BAD_REQUEST);
        }

        LocalDateTime ahora = LocalDateTime.now();
        String referencia = "PYG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

        OrdenCompra orden = OrdenCompra.builder()
                .cliente(cliente)
                .usuario(usuario)
                .referencia(referencia)
                .montoTotal(BigDecimal.ZERO)
                .estado(EstadoOrdenCompra.PENDIENTE)
                .modoSimulado(pasarelaPago.esModoSimulado())
                .fechaCreacion(ahora)
                .fechaActualizacion(ahora)
                .build();
        orden = ordenCompraRepository.save(orden);

        BigDecimal montoTotal = BigDecimal.ZERO;
        List<OrdenCompraItem> items = new java.util.ArrayList<>();
        for (ItemCarritoRequest itemReq : request.items()) {
            Proceso proceso = procesoRepository.findById(itemReq.idProceso())
                    .orElseThrow(() -> new ApiException("Proceso de catálogo no encontrado: " + itemReq.idProceso(), HttpStatus.BAD_REQUEST));

            if (!Boolean.TRUE.equals(proceso.getActivo())) {
                throw new ApiException("El proceso '" + proceso.getNombreProceso() + "' no está disponible", HttpStatus.BAD_REQUEST);
            }
            if (proceso.getValor() == null) {
                throw new ApiException("El proceso '" + proceso.getNombreProceso() + "' no tiene un precio definido para compra en línea", HttpStatus.BAD_REQUEST);
            }

            BigDecimal valorUnitario = resolverValorUnitario(proceso, itemReq.cantidad());
            BigDecimal subtotal = valorUnitario.multiply(BigDecimal.valueOf(itemReq.cantidad()));
            montoTotal = montoTotal.add(subtotal);

            items.add(OrdenCompraItem.builder()
                    .ordenCompra(orden)
                    .proceso(proceso)
                    .cantidad(itemReq.cantidad())
                    .valorUnitario(valorUnitario)
                    .subtotal(subtotal)
                    .build());
        }
        ordenCompraItemRepository.saveAll(items);

        orden.setMontoTotal(montoTotal);
        orden = ordenCompraRepository.save(orden);

        log.info("Orden de compra creada: id={}, cliente={}, monto={}", orden.getIdOrdenCompra(), cliente.getIdCliente(), montoTotal);
        return construirRespuesta(orden, items);
    }

    public IniciarPagoResponse iniciarPago(Long idOrden, String emailUsuario) {
        OrdenCompra orden = resolverOrdenDelCliente(idOrden, emailUsuario);
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE && orden.getEstado() != EstadoOrdenCompra.RECHAZADA) {
            throw new ApiException("Esta orden ya no está pendiente de pago", HttpStatus.CONFLICT);
        }

        // Una orden RECHAZADA se puede reintentar: se reabre como PENDIENTE con los mismos ítems/precios.
        orden.setEstado(EstadoOrdenCompra.PENDIENTE);

        PasarelaPago.IniciarPagoResultado resultado = pasarelaPago.iniciarPago(orden);
        orden.setUrlCheckout(resultado.urlCheckout());
        orden.setModoSimulado(resultado.simulado());
        orden.setFechaActualizacion(LocalDateTime.now());
        ordenCompraRepository.save(orden);

        return new IniciarPagoResponse(resultado.urlCheckout(), resultado.simulado());
    }

    @Transactional(readOnly = true)
    public OrdenCompraResponse obtenerOrden(Long idOrden, String emailUsuario) {
        OrdenCompra orden = resolverOrdenDelCliente(idOrden, emailUsuario);
        return construirRespuesta(orden, ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra()));
    }

    @Transactional(readOnly = true)
    public Page<OrdenCompraResponse> listarOrdenes(String emailUsuario, Pageable pageable) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);
        return ordenCompraRepository.findByCliente_IdClienteOrderByFechaCreacionDesc(cliente.getIdCliente(), pageable)
                .map(orden -> construirRespuesta(orden, ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra())));
    }

    public OrdenCompraResponse simularPago(Long idOrden, SimulacionPagoRequest request, String emailUsuario) {
        if (!pasarelaPago.esModoSimulado()) {
            throw new ApiException("El simulador de pagos está deshabilitado", HttpStatus.FORBIDDEN);
        }

        OrdenCompra orden = resolverOrdenDelCliente(idOrden, emailUsuario);
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            throw new ApiException("Esta orden ya no está pendiente de pago", HttpStatus.CONFLICT);
        }

        transaccionPagoRepository.save(TransaccionPago.builder()
                .ordenCompra(orden)
                .estado("APROBADO".equals(request.resultado()) ? EstadoTransaccionPago.APROBADA : EstadoTransaccionPago.DECLINADA)
                .origen("SIMULADOR")
                .fechaCreacion(LocalDateTime.now())
                .build());

        if ("APROBADO".equals(request.resultado())) {
            aprobarOrden(orden);
        } else {
            rechazarOrden(orden);
        }

        return construirRespuesta(orden, ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra()));
    }

    public void procesarEventoWompi(String rawBody) {
        if (!pasarelaPago.eventsSecretConfigurado()) {
            log.warn("Webhook de Wompi recibido pero no hay events-secret configurado — se descarta");
            throw new ApiException("Webhook no configurado", HttpStatus.BAD_REQUEST);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new ApiException("Cuerpo de webhook inválido", HttpStatus.BAD_REQUEST);
        }

        JsonNode dataNode = root.path("data");
        JsonNode signatureNode = root.path("signature");
        String timestamp = root.path("timestamp").asText("");
        String checksumEsperado = signatureNode.path("checksum").asText(null);

        StringBuilder cadena = new StringBuilder();
        for (JsonNode propiedad : signatureNode.path("properties")) {
            cadena.append(valorPropiedad(dataNode, propiedad.asText()));
        }
        cadena.append(timestamp);

        if (!pasarelaPago.validarFirmaEvento(cadena.toString(), checksumEsperado)) {
            log.warn("Webhook de Wompi con firma inválida — se rechaza");
            throw new ApiException("Firma inválida", HttpStatus.UNAUTHORIZED);
        }

        JsonNode transaccion = dataNode.path("transaction");
        String idTransaccionWompi = transaccion.path("id").asText(null);
        String referencia = transaccion.path("reference").asText(null);
        String estadoWompi = transaccion.path("status").asText(null);

        if (idTransaccionWompi == null || referencia == null) {
            log.warn("Webhook de Wompi sin id/referencia de transacción — se ignora");
            return;
        }

        if (transaccionPagoRepository.existsByIdTransaccionWompi(idTransaccionWompi)) {
            log.info("Evento de Wompi duplicado ignorado: idTransaccion={}", idTransaccionWompi);
            return;
        }

        OrdenCompra orden = ordenCompraRepository.findByReferencia(referencia).orElse(null);
        if (orden == null) {
            log.error("Webhook de Wompi referencia orden inexistente: {}", referencia);
            return;
        }

        EstadoTransaccionPago estadoTransaccion = switch (estadoWompi == null ? "" : estadoWompi) {
            case "APPROVED" -> EstadoTransaccionPago.APROBADA;
            case "DECLINED", "VOIDED", "ERROR" -> EstadoTransaccionPago.DECLINADA;
            default -> EstadoTransaccionPago.PENDIENTE;
        };

        transaccionPagoRepository.save(TransaccionPago.builder()
                .ordenCompra(orden)
                .estado(estadoTransaccion)
                .idTransaccionWompi(idTransaccionWompi)
                .origen("WOMPI_WEBHOOK")
                .payloadRaw(rawBody)
                .fechaCreacion(LocalDateTime.now())
                .build());

        orden.setIdTransaccionPasarela(idTransaccionWompi);
        orden.setMetodoPago(transaccion.path("payment_method_type").asText(null));

        if (estadoTransaccion == EstadoTransaccionPago.APROBADA) {
            aprobarOrden(orden);
        } else if (estadoTransaccion == EstadoTransaccionPago.DECLINADA) {
            rechazarOrden(orden);
        }
    }

    /** Idempotente: si la orden ya no está PENDIENTE, no vuelve a acreditar saldo. */
    private void aprobarOrden(OrdenCompra orden) {
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            log.info("Orden {} ya estaba en estado {}, se ignora aprobación duplicada", orden.getIdOrdenCompra(), orden.getEstado());
            return;
        }
        orden.setEstado(EstadoOrdenCompra.APROBADA);
        orden.setFechaActualizacion(LocalDateTime.now());
        ordenCompraRepository.save(orden);

        List<OrdenCompraItem> items = ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra());
        for (OrdenCompraItem item : items) {
            saldoServicioClienteService.acreditar(orden.getCliente(), item.getProceso(), item.getCantidad());
        }

        log.info("Orden aprobada: id={}, cliente={}, saldo acreditado", orden.getIdOrdenCompra(), orden.getCliente().getIdCliente());
    }

    private void rechazarOrden(OrdenCompra orden) {
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            log.info("Orden {} ya estaba en estado {}, se ignora rechazo duplicado", orden.getIdOrdenCompra(), orden.getEstado());
            return;
        }
        orden.setEstado(EstadoOrdenCompra.RECHAZADA);
        orden.setFechaActualizacion(LocalDateTime.now());
        ordenCompraRepository.save(orden);
        log.info("Orden rechazada: id={}", orden.getIdOrdenCompra());
    }

    private String valorPropiedad(JsonNode dataNode, String path) {
        JsonNode actual = dataNode;
        for (String segmento : path.split("\\.")) {
            actual = actual.path(segmento);
        }
        return actual.asText("");
    }

    /** Precio por unidad según el tramo de volumen aplicable (el de mayor cantidadMinima que la cantidad alcance); si ninguno aplica, usa el precio base del proceso. */
    private BigDecimal resolverValorUnitario(Proceso proceso, Integer cantidad) {
        return tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaDesc(proceso.getIdProceso()).stream()
                .filter(t -> cantidad >= t.getCantidadMinima())
                .map(TramoPrecioProceso::getValorUnitario)
                .findFirst()
                .orElse(proceso.getValor());
    }

    private Usuario resolverUsuario(String emailUsuario) {
        return usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private Cliente resolverCliente(Usuario usuario) {
        if (usuario.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        return clienteRepository.findById(usuario.getIdCliente())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
    }

    private OrdenCompra resolverOrdenDelCliente(Long idOrden, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);
        return ordenCompraRepository.findByIdOrdenCompraAndCliente_IdCliente(idOrden, cliente.getIdCliente())
                .orElseThrow(() -> new ApiException("Orden no encontrada", HttpStatus.NOT_FOUND));
    }

    private OrdenCompraResponse construirRespuesta(OrdenCompra orden, List<OrdenCompraItem> items) {
        List<ItemOrdenResponse> itemsResp = items.stream()
                .map(i -> new ItemOrdenResponse(
                        i.getProceso().getIdProceso(),
                        i.getProceso().getNombreProceso(),
                        i.getCantidad(),
                        i.getValorUnitario(),
                        i.getSubtotal()))
                .toList();

        return new OrdenCompraResponse(
                orden.getIdOrdenCompra(),
                orden.getReferencia(),
                orden.getMontoTotal(),
                orden.getEstado().name(),
                orden.getModoSimulado(),
                orden.getUrlCheckout(),
                itemsResp,
                orden.getFechaCreacion());
    }
}
