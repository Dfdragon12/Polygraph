package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.config.WompiProperties;
import com.polygraph.erp.modules.pagos.dto.EstadoPagoResponse;
import com.polygraph.erp.modules.pagos.dto.IniciarPagoWompiRequest;
import com.polygraph.erp.modules.pagos.dto.IniciarPagoWompiResponse;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.PagoWompi;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraRepository;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import com.polygraph.erp.shared.enums.EstadoPago;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.EnmascaradorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final WompiSignatureService wompiSignatureService;
    private final WompiProperties wompiProperties;
    private final AuditoriaPagoService auditoriaPagoService;

    public IniciarPagoWompiResponse iniciarPago(IniciarPagoWompiRequest request, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);
        verificarAccesoCliente(usuario, request.clienteId());

        if (request.montoEnCentavos() == null || request.montoEnCentavos() <= 0) {
            throw new ApiException("montoEnCentavos debe ser mayor a cero", HttpStatus.BAD_REQUEST);
        }

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        // Nunca se confía en el monto que envía el frontend: se recalcula desde la orden de compra.
        long montoRecalculado = recalcularMontoDesdeOrden(request.facturaOConceptoId(), request.clienteId());
        if (!request.montoEnCentavos().equals(montoRecalculado)) {
            log.warn("montoEnCentavos recibido ({}) no coincide con el recalculado ({}) — se usa el recalculado. referenciaOrden={}",
                    request.montoEnCentavos(), montoRecalculado, request.facturaOConceptoId());
        }

        String referencia = generarReferencia();
        LocalDateTime ahora = LocalDateTime.now();

        PagoWompi pago = PagoWompi.builder()
                .referencia(referencia)
                .montoEnCentavos(montoRecalculado)
                .moneda(MONEDA)
                .estado(EstadoPago.PENDIENTE)
                .cliente(cliente)
                .creadoPor(usuario)
                .facturaOConceptoId(request.facturaOConceptoId())
                .descripcion(request.descripcion())
                .fechaCreacion(ahora)
                .fechaActualizacion(ahora)
                .build();
        pagoWompiRepository.save(pago);
        auditoriaPagoService.registrarCreacion(pago, usuario);

        String firmaIntegridad = wompiSignatureService.generarFirmaIntegridad(referencia, montoRecalculado, MONEDA);

        log.info("Pago Wompi iniciado: referencia={}, cliente={}, monto={}, generadoPor={}",
                referencia, cliente.getIdCliente(), montoRecalculado, EnmascaradorUtil.enmascararEmail(usuario.getEmail()));

        return new IniciarPagoWompiResponse(
                wompiProperties.getPublicKey(),
                referencia,
                montoRecalculado,
                MONEDA,
                firmaIntegridad,
                wompiProperties.getRedirectUrl());
    }

    @Transactional(readOnly = true)
    public EstadoPagoResponse obtenerEstado(String referencia, String emailUsuario) {
        Usuario usuario = resolverUsuario(emailUsuario);

        PagoWompi pago = pagoWompiRepository.findByReferencia(referencia)
                .orElseThrow(() -> new ApiException("Pago no encontrado", HttpStatus.NOT_FOUND));

        verificarAccesoCliente(usuario, pago.getCliente().getIdCliente());

        return new EstadoPagoResponse(
                pago.getReferencia(),
                pago.getMontoEnCentavos(),
                pago.getMoneda(),
                pago.getEstado().name(),
                pago.getMetodoPago(),
                pago.getDescripcion(),
                pago.getFechaCreacion(),
                pago.getFechaActualizacion());
    }

    /** ADMIN_POLYGRAPH y GESTOR tienen acceso total; ADMIN_CLIENTE solo a los pagos de su propio cliente. */
    private void verificarAccesoCliente(Usuario usuario, Integer clienteId) {
        if (usuario.getRol() == Rol.ADMIN_CLIENTE
                && (usuario.getIdCliente() == null || !usuario.getIdCliente().equals(clienteId))) {
            throw new ApiException("No tienes acceso a los pagos de este cliente", HttpStatus.FORBIDDEN);
        }
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

    private String generarReferencia() {
        String corto = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "PS-" + corto + "-" + System.currentTimeMillis();
    }

    private Usuario resolverUsuario(String emailUsuario) {
        return usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
