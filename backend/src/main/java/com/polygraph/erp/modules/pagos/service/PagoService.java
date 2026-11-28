package com.polygraph.erp.modules.pagos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.dto.EstadoPagoResponse;
import com.polygraph.erp.modules.pagos.dto.IniciarPagoWompiRequest;
import com.polygraph.erp.modules.pagos.dto.IniciarPagoWompiResponse;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoConceptoPago;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.EnmascaradorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PagoService {

    private static final String MONEDA = "COP";

    private final PagoWompiRepository pagoWompiRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturaPospagoRepository facturaPospagoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final WompiSignatureService wompiSignatureService;
    private final WompiProperties wompiProperties;
    private final AuditoriaPagoService auditoriaPagoService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public IniciarPagoWompiResponse iniciarPago(IniciarPagoWompiRequest request, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        verificarAccesoCliente(usuario, request.clienteId());

        if (request.montoEnCentavos() == null || request.montoEnCentavos() <= 0) {
            throw new ApiException("montoEnCentavos debe ser mayor a cero", HttpStatus.BAD_REQUEST);
        }

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        // Nunca se confía en el monto que envía el frontend: se recalcula desde el concepto (orden de compra o factura pospago).
        TipoConceptoPago tipoConcepto = request.tipoConceptoOrDefault();
        long montoRecalculado = recalcularMonto(tipoConcepto, request.facturaOConceptoId(), request.clienteId());
        if (!request.montoEnCentavos().equals(montoRecalculado)) {
            log.warn("montoEnCentavos recibido ({}) no coincide con el recalculado ({}) — se usa el recalculado. referenciaOrden={}",
                    request.montoEnCentavos(), montoRecalculado, request.facturaOConceptoId());
        }

        // Reintento sobre un pago ya iniciado para este mismo concepto: se reusa su referencia (igual
        // que WompiPasarelaPago reusa la referencia de la OrdenCompra en cada reintento) en vez de
        // crear un pago nuevo — evita dejar pagos huérfanos sueltos cada vez que el cliente da clic
        // en "Pagar" de nuevo (p. ej. porque canceló el checkout anterior o le pareció que no cargaba).
        PagoWompi pagoExistente = pagoWompiRepository
                .findFirstByFacturaOConceptoIdAndTipoConceptoAndEstadoOrderByFechaCreacionDesc(
                        request.facturaOConceptoId(), tipoConcepto, EstadoPago.PENDIENTE)
                .orElse(null);

        String referencia;
        LocalDateTime ahora = LocalDateTime.now();
        if (pagoExistente != null) {
            // Se reusa el registro, no la referencia: Wompi rechaza una referencia que ya tuvo una transacción
            // («la referencia ya ha sido usada») aunque haya sido rechazada o abandonada.
            referencia = ReferenciaPago.nuevoIntento(pagoExistente.getReferencia());
            pagoExistente.setReferencia(referencia);
            pagoExistente.setTransaccionWompiId(null);
            pagoExistente.setMontoEnCentavos(montoRecalculado);
            pagoExistente.setFechaActualizacion(ahora);
            pagoWompiRepository.save(pagoExistente);
            log.info("Pago Wompi reintentado — mismo pago, referencia nueva: referencia={}, cliente={}",
                    referencia, cliente.getIdCliente());
        } else {
            referencia = generarReferencia();
            PagoWompi pago = PagoWompi.builder()
                    .referencia(referencia)
                    .montoEnCentavos(montoRecalculado)
                    .moneda(MONEDA)
                    .estado(EstadoPago.PENDIENTE)
                    .cliente(cliente)
                    .creadoPor(usuario)
                    .facturaOConceptoId(request.facturaOConceptoId())
                    .tipoConcepto(tipoConcepto)
                    .descripcion(request.descripcion())
                    .fechaCreacion(ahora)
                    .fechaActualizacion(ahora)
                    .build();
            pagoWompiRepository.save(pago);
            auditoriaPagoService.registrarCreacion(pago, usuario);
        }

        String firmaIntegridad = wompiSignatureService.generarFirmaIntegridad(referencia, montoRecalculado, MONEDA);
        String redirectUrl = construirRedirectUrl(tipoConcepto, referencia);
        String checkoutUrl = wompiSignatureService.construirUrlCheckout(referencia, montoRecalculado, MONEDA, redirectUrl);

        log.info("Pago Wompi iniciado: referencia={}, cliente={}, monto={}, generadoPor={}",
                referencia, cliente.getIdCliente(), montoRecalculado, EnmascaradorUtil.enmascararEmail(usuario.getEmail()));

        return new IniciarPagoWompiResponse(
                wompiProperties.getPublicKey(),
                referencia,
                montoRecalculado,
                MONEDA,
                firmaIntegridad,
                redirectUrl,
                checkoutUrl);
    }

    /** A dónde vuelve el navegador después de pagar — página de resultado propia según el tipo de concepto. */
    private String construirRedirectUrl(TipoConceptoPago tipoConcepto, String referencia) {
        return switch (tipoConcepto) {
            case FACTURA_POSPAGO -> frontendUrl + "/cliente/pospago/resultado/" + referencia;
            case ORDEN_COMPRA -> wompiProperties.getRedirectUrl();
        };
    }

    @Transactional(readOnly = true)
    public EstadoPagoResponse obtenerEstado(String referencia, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);

        PagoWompi pago = pagoWompiRepository.findByReferencia(referencia)
                .or(() -> pagoWompiRepository.findFirstByReferenciaStartingWith(ReferenciaPago.base(referencia)))
                .orElseThrow(() -> new ApiException("Pago no encontrado", HttpStatus.NOT_FOUND));

        verificarAccesoCliente(usuario, pago.getCliente().getIdCliente());

        JsonNode transaccion = extraerNodoTransaccion(pago.getPayloadUltimoEvento());

        return new EstadoPagoResponse(
                pago.getReferencia(),
                pago.getMontoEnCentavos(),
                pago.getMoneda(),
                pago.getEstado().name(),
                pago.getMetodoPago(),
                pago.getDescripcion(),
                pago.getFechaCreacion(),
                pago.getFechaActualizacion(),
                pago.getTransaccionWompiId(),
                textoOrNull(transaccion, "payment_method", "extra", "external_identifier"),
                textoOrNull(transaccion, "customer_data", "full_name"),
                textoOrNull(transaccion, "customer_data", "phone_number"),
                textoOrNull(transaccion, "customer_email"),
                pago.getFacturaOConceptoId(),
                pago.getTipoConcepto().name());
    }

    /**
     * El payload guardado tiene dos formas posibles según el origen: el body completo del webhook
     * de Wompi ({@code data.transaction.*}) o la respuesta directa de la API de consulta usada por
     * {@code ConciliacionPagosJob} (el objeto de transacción sin el envoltorio {@code data.transaction}).
     */
    private JsonNode extraerNodoTransaccion(String payloadCrudo) {
        if (payloadCrudo == null || payloadCrudo.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payloadCrudo);
            JsonNode anidado = root.path("data").path("transaction");
            if (anidado.hasNonNull("id")) {
                return anidado;
            }
            return root.hasNonNull("id") ? root : null;
        } catch (Exception e) {
            log.warn("No se pudo parsear el payload guardado de Wompi para extraer datos del pagador: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String textoOrNull(JsonNode nodo, String... ruta) {
        if (nodo == null) {
            return null;
        }
        JsonNode actual = nodo;
        for (String campo : ruta) {
            actual = actual.path(campo);
        }
        return actual.isMissingNode() || actual.isNull() ? null : actual.asText(null);
    }

    /** ADMIN_POLYGRAPH y GESTOR tienen acceso total; ADMIN_CLIENTE solo a los pagos de su propio cliente. */
    private void verificarAccesoCliente(Usuario usuario, Integer clienteId) {
        if (usuario.getRol() == Rol.ADMIN_CLIENTE
                && (usuario.getIdCliente() == null || !usuario.getIdCliente().equals(clienteId))) {
            throw new ApiException("No tienes acceso a los pagos de este cliente", HttpStatus.FORBIDDEN);
        }
    }

    private long recalcularMonto(TipoConceptoPago tipoConcepto, Long idConcepto, Integer clienteId) {
        return switch (tipoConcepto) {
            case ORDEN_COMPRA -> recalcularMontoDesdeOrden(idConcepto, clienteId);
            case FACTURA_POSPAGO -> recalcularMontoDesdeFactura(idConcepto, clienteId);
        };
    }

    private long recalcularMontoDesdeOrden(Long idOrdenCompra, Integer clienteId) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrdenCompra)
                .orElseThrow(() -> new ApiException("Orden de compra no encontrada: " + idOrdenCompra, HttpStatus.BAD_REQUEST));

        if (!orden.getCliente().getIdCliente().equals(clienteId)) {
            throw new ApiException("La orden de compra no pertenece al cliente indicado", HttpStatus.BAD_REQUEST);
        }
        if (orden.getEstado() == EstadoOrdenCompra.APROBADA) {
            throw new ApiException("Esta orden de compra ya fue pagada", HttpStatus.CONFLICT);
        }

        return orden.getMontoTotal().multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private long recalcularMontoDesdeFactura(Long idFactura, Integer clienteId) {
        FacturaPospago factura = facturaPospagoRepository.findById(idFactura)
                .orElseThrow(() -> new ApiException("Factura pospago no encontrada: " + idFactura, HttpStatus.BAD_REQUEST));

        if (!factura.getCliente().getIdCliente().equals(clienteId)) {
            throw new ApiException("La factura no pertenece al cliente indicado", HttpStatus.BAD_REQUEST);
        }
        if (factura.getEstado() == EstadoFacturaPospago.PAGADA) {
            throw new ApiException("Esta factura ya fue pagada", HttpStatus.CONFLICT);
        }

        return factura.getMontoTotal().multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private String generarReferencia() {
        String corto = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "PS-" + corto + "-" + System.currentTimeMillis();
    }

    private Usuario resolverUsuario(String emailUsuario) {
        return usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
