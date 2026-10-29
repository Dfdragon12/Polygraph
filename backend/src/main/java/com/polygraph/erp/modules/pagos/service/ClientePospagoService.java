package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.pagos.dto.EstadoCuentaPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.FacturaPospagoDetalleResponse;
import com.polygraph.erp.modules.pagos.dto.FacturaPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.IntentoPagoResponse;
import com.polygraph.erp.modules.pagos.dto.MovimientoCreditoPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.PendienteFacturarPospagoResponse;
import com.polygraph.erp.modules.pagos.dto.ResumenFacturasPospagoResponse;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoConceptoPago;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Crédito pospago tipo tarjeta: cada solicitud consume {@code creditoDisponible} (validado y
 * descontado aquí, llamado desde SolicitudService igual que SaldoServicioClienteService para
 * prepago); el admin junta lo consumido sin facturar en una {@link FacturaPospago} (corte manual,
 * como el comprobante de una orden de compra); el cliente la paga por el pipeline genérico de
 * Wompi (PagoService/PagoAplicacionService) y el cupo se restituye.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientePospagoService {

    private final ClientePospagoRepository clientePospagoRepository;
    private final MovimientoCreditoPospagoRepository movimientoRepository;
    private final FacturaPospagoRepository facturaPospagoRepository;
    private final PagoWompiRepository pagoWompiRepository;
    private final CiudadRepository ciudadRepository;
    private final RecargoPendienteService recargoPendienteService;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;

    // ---------- Resolución desde el usuario autenticado (portal cliente) ----------

    @Transactional(readOnly = true)
    public EstadoCuentaPospagoResponse obtenerEstadoCuenta(String emailUsuario) {
        return obtenerEstadoCuenta(resolverClienteDeUsuario(emailUsuario));
    }

    @Transactional(readOnly = true)
    public List<FacturaPospagoResponse> listarFacturas(String emailUsuario) {
        return listarFacturas(resolverClienteDeUsuario(emailUsuario));
    }

    @Transactional(readOnly = true)
    public FacturaPospagoDetalleResponse obtenerFacturaConDetalle(Long idFactura, String emailUsuario) {
        Integer idCliente = resolverClienteDeUsuario(emailUsuario);
        if (!resolverClienteDeFactura(idFactura).equals(idCliente)) {
            throw new ApiException("Factura no encontrada", HttpStatus.NOT_FOUND);
        }
        return obtenerFacturaConDetalle(idFactura);
    }

    private Integer resolverClienteDeUsuario(String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        return usuario.getIdCliente();
    }

    private record CostoServicio(BigDecimal montoBase, BigDecimal montoRecargo, BigDecimal montoTotal) {}

    @Transactional(readOnly = true)
    public void validarCreditoDisponible(Cliente cliente, Proceso proceso, Integer idCiudad) {
        ClientePospago pospago = obtenerPospago(cliente.getIdCliente());
        CostoServicio costo = calcularCosto(proceso, idCiudad);

        BigDecimal disponible = pospago.getCreditoDisponible() != null ? pospago.getCreditoDisponible() : BigDecimal.ZERO;
        if (disponible.compareTo(costo.montoTotal()) < 0) {
            throw new ApiException(
                    "No tienes cupo disponible para solicitar '" + proceso.getNombreProceso() + "'. "
                    + "Contacta a tu gestor para ampliar tu límite de crédito.",
                    HttpStatus.PAYMENT_REQUIRED);
        }
    }

    @Transactional
    public void descontarCredito(Cliente cliente, Servicio servicio, Proceso proceso, Integer idCiudad) {
        ClientePospago pospago = obtenerPospago(cliente.getIdCliente());
        CostoServicio costo = calcularCosto(proceso, idCiudad);

        BigDecimal disponible = pospago.getCreditoDisponible() != null ? pospago.getCreditoDisponible() : BigDecimal.ZERO;
        pospago.setCreditoDisponible(disponible.subtract(costo.montoTotal()));
        clientePospagoRepository.save(pospago);

        movimientoRepository.save(MovimientoCreditoPospago.builder()
                .cliente(cliente)
                .servicio(servicio)
                .montoBase(costo.montoBase())
                .montoRecargoCiudad(costo.montoRecargo())
                .montoTotal(costo.montoTotal())
                .fechaGenerado(LocalDateTime.now())
                .build());
        log.info("Crédito pospago descontado: cliente={}, servicio={}, monto={}",
                cliente.getIdCliente(), servicio.getIdServicio(), costo.montoTotal());
    }

    private CostoServicio calcularCosto(Proceso proceso, Integer idCiudad) {
        if (proceso.getValor() == null) {
            throw new ApiException(
                    "El proceso '" + proceso.getNombreProceso() + "' no tiene un precio definido para clientes pospago",
                    HttpStatus.BAD_REQUEST);
        }
        BigDecimal montoBase = proceso.getValor();
        Ciudad ciudad = idCiudad != null ? ciudadRepository.findById(idCiudad).orElse(null) : null;
        BigDecimal montoRecargo = recargoPendienteService.calcular(proceso, ciudad);
        return new CostoServicio(montoBase, montoRecargo, montoBase.add(montoRecargo));
    }

    private ClientePospago obtenerPospago(Integer idCliente) {
        return clientePospagoRepository.findByIdCliente(idCliente)
                .orElseThrow(() -> new ApiException(
                        "Tu cuenta pospago todavía no tiene un cupo de crédito asignado. Contacta a tu gestor.",
                        HttpStatus.FORBIDDEN));
    }

    // ---------- Estado de cuenta ----------

    @Transactional(readOnly = true)
    public EstadoCuentaPospagoResponse obtenerEstadoCuenta(Integer idCliente) {
        ClientePospago pospago = obtenerPospago(idCliente);

        BigDecimal consumidoSinFacturar = movimientoRepository
                .findByCliente_IdClienteAndFacturaIsNullAndAnuladoEnIsNullOrderByFechaGeneradoAsc(idCliente).stream()
                .map(MovimientoCreditoPospago::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal montoFacturasPendientes = facturaPospagoRepository.findByCliente_IdClienteOrderByFechaGeneracionDesc(idCliente).stream()
                .filter(f -> f.getEstado() == EstadoFacturaPospago.PENDIENTE || f.getEstado() == EstadoFacturaPospago.VENCIDA)
                .map(FacturaPospago::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new EstadoCuentaPospagoResponse(
                idCliente,
                pospago.getLimiteCredito(),
                pospago.getCreditoDisponible(),
                consumidoSinFacturar,
                montoFacturasPendientes,
                pospago.getEstadoMora() != null ? pospago.getEstadoMora().name() : null);
    }

    @Transactional(readOnly = true)
    public List<MovimientoCreditoPospagoResponse> listarMovimientosPendientesDeFacturar(Integer idCliente) {
        return movimientoRepository.findByCliente_IdClienteAndFacturaIsNullAndAnuladoEnIsNullOrderByFechaGeneradoAsc(idCliente).stream()
                .map(this::mapearMovimiento)
                .toList();
    }

    /** Consumo pospago sin facturar de TODOS los clientes a la vez, agrupado por cliente — panel
     *  "Pospago: servicios pendientes de facturar" de Facturacion.jsx. Ordenado por monto
     *  descendente para que el admin priorice los cortes más grandes. */
    @Transactional(readOnly = true)
    public List<PendienteFacturarPospagoResponse> listarPendientesGlobal() {
        Map<Integer, List<MovimientoCreditoPospago>> porCliente = movimientoRepository
                .findByFacturaIsNullAndAnuladoEnIsNullOrderByFechaGeneradoAsc().stream()
                .collect(Collectors.groupingBy(m -> m.getCliente().getIdCliente(), LinkedHashMap::new, Collectors.toList()));

        return porCliente.values().stream()
                .map(movimientos -> {
                    Cliente cliente = movimientos.get(0).getCliente();
                    BigDecimal total = movimientos.stream().map(MovimientoCreditoPospago::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new PendienteFacturarPospagoResponse(
                            cliente.getIdCliente(), cliente.getNit(), nombreCliente(cliente), movimientos.size(), total,
                            movimientos.get(0).getFechaGenerado());
                })
                .sorted(Comparator.comparing(PendienteFacturarPospagoResponse::montoTotal).reversed())
                .toList();
    }

    /** Sumas pospago para la barra "Pagado / Pendiente / Vencido" de Facturacion.jsx. */
    @Transactional(readOnly = true)
    public ResumenFacturasPospagoResponse resumenFacturas() {
        return new ResumenFacturasPospagoResponse(
                facturaPospagoRepository.sumMontoTotalByEstado(EstadoFacturaPospago.PAGADA),
                facturaPospagoRepository.sumMontoTotalByEstado(EstadoFacturaPospago.PENDIENTE),
                facturaPospagoRepository.sumMontoTotalByEstado(EstadoFacturaPospago.VENCIDA));
    }

    // ---------- Facturas ----------

    @Transactional
    public FacturaPospagoResponse generarFactura(Integer idCliente, LocalDate fechaVencimiento, List<Long> idsMovimientos, String emailAdmin) {
        Usuario admin = usuarioRepository.findByEmail(emailAdmin)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return generarFactura(idCliente, fechaVencimiento, idsMovimientos, admin);
    }

    /** Si idsMovimientos viene vacío o null, se factura TODO lo pendiente del cliente (comportamiento
     *  de siempre). Si viene con ids puntuales, se factura solo esos — el resto del consumo queda
     *  pendiente para un corte posterior, para que el admin pueda agrupar la facturación pospago
     *  como necesite (ej. por mes, por proceso) en vez de un solo corte con todo junto. */
    @Transactional
    public FacturaPospagoResponse generarFactura(Integer idCliente, LocalDate fechaVencimiento, List<Long> idsMovimientos, Usuario admin) {
        Cliente cliente = obtenerPospago(idCliente).getCliente();
        List<MovimientoCreditoPospago> todosPendientes = movimientoRepository
                .findByCliente_IdClienteAndFacturaIsNullAndAnuladoEnIsNullOrderByFechaGeneradoAsc(idCliente);

        List<MovimientoCreditoPospago> pendientes;
        if (idsMovimientos == null || idsMovimientos.isEmpty()) {
            pendientes = todosPendientes;
        } else {
            java.util.Set<Long> idsValidos = todosPendientes.stream()
                    .map(MovimientoCreditoPospago::getIdMovimiento).collect(Collectors.toSet());
            if (!idsValidos.containsAll(idsMovimientos)) {
                throw new ApiException(
                        "Alguno de los movimientos seleccionados ya no está pendiente de facturar o no pertenece a este cliente",
                        HttpStatus.CONFLICT);
            }
            pendientes = todosPendientes.stream().filter(m -> idsMovimientos.contains(m.getIdMovimiento())).toList();
        }

        if (pendientes.isEmpty()) {
            throw new ApiException("Este cliente no tiene consumo pendiente de facturar", HttpStatus.BAD_REQUEST);
        }

        BigDecimal montoTotal = pendientes.stream().map(MovimientoCreditoPospago::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime ahora = LocalDateTime.now();

        FacturaPospago factura = FacturaPospago.builder()
                .cliente(cliente)
                .referencia(generarReferencia())
                .fechaCorte(ahora.toLocalDate())
                .fechaVencimiento(fechaVencimiento)
                .montoTotal(montoTotal)
                .estado(EstadoFacturaPospago.PENDIENTE)
                .fechaGeneracion(ahora)
                .generadaPor(admin)
                .build();
        FacturaPospago facturaGuardada = facturaPospagoRepository.save(factura);

        for (MovimientoCreditoPospago m : pendientes) {
            m.setFactura(facturaGuardada);
        }
        movimientoRepository.saveAll(pendientes);

        log.info("Factura pospago generada: id={}, cliente={}, monto={}, movimientos={}",
                facturaGuardada.getIdFactura(), idCliente, montoTotal, pendientes.size());

        usuarioRepository.findFirstByIdClienteAndRol(idCliente, Rol.ADMIN_CLIENTE).ifPresent(usuarioCliente ->
                notificacionRepository.save(Notificacion.builder()
                        .usuario(usuarioCliente)
                        .tipo("FACTURA_POSPAGO_GENERADA")
                        .titulo("Nueva factura de crédito pospago")
                        .mensaje("Se generó tu factura " + facturaGuardada.getReferencia() + " por "
                                + montoTotal + " COP, vence el " + fechaVencimiento + ".")
                        .leida(false)
                        .fechaCreacion(ahora)
                        .referenciaTipo("FACTURA_POSPAGO")
                        .referenciaId(facturaGuardada.getIdFactura())
                        .build()));

        return mapearFactura(facturaGuardada, cliente);
    }

    @Transactional(readOnly = true)
    public List<FacturaPospagoResponse> listarFacturas(Integer idCliente) {
        return facturaPospagoRepository.findByCliente_IdClienteOrderByFechaGeneracionDesc(idCliente).stream()
                .map(f -> mapearFactura(f, f.getCliente()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<FacturaPospagoResponse> listarTodasLasFacturas(String estado, Pageable pageable) {
        Page<FacturaPospago> pagina = estado != null && !estado.isBlank()
                ? facturaPospagoRepository.findByEstadoOrderByFechaGeneracionDesc(EstadoFacturaPospago.valueOf(estado.toUpperCase()), pageable)
                : facturaPospagoRepository.findAllByOrderByFechaGeneracionDesc(pageable);
        return pagina.map(f -> mapearFactura(f, f.getCliente()));
    }

    @Transactional(readOnly = true)
    public FacturaPospagoDetalleResponse obtenerFacturaConDetalle(Long idFactura) {
        FacturaPospago factura = facturaPospagoRepository.findById(idFactura)
                .orElseThrow(() -> new ApiException("Factura no encontrada", HttpStatus.NOT_FOUND));
        List<MovimientoCreditoPospagoResponse> movimientos = movimientoRepository
                .findByFactura_IdFacturaOrderByFechaGeneradoAsc(idFactura).stream()
                .map(this::mapearMovimiento)
                .toList();
        List<IntentoPagoResponse> intentosPago = pagoWompiRepository
                .findByFacturaOConceptoIdAndTipoConceptoOrderByFechaCreacionDesc(idFactura, TipoConceptoPago.FACTURA_POSPAGO).stream()
                .map(this::mapearIntentoPago)
                .toList();
        return new FacturaPospagoDetalleResponse(mapearFactura(factura, factura.getCliente()), movimientos, intentosPago);
    }

    private IntentoPagoResponse mapearIntentoPago(PagoWompi p) {
        return new IntentoPagoResponse(p.getReferencia(), p.getEstado().name(), p.getMetodoPago(), p.getFechaCreacion(), p.getFechaActualizacion());
    }

    /** Solo se puede borrar una factura mientras siga PENDIENTE — nunca una ya pagada. Los
     * movimientos que traía se desvinculan (no se borran) y vuelven a quedar pendientes de
     * facturar, para poder incluirlos en una factura nueva más adelante. */
    @Transactional
    public void eliminarFacturaPendiente(Long idFactura) {
        FacturaPospago factura = facturaPospagoRepository.findById(idFactura)
                .orElseThrow(() -> new ApiException("Factura no encontrada", HttpStatus.NOT_FOUND));
        if (factura.getEstado() != EstadoFacturaPospago.PENDIENTE) {
            throw new ApiException("Solo se pueden eliminar facturas en estado Pendiente", HttpStatus.CONFLICT);
        }

        List<MovimientoCreditoPospago> movimientos = movimientoRepository.findByFactura_IdFacturaOrderByFechaGeneradoAsc(idFactura);
        for (MovimientoCreditoPospago m : movimientos) {
            m.setFactura(null);
        }
        movimientoRepository.saveAll(movimientos);

        facturaPospagoRepository.delete(factura);
        log.info("Factura pospago pendiente eliminada: id={}, referencia={}, movimientos liberados={}",
                idFactura, factura.getReferencia(), movimientos.size());
    }

    /** Cliente dueño de la factura, para validar acceso desde el controller. */
    @Transactional(readOnly = true)
    public Integer resolverClienteDeFactura(Long idFactura) {
        return facturaPospagoRepository.findById(idFactura)
                .map(f -> f.getCliente().getIdCliente())
                .orElseThrow(() -> new ApiException("Factura no encontrada", HttpStatus.NOT_FOUND));
    }

    /** Llamado por PagoAplicacionService cuando un PagoWompi con tipoConcepto=FACTURA_POSPAGO queda APROBADO. */
    @Transactional
    public void marcarFacturaPagada(Long idFactura) {
        FacturaPospago factura = facturaPospagoRepository.findById(idFactura).orElse(null);
        if (factura == null) {
            log.error("Pago aprobado para factura pospago inexistente: id={}", idFactura);
            return;
        }
        if (factura.getEstado() == EstadoFacturaPospago.PAGADA) {
            return; // idempotente — el webhook puede reintentar el mismo evento
        }

        factura.setEstado(EstadoFacturaPospago.PAGADA);
        factura.setFechaPago(LocalDateTime.now());
        facturaPospagoRepository.save(factura);

        ClientePospago pospago = obtenerPospago(factura.getCliente().getIdCliente());
        BigDecimal disponible = pospago.getCreditoDisponible() != null ? pospago.getCreditoDisponible() : BigDecimal.ZERO;
        pospago.setCreditoDisponible(disponible.add(factura.getMontoTotal()));
        clientePospagoRepository.save(pospago);

        log.info("Factura pospago pagada: id={}, cliente={}, monto restituido={}",
                idFactura, factura.getCliente().getIdCliente(), factura.getMontoTotal());
    }

    private MovimientoCreditoPospagoResponse mapearMovimiento(MovimientoCreditoPospago m) {
        Servicio s = m.getServicio();
        Candidato candidato = s.getCandidato();
        Ciudad ciudad = candidato != null && candidato.getIdCiudadResidencia() != null
                ? ciudadRepository.findById(candidato.getIdCiudadResidencia()).orElse(null)
                : null;
        return new MovimientoCreditoPospagoResponse(
                m.getIdMovimiento(),
                s.getIdServicio(),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                candidato != null ? (candidato.getNombres() + " " + candidato.getApellidos()) : null,
                ciudad != null ? ciudad.getNombreCiudad() : null,
                m.getMontoBase(),
                m.getMontoRecargoCiudad(),
                m.getMontoTotal(),
                m.getFechaGenerado());
    }

    private FacturaPospagoResponse mapearFactura(FacturaPospago f, Cliente cliente) {
        return new FacturaPospagoResponse(
                f.getIdFactura(),
                cliente.getIdCliente(),
                f.getReferencia(),
                f.getFechaCorte(),
                f.getFechaVencimiento(),
                f.getMontoTotal(),
                f.getEstado().name(),
                f.getFechaGeneracion(),
                f.getFechaPago(),
                nombreCliente(cliente));
    }

    private String nombreCliente(Cliente cliente) {
        if ("NATURAL".equals(cliente.getTipoPersona() != null ? cliente.getTipoPersona().name() : null)) {
            String nombre = ((cliente.getNombre() != null ? cliente.getNombre() : "") + " " + (cliente.getApellido() != null ? cliente.getApellido() : "")).trim();
            return nombre.isBlank() ? cliente.getEmailPrincipal() : nombre;
        }
        return cliente.getRazonSocial() != null ? cliente.getRazonSocial() : cliente.getNombreComercial();
    }

    private String generarReferencia() {
        String corto = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "FP-" + corto + "-" + System.currentTimeMillis();
    }
}
