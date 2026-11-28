package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.DescuentoDetalleResponse;
import com.polygraph.erp.modules.catalogo.dto.DescuentoRequest;
import com.polygraph.erp.modules.catalogo.dto.DescuentoResponse;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.Descuento;
import com.polygraph.erp.modules.catalogo.entity.DescuentoCliente;
import com.polygraph.erp.modules.catalogo.entity.DescuentoUso;
import com.polygraph.erp.modules.catalogo.repository.ClasificacionProcesoRepository;
import com.polygraph.erp.modules.catalogo.repository.DescuentoClienteRepository;
import com.polygraph.erp.modules.catalogo.repository.DescuentoRepository;
import com.polygraph.erp.modules.catalogo.repository.DescuentoUsoRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.shared.enums.PublicoDescuento;
import com.polygraph.erp.shared.enums.TipoDescuento;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Motor de descuentos: reglas dinámicas (% o monto fijo, con o sin código, con o sin vigencia)
 * aplicables a todo el catálogo, una categoría o un proceso puntual — en vez de precios de
 * promoción hardcodeados. El precio final SIEMPRE se recalcula aquí, nunca se confía en un
 * descuento que venga del frontend.
 *
 * Cada descuento puede ir dirigido a TODOS los clientes o solo a una lista específica
 * (publicoObjetivo). Los que tienen código (cupón) además solo se pueden usar una vez por
 * cliente — se valida al crear la orden y se confirma al aprobarse el pago.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescuentoService {

    private final DescuentoRepository descuentoRepository;
    private final DescuentoClienteRepository descuentoClienteRepository;
    private final DescuentoUsoRepository descuentoUsoRepository;
    private final ClasificacionProcesoRepository clasificacionRepository;
    private final ProcesoRepository procesoRepository;
    private final ClienteRepository clienteRepository;

    /**
     * Valida un código de cupón para este cliente: existe, activo, vigente, el cliente es
     * elegible y no lo ha usado antes. Si no se indicó código, no es un error — devuelve null.
     */
    @Transactional(readOnly = true)
    public Descuento validarCupon(String codigo, Integer idCliente) {
        if (codigo == null || codigo.isBlank()) return null;

        Descuento d = descuentoRepository.findByCodigoIgnoreCase(codigo.trim())
                .orElseThrow(() -> new ApiException("El código de descuento no existe", HttpStatus.BAD_REQUEST));

        if (!Boolean.TRUE.equals(d.getActivo())) {
            throw new ApiException("Este código de descuento ya no está activo", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (d.getFechaInicio() != null && ahora.isBefore(d.getFechaInicio())) {
            throw new ApiException("Este código todavía no está vigente", HttpStatus.BAD_REQUEST);
        }
        if (d.getFechaFin() != null && ahora.isAfter(d.getFechaFin())) {
            throw new ApiException("Este código ya venció", HttpStatus.BAD_REQUEST);
        }
        if (!esElegible(d, idCliente)) {
            throw new ApiException("Este código de descuento no aplica a tu cuenta", HttpStatus.BAD_REQUEST);
        }
        if (descuentoUsoRepository.existsByDescuento_IdDescuentoAndCliente_IdCliente(d.getIdDescuento(), idCliente)) {
            throw new ApiException("Ya usaste este código de descuento", HttpStatus.BAD_REQUEST);
        }
        return d;
    }

    /** Mejor precio para este proceso: compara los descuentos automáticos vigentes y elegibles contra el cupón ya validado (si hay). */
    @Transactional(readOnly = true)
    public BigDecimal aplicarMejorPrecio(Proceso proceso, BigDecimal precioBase, Integer idCliente, Descuento cupon) {
        return mejorPrecioConDescuento(proceso, precioBase, idCliente, cupon).precio();
    }

    /** Igual que {@link #aplicarMejorPrecio}, pero además indica CUÁL descuento ganó (o null si ninguno aplicó) — para dejarlo registrado en el ítem de la orden. */
    @Transactional(readOnly = true)
    public MejorPrecioResult mejorPrecioConDescuento(Proceso proceso, BigDecimal precioBase, Integer idCliente, Descuento cupon) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Descuento> vigentes = descuentoRepository.findVigentesParaProceso(
                proceso.getIdProceso(), proceso.getClasificacion().getIdClasificacion(), ahora);

        BigDecimal mejorPrecio = precioBase;
        Descuento mejorDescuento = null;
        for (Descuento d : vigentes) {
            if (d.getCodigo() != null) continue; // los con código solo aplican si son el cupón validado
            if (!esElegible(d, idCliente)) continue;
            BigDecimal precioConD = calcularPrecioConDescuento(d, precioBase);
            if (precioConD.compareTo(mejorPrecio) < 0) {
                mejorPrecio = precioConD;
                mejorDescuento = d;
            }
        }
        if (cupon != null && aplicaAlcance(cupon, proceso)) {
            BigDecimal precioConCupon = calcularPrecioConDescuento(cupon, precioBase);
            if (precioConCupon.compareTo(mejorPrecio) < 0) {
                mejorPrecio = precioConCupon;
                mejorDescuento = cupon;
            }
        }
        return new MejorPrecioResult(mejorPrecio, mejorDescuento);
    }

    public record MejorPrecioResult(BigDecimal precio, Descuento descuento) {}

    /** Confirma el uso del cupón de una orden — se llama solo cuando la orden queda aprobada. */
    @Transactional
    public void registrarUso(Descuento descuento, Cliente cliente, OrdenCompra orden) {
        if (descuento == null) return;
        if (descuentoUsoRepository.existsByDescuento_IdDescuentoAndCliente_IdCliente(descuento.getIdDescuento(), cliente.getIdCliente())) {
            log.warn("Intento de registrar de nuevo un cupón ya usado: descuento={}, cliente={}", descuento.getIdDescuento(), cliente.getIdCliente());
            return;
        }
        descuentoUsoRepository.save(DescuentoUso.builder()
                .descuento(descuento)
                .cliente(cliente)
                .ordenCompra(orden)
                .fechaUso(LocalDateTime.now())
                .build());
        log.info("Cupón usado: descuento={}, cliente={}, orden={}", descuento.getIdDescuento(), cliente.getIdCliente(), orden.getIdOrdenCompra());
    }

    private boolean esElegible(Descuento d, Integer idCliente) {
        if (d.getPublicoObjetivo() == PublicoDescuento.TODOS) return true;
        return descuentoClienteRepository.existsByDescuento_IdDescuentoAndCliente_IdCliente(d.getIdDescuento(), idCliente);
    }

    private boolean aplicaAlcance(Descuento d, Proceso proceso) {
        return switch (d.getAlcance()) {
            case GLOBAL -> true;
            case PROCESO -> d.getProceso() != null && d.getProceso().getIdProceso().equals(proceso.getIdProceso());
            case CATEGORIA -> d.getClasificacion() != null
                    && d.getClasificacion().getIdClasificacion().equals(proceso.getClasificacion().getIdClasificacion());
        };
    }

    private BigDecimal calcularPrecioConDescuento(Descuento d, BigDecimal precioBase) {
        BigDecimal reduccion = d.getTipo() == TipoDescuento.PORCENTAJE
                ? precioBase.multiply(d.getValor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : d.getValor();
        if (d.getMontoMaximoDescuento() != null) {
            reduccion = reduccion.min(d.getMontoMaximoDescuento());
        }
        return precioBase.subtract(reduccion).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<DescuentoResponse> listar() {
        return descuentoRepository.findAllByOrderByFechaCreacionDesc().stream().map(this::mapear).toList();
    }

    @Transactional
    public DescuentoResponse crear(DescuentoRequest request) {
        Descuento descuento = new Descuento();
        aplicarCampos(descuento, request);
        descuentoRepository.save(descuento);
        guardarClientesElegibles(descuento, request);
        log.info("Descuento creado: id={}, nombre={}, alcance={}", descuento.getIdDescuento(), descuento.getNombre(), descuento.getAlcance());
        return mapear(descuento);
    }

    @Transactional
    public DescuentoResponse actualizar(Long id, DescuentoRequest request) {
        Descuento descuento = descuentoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Descuento no encontrado", HttpStatus.NOT_FOUND));
        aplicarCampos(descuento, request);
        descuentoRepository.save(descuento);
        guardarClientesElegibles(descuento, request);
        return mapear(descuento);
    }

    /** Desglose completo para el admin: info del descuento + quién lo ha usado y cuándo. */
    @Transactional(readOnly = true)
    public DescuentoDetalleResponse obtenerDetalle(Long id) {
        Descuento descuento = descuentoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Descuento no encontrado", HttpStatus.NOT_FOUND));

        List<DescuentoDetalleResponse.UsoResponse> usos = descuentoUsoRepository
                .findByDescuento_IdDescuentoOrderByFechaUsoDesc(id).stream()
                .map(u -> new DescuentoDetalleResponse.UsoResponse(
                        u.getCliente().getIdCliente(),
                        nombreClienteDisplay(u.getCliente()),
                        u.getCliente().getEmailPrincipal(),
                        u.getFechaUso(),
                        u.getOrdenCompra() != null ? u.getOrdenCompra().getIdOrdenCompra() : null,
                        u.getOrdenCompra() != null ? u.getOrdenCompra().getReferencia() : null,
                        u.getOrdenCompra() != null ? u.getOrdenCompra().getEstado().name() : null,
                        u.getOrdenCompra() != null ? u.getOrdenCompra().getMontoTotal() : null))
                .toList();

        return new DescuentoDetalleResponse(mapear(descuento), usos);
    }

    @Transactional
    public DescuentoResponse cambiarActivo(Long id, boolean activo) {
        Descuento descuento = descuentoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Descuento no encontrado", HttpStatus.NOT_FOUND));
        descuento.setActivo(activo);
        descuentoRepository.save(descuento);
        return mapear(descuento);
    }

    private void aplicarCampos(Descuento descuento, DescuentoRequest request) {
        descuento.setNombre(request.nombre());
        descuento.setCodigo(request.codigo() != null && !request.codigo().isBlank() ? request.codigo().toUpperCase() : null);
        descuento.setTipo(request.tipo());
        descuento.setValor(request.valor());
        descuento.setAlcance(request.alcance());
        descuento.setMontoMaximoDescuento(request.montoMaximoDescuento());
        descuento.setFechaInicio(request.fechaInicio());
        descuento.setFechaFin(request.fechaFin());
        descuento.setActivo(request.activo() != null ? request.activo() : Boolean.TRUE);
        descuento.setPublicoObjetivo(request.publicoObjetivo() != null ? request.publicoObjetivo() : PublicoDescuento.TODOS);

        descuento.setClasificacion(null);
        descuento.setProceso(null);

        switch (request.alcance()) {
            case CATEGORIA -> descuento.setClasificacion(resolverClasificacion(request.idClasificacion()));
            case PROCESO -> descuento.setProceso(resolverProceso(request.idProceso()));
            case GLOBAL -> { /* sin referencia */ }
        }
    }

    /** Reemplaza el set completo de clientes elegibles — solo tiene sentido si publicoObjetivo = CLIENTES_ESPECIFICOS. */
    private void guardarClientesElegibles(Descuento descuento, DescuentoRequest request) {
        descuentoClienteRepository.deleteByDescuento_IdDescuento(descuento.getIdDescuento());
        // Sin este flush, el DELETE puede quedar pendiente mientras los INSERT de abajo se ejecutan
        // de inmediato (IDENTITY obliga a insertar ya) — al reseleccionar los mismos clientes (lo normal
        // al editar, porque el formulario se precarga con ellos) choca con UNIQUE(id_descuento, id_cliente).
        descuentoClienteRepository.flush();
        if (descuento.getPublicoObjetivo() != PublicoDescuento.CLIENTES_ESPECIFICOS) return;

        List<Integer> ids = request.idsClientesElegibles();
        if (ids == null || ids.isEmpty()) {
            throw new ApiException("Debe seleccionar al menos un cliente para este descuento", HttpStatus.BAD_REQUEST);
        }
        for (Integer idCliente : ids) {
            Cliente cliente = clienteRepository.findById(idCliente)
                    .orElseThrow(() -> new ApiException("Cliente no encontrado: " + idCliente, HttpStatus.BAD_REQUEST));
            descuentoClienteRepository.save(DescuentoCliente.builder()
                    .descuento(descuento)
                    .cliente(cliente)
                    .build());
        }
    }

    private ClasificacionProceso resolverClasificacion(Integer id) {
        if (id == null) {
            throw new ApiException("Debe indicar la categoría para un descuento de alcance CATEGORIA", HttpStatus.BAD_REQUEST);
        }
        return clasificacionRepository.findById(id)
                .orElseThrow(() -> new ApiException("Categoría no encontrada: " + id, HttpStatus.BAD_REQUEST));
    }

    private Proceso resolverProceso(Integer id) {
        if (id == null) {
            throw new ApiException("Debe indicar el proceso para un descuento de alcance PROCESO", HttpStatus.BAD_REQUEST);
        }
        return procesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Proceso no encontrado: " + id, HttpStatus.BAD_REQUEST));
    }

    private String nombreClienteDisplay(Cliente c) {
        if (c.getTipoPersona() != null && "NATURAL".equals(c.getTipoPersona().name())) {
            String nombre = (c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "");
            return nombre.isBlank() ? c.getEmailPrincipal() : nombre.trim();
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }

    private DescuentoResponse mapear(Descuento d) {
        List<DescuentoResponse.ClienteElegibleResponse> clientesElegibles =
                d.getPublicoObjetivo() == PublicoDescuento.CLIENTES_ESPECIFICOS
                        ? descuentoClienteRepository.findByDescuento_IdDescuento(d.getIdDescuento()).stream()
                            .map(dc -> new DescuentoResponse.ClienteElegibleResponse(
                                    dc.getCliente().getIdCliente(), nombreClienteDisplay(dc.getCliente())))
                            .toList()
                        : List.of();

        return new DescuentoResponse(
                d.getIdDescuento(),
                d.getNombre(),
                d.getCodigo(),
                d.getTipo().name(),
                d.getValor(),
                d.getAlcance().name(),
                d.getClasificacion() != null ? d.getClasificacion().getIdClasificacion() : null,
                d.getClasificacion() != null ? d.getClasificacion().getNombre() : null,
                d.getProceso() != null ? d.getProceso().getIdProceso() : null,
                d.getProceso() != null ? d.getProceso().getNombreProceso() : null,
                d.getMontoMaximoDescuento(),
                d.getFechaInicio(),
                d.getFechaFin(),
                d.getActivo(),
                d.getFechaCreacion(),
                d.getPublicoObjetivo().name(),
                clientesElegibles,
                descuentoUsoRepository.countByDescuento_IdDescuento(d.getIdDescuento()));
    }
}
