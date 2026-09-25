package com.polygraph.erp.modules.pagos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.dto.*;
import com.polygraph.erp.modules.pagos.entity.ComprobanteCompra;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.TransaccionPago;
import com.polygraph.erp.modules.pagos.repository.ComprobanteCompraRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.repository.TransaccionPagoRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.entity.Descuento;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.service.DescuentoService;
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
    private final ComprobanteCompraRepository comprobanteCompraRepository;
    private final ProcesoRepository procesoRepository;
    private final TramoPrecioProcesoRepository tramoRepository;
    private final ClienteRepository clienteRepository;
    private final CiudadRepository ciudadRepository;
    private final UsuarioRepository usuarioRepository;
    private final WompiPasarelaPago pasarelaPago;
    private final SaldoServicioClienteService saldoServicioClienteService;
    private final DescuentoService descuentoService;
    private final RecargoPendienteService recargoPendienteService;
    private final NotificacionService notificacionService;

    public OrdenCompraResponse crearOrden(CrearOrdenRequest request, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);

        if (cliente.getTipoCliente() != TipoCliente.PREPAGO) {
            throw new ApiException(
                    "Tu cuenta opera con crédito pospago. Usa 'Nueva Solicitud' para solicitar servicios; no necesitas comprarlos por adelantado.",
                    HttpStatus.BAD_REQUEST);
        }

        List<ItemCarritoRequest> itemsReq = request.items() != null ? request.items() : List.of();
        if (itemsReq.isEmpty()) {
            throw new ApiException("El carrito no puede estar vacío", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime ahora = LocalDateTime.now();
        String referencia = "PYG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

        Descuento cupon = descuentoService.validarCupon(request.codigoCupon(), cliente.getIdCliente());

        OrdenCompra orden = OrdenCompra.builder()
                .cliente(cliente)
                .usuario(usuario)
                .referencia(referencia)
                .montoTotal(BigDecimal.ZERO)
                .estado(EstadoOrdenCompra.PENDIENTE)
                .modoSimulado(pasarelaPago.esModoSimulado())
                .descuentoCupon(cupon)
                .fechaCreacion(ahora)
                .fechaActualizacion(ahora)
                .build();
        orden = ordenCompraRepository.save(orden);

        BigDecimal montoTotal = BigDecimal.ZERO;
        List<OrdenCompraItem> items = new java.util.ArrayList<>();

        for (ItemCarritoRequest itemReq : itemsReq) {
            Proceso proceso = procesoRepository.findById(itemReq.idProceso())
                    .orElseThrow(() -> new ApiException("Proceso de catálogo no encontrado: " + itemReq.idProceso(), HttpStatus.BAD_REQUEST));

            if (!Boolean.TRUE.equals(proceso.getActivo())) {
                throw new ApiException("El proceso '" + proceso.getNombreProceso() + "' no está disponible", HttpStatus.BAD_REQUEST);
            }
            if (proceso.getValor() == null) {
                throw new ApiException("El proceso '" + proceso.getNombreProceso() + "' no tiene un precio definido para compra en línea", HttpStatus.BAD_REQUEST);
            }

            BigDecimal valorOriginal = resolverValorUnitario(proceso, itemReq.cantidad());
            DescuentoService.MejorPrecioResult mejorPrecio = descuentoService.mejorPrecioConDescuento(proceso, valorOriginal, cliente.getIdCliente(), cupon);
            BigDecimal valorUnitario = mejorPrecio.precio();
            BigDecimal subtotal = valorUnitario.multiply(BigDecimal.valueOf(itemReq.cantidad()));
            montoTotal = montoTotal.add(subtotal);

            items.add(OrdenCompraItem.builder()
                    .ordenCompra(orden)
                    .proceso(proceso)
                    .cantidad(itemReq.cantidad())
                    .valorOriginal(valorOriginal)
                    .valorUnitario(valorUnitario)
                    .subtotal(subtotal)
                    .nombreDescuentoAplicado(mejorPrecio.descuento() != null ? mejorPrecio.descuento().getNombre() : null)
                    .build());
        }

        ordenCompraItemRepository.saveAll(items);

        // Cualquier recargo por ciudad pendiente de solicitudes anteriores se cobra ahora, junto con esta compra.
        BigDecimal recargosPendientes = recargoPendienteService.totalPendiente(cliente.getIdCliente());
        montoTotal = montoTotal.add(recargosPendientes);

        orden.setMontoTotal(montoTotal);
        orden.setMontoRecargosPendientes(recargosPendientes);
        orden = ordenCompraRepository.save(orden);

        log.info("Orden de compra creada: id={}, cliente={}, monto={}", orden.getIdOrdenCompra(), cliente.getIdCliente(), montoTotal);
        return construirRespuesta(orden, items);
    }

    /** Recargos por ciudad acumulados de solicitudes anteriores, todavía sin cobrar. */
    @Transactional(readOnly = true)
    public RecargosPendientesResponse obtenerRecargosPendientes(String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);
        return recargoPendienteService.obtenerPendientes(cliente.getIdCliente());
    }

    /**
     * Valida un código de cupón contra el carrito actual SIN crear ninguna orden — usado por el
     * modal de checkout para avisar de una vez si el código no existe, ya lo usó el cliente, no le
     * pertenece, o no aplica a ninguno de los servicios que tiene en el carrito.
     */
    @Transactional(readOnly = true)
    public ValidarCuponResponse validarCupon(CrearOrdenRequest request, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);

        if (request.codigoCupon() == null || request.codigoCupon().isBlank()) {
            throw new ApiException("Ingresa un código de descuento", HttpStatus.BAD_REQUEST);
        }
        List<ItemCarritoRequest> itemsReq = request.items() != null ? request.items() : List.of();
        if (itemsReq.isEmpty()) {
            throw new ApiException("El carrito está vacío", HttpStatus.BAD_REQUEST);
        }

        Descuento cupon = descuentoService.validarCupon(request.codigoCupon(), cliente.getIdCliente());

        BigDecimal totalSinCupon = BigDecimal.ZERO;
        BigDecimal totalConCupon = BigDecimal.ZERO;
        for (ItemCarritoRequest itemReq : itemsReq) {
            Proceso proceso = procesoRepository.findById(itemReq.idProceso())
                    .orElseThrow(() -> new ApiException("Proceso de catálogo no encontrado: " + itemReq.idProceso(), HttpStatus.BAD_REQUEST));
            BigDecimal valorBase = resolverValorUnitario(proceso, itemReq.cantidad());
            BigDecimal cantidad = BigDecimal.valueOf(itemReq.cantidad());
            totalSinCupon = totalSinCupon.add(descuentoService.aplicarMejorPrecio(proceso, valorBase, cliente.getIdCliente(), null).multiply(cantidad));
            totalConCupon = totalConCupon.add(descuentoService.aplicarMejorPrecio(proceso, valorBase, cliente.getIdCliente(), cupon).multiply(cantidad));
        }

        // Los recargos pendientes se cobran junto con esta compra sin importar el cupón — se suman
        // por igual a ambos lados para que la comparación del descuento siga siendo justa.
        BigDecimal recargosPendientes = recargoPendienteService.totalPendiente(cliente.getIdCliente());
        totalSinCupon = totalSinCupon.add(recargosPendientes);
        totalConCupon = totalConCupon.add(recargosPendientes);

        BigDecimal montoDescuento = totalSinCupon.subtract(totalConCupon);
        if (montoDescuento.signum() <= 0) {
            throw new ApiException("Este código no aplica a los servicios de tu carrito", HttpStatus.BAD_REQUEST);
        }

        return new ValidarCuponResponse("Código válido", totalSinCupon, totalConCupon, montoDescuento);
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

    // ---------- Comprobantes de compra (no son factura electrónica DIAN — ver ComprobanteCompra) ----------

    @Transactional(readOnly = true)
    public List<ComprobanteResponse> listarComprobantes(String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);
        return comprobanteCompraRepository.findByOrdenCompra_Cliente_IdClienteOrderByFechaEmisionDesc(cliente.getIdCliente()).stream()
                .map(this::construirComprobanteRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComprobanteResponse obtenerComprobante(Integer idComprobante, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        Cliente cliente = resolverCliente(usuario);
        ComprobanteCompra comprobante = comprobanteCompraRepository.findById(idComprobante)
                .orElseThrow(() -> new ApiException("Comprobante no encontrado", HttpStatus.NOT_FOUND));
        if (!comprobante.getOrdenCompra().getCliente().getIdCliente().equals(cliente.getIdCliente())) {
            throw new ApiException("Comprobante no encontrado", HttpStatus.NOT_FOUND);
        }
        return construirComprobanteRespuesta(comprobante);
    }

    /** Sin validar dueño — para el admin, que puede ver el comprobante de cualquier cliente. */
    @Transactional(readOnly = true)
    public ComprobanteResponse obtenerComprobanteAdmin(Integer idComprobante) {
        ComprobanteCompra comprobante = comprobanteCompraRepository.findById(idComprobante)
                .orElseThrow(() -> new ApiException("Comprobante no encontrado", HttpStatus.NOT_FOUND));
        return construirComprobanteRespuesta(comprobante);
    }

    private ComprobanteResponse construirComprobanteRespuesta(ComprobanteCompra comprobante) {
        OrdenCompra orden = comprobante.getOrdenCompra();
        List<OrdenCompraItem> itemsEntidad = ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra());
        List<ItemOrdenResponse> items = itemsEntidad.stream()
                .map(i -> new ItemOrdenResponse(
                        i.getProceso().getIdProceso(),
                        i.getProceso().getNombreProceso(),
                        i.getCantidad(),
                        i.getValorOriginal(),
                        i.getValorUnitario(),
                        i.getSubtotal(),
                        i.getNombreDescuentoAplicado()))
                .toList();

        Cliente cliente = orden.getCliente();
        Ciudad ciudadCliente = cliente.getIdCiudad() != null ? ciudadRepository.findById(cliente.getIdCiudad()).orElse(null) : null;
        String nitCliente = cliente.getNit() != null
                ? cliente.getNit() + (cliente.getDv() != null ? "-" + cliente.getDv() : "")
                : null;

        return new ComprobanteResponse(
                comprobante.getIdComprobante(),
                "CP-" + String.format("%06d", comprobante.getIdComprobante()),
                comprobante.getFechaEmision(),
                orden.getIdOrdenCompra(),
                orden.getReferencia(),
                orden.getMontoTotal(),
                items,
                orden.getNombrePagador(),
                orden.getTelefonoPagador(),
                orden.getEmailPagador(),
                true,
                orden.getDescuentoCupon() != null ? orden.getDescuentoCupon().getCodigo() : null,
                orden.getDescuentoCupon() != null ? orden.getDescuentoCupon().getNombre() : null,
                montoDescuentoTotal(itemsEntidad),
                orden.getMontoRecargosPendientes(),
                nombreClienteDisplay(cliente),
                nitCliente,
                cliente.getTelefono(),
                cliente.getDireccion(),
                ciudadCliente != null ? ciudadCliente.getNombreCiudad() : null,
                cliente.getEmailPrincipal());
    }

    /** Suma, a través de todos los ítems, cuánto se redujo el precio (base vs. lo que se hubiera cobrado sin descuento). */
    private BigDecimal montoDescuentoTotal(List<OrdenCompraItem> items) {
        return items.stream()
                .map(i -> i.getValorOriginal().subtract(i.getValorUnitario()).multiply(BigDecimal.valueOf(i.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    /**
     * Aplica un evento de Wompi YA VALIDADO (checksum verificado por el caller — ver
     * {@link PagoWompiWebhookService}, único punto de entrada de webhooks de Wompi en todo el
     * sistema) a la orden de compra que referencia. Se usa como fallback cuando la referencia no
     * es de un pago del flujo genérico (PagoWompi) — así ambos flujos comparten un solo webhook
     * sin duplicar el parseo ni la verificación de firma.
     *
     * @return false si la referencia no corresponde a ninguna orden de compra (ninguno de los dos flujos la reconoce).
     */
    @Transactional
    public boolean aplicarEventoTransaccion(JsonNode transaccion, String idTransaccionWompi, String referencia, String estadoWompi, String rawBody) {
        if (transaccionPagoRepository.existsByIdTransaccionWompi(idTransaccionWompi)) {
            log.info("Evento de Wompi duplicado ignorado: idTransaccion={}", idTransaccionWompi);
            return true;
        }

        OrdenCompra orden = ordenCompraRepository.findByReferencia(referencia).orElse(null);
        if (orden == null) {
            return false;
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
        orden.setEmailPagador(transaccion.path("customer_email").asText(null));
        orden.setNombrePagador(transaccion.path("customer_data").path("full_name").asText(null));
        orden.setTelefonoPagador(transaccion.path("customer_data").path("phone_number").asText(null));
        orden.setNumeroAprobacion(transaccion.path("payment_method").path("extra").path("external_identifier").asText(null));

        if (estadoTransaccion == EstadoTransaccionPago.APROBADA) {
            aprobarOrden(orden);
        } else if (estadoTransaccion == EstadoTransaccionPago.DECLINADA) {
            rechazarOrden(orden);
        }
        return true;
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

        if (orden.getDescuentoCupon() != null) {
            descuentoService.registrarUso(orden.getDescuentoCupon(), orden.getCliente(), orden);
        }

        recargoPendienteService.saldarPendientes(orden.getCliente(), orden);

        log.info("Orden aprobada: id={}, cliente={}, saldo acreditado", orden.getIdOrdenCompra(), orden.getCliente().getIdCliente());
        // Antes el cliente nunca se enteraba de que su compra se acreditó — tenía que entrar a
        // revisar su bolsa de servicios por su cuenta.
        notificacionService.crearParaCliente(orden.getCliente().getIdCliente(), "PAGO_APROBADO", "Compra acreditada",
                "Tu compra " + orden.getReferencia() + " fue aprobada — ya tienes el saldo disponible en tu bolsa.");
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
        notificacionService.crearParaCliente(orden.getCliente().getIdCliente(), "PAGO_RECHAZADO", "Compra rechazada",
                "Tu compra " + orden.getReferencia() + " no pudo procesarse — intenta de nuevo.");
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
                        i.getValorOriginal(),
                        i.getValorUnitario(),
                        i.getSubtotal(),
                        i.getNombreDescuentoAplicado()))
                .toList();

        Integer idComprobante = comprobanteCompraRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra())
                .map(ComprobanteCompra::getIdComprobante)
                .orElse(null);
        String numeroComprobante = idComprobante != null ? "CP-" + String.format("%06d", idComprobante) : null;

        return new OrdenCompraResponse(
                orden.getIdOrdenCompra(),
                orden.getReferencia(),
                orden.getMontoTotal(),
                orden.getEstado().name(),
                orden.getModoSimulado(),
                orden.getUrlCheckout(),
                itemsResp,
                orden.getFechaCreacion(),
                orden.getMetodoPago(),
                orden.getNumeroAprobacion(),
                orden.getIdTransaccionPasarela(),
                orden.getNombrePagador(),
                orden.getTelefonoPagador(),
                orden.getEmailPagador(),
                orden.getCliente().getIdCliente(),
                nombreClienteDisplay(orden.getCliente()),
                idComprobante,
                numeroComprobante,
                orden.getDescuentoCupon() != null ? orden.getDescuentoCupon().getCodigo() : null,
                orden.getDescuentoCupon() != null ? orden.getDescuentoCupon().getNombre() : null,
                montoDescuentoTotal(items),
                orden.getMontoRecargosPendientes());
    }

    private String nombreClienteDisplay(Cliente c) {
        if (c.getTipoPersona() != null && "NATURAL".equals(c.getTipoPersona().name())) {
            String nombre = (c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "");
            return nombre.isBlank() ? c.getEmailPrincipal() : nombre.trim();
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }

    // ---------- Administración (ADMIN_POLYGRAPH / GESTOR) ----------

    @Transactional(readOnly = true)
    public Page<OrdenCompraResponse> listarTodasLasOrdenes(String estado, Integer idCliente, Pageable pageable) {
        boolean conEstado = estado != null && !estado.isBlank();
        EstadoOrdenCompra estadoEnum = conEstado ? EstadoOrdenCompra.valueOf(estado) : null;

        Page<OrdenCompra> ordenes;
        if (idCliente != null && conEstado) {
            ordenes = ordenCompraRepository.findByCliente_IdClienteAndEstadoOrderByFechaCreacionDesc(idCliente, estadoEnum, pageable);
        } else if (idCliente != null) {
            ordenes = ordenCompraRepository.findByCliente_IdClienteOrderByFechaCreacionDesc(idCliente, pageable);
        } else if (conEstado) {
            ordenes = ordenCompraRepository.findByEstadoOrderByFechaCreacionDesc(estadoEnum, pageable);
        } else {
            ordenes = ordenCompraRepository.findAllByOrderByFechaCreacionDesc(pageable);
        }
        return ordenes.map(orden -> construirRespuesta(orden, ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra())));
    }

    /** Órdenes prepago aprobadas (ya cobradas) sin comprobante generado todavía — panel "sin
     *  facturar" de Facturacion.jsx, cruzando todos los clientes prepago a la vez. */
    @Transactional(readOnly = true)
    public Page<OrdenCompraResponse> listarOrdenesSinFacturar(Pageable pageable) {
        return ordenCompraRepository.findAprobadasSinComprobante(pageable)
                .map(orden -> construirRespuesta(orden, ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra())));
    }

    /** Detalle de una orden para el modal "Ver detalle" de la tabla unificada de
     *  Facturacion.jsx — a diferencia de obtenerOrden(id, email), este no valida dueño porque lo
     *  llama un admin, no el cliente. */
    @Transactional(readOnly = true)
    public OrdenCompraResponse obtenerOrdenAdmin(Long idOrden) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrden)
                .orElseThrow(() -> new ApiException("Orden no encontrada", HttpStatus.NOT_FOUND));
        return construirRespuesta(orden, ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(orden.getIdOrdenCompra()));
    }

    /** Sumas prepago para la barra "Pagado / Pendiente / Vencido" de Facturacion.jsx. */
    @Transactional(readOnly = true)
    public ResumenPagosOrdenesResponse resumenPagos() {
        return new ResumenPagosOrdenesResponse(
                ordenCompraRepository.sumMontoTotalByEstado(EstadoOrdenCompra.APROBADA),
                ordenCompraRepository.sumMontoTotalByEstado(EstadoOrdenCompra.PENDIENTE));
    }

    /** Solo se puede borrar una orden mientras siga PENDIENTE — para limpiar pruebas o intentos
     * abandonados, nunca una que ya tuvo movimiento de pago real (aprobada/rechazada). */
    @Transactional
    public void eliminarOrdenPendiente(Long idOrden) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrden)
                .orElseThrow(() -> new ApiException("Orden no encontrada", HttpStatus.NOT_FOUND));
        if (orden.getEstado() != EstadoOrdenCompra.PENDIENTE) {
            throw new ApiException("Solo se pueden eliminar órdenes en estado Pendiente", HttpStatus.CONFLICT);
        }
        ordenCompraItemRepository.deleteAll(ordenCompraItemRepository.findByOrdenCompra_IdOrdenCompra(idOrden));
        ordenCompraRepository.delete(orden);
        log.info("Orden de compra pendiente eliminada: id={}, referencia={}", idOrden, orden.getReferencia());
    }

    /** Genera el comprobante de una orden aprobada — a mano, por un admin. No es automático. */
    public ComprobanteResponse generarComprobante(Long idOrden) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrden)
                .orElseThrow(() -> new ApiException("Orden no encontrada", HttpStatus.NOT_FOUND));

        if (orden.getEstado() != EstadoOrdenCompra.APROBADA) {
            throw new ApiException("Solo se puede generar comprobante de una orden aprobada", HttpStatus.BAD_REQUEST);
        }
        if (comprobanteCompraRepository.existsByOrdenCompra_IdOrdenCompra(idOrden)) {
            throw new ApiException("Esta orden ya tiene un comprobante generado", HttpStatus.CONFLICT);
        }

        ComprobanteCompra comprobante = comprobanteCompraRepository.save(ComprobanteCompra.builder()
                .ordenCompra(orden)
                .fechaEmision(LocalDateTime.now())
                .build());

        log.info("Comprobante generado manualmente: orden={}, comprobante={}", idOrden, comprobante.getIdComprobante());
        return construirComprobanteRespuesta(comprobante);
    }
}
