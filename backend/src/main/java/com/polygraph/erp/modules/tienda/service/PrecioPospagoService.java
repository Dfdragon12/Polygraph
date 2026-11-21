package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPlan;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPrecio;
import com.polygraph.erp.shared.enums.ModoPrecioPospago;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lista de precios pospago (TP-11). Es el ÚNICO lugar donde se decide cuánto vale un servicio para un cliente pospago, para poder agregar después
 * tarifas por contrato sin tocar el resto. Todo SIN IVA salvo donde se diga lo contrario.
 * <ul>
 *   <li>Modo PLAN_100 (por omisión): el precio por unidad del plan de 100 de la lista prepago, hoy.</li>
 *   <li>Modo PROPIO: {@code precioPospagoPropio}.</li>
 *   <li>Con el interruptor {@code pospagoPlan100} apagado: el comportamiento de siempre (el precio base del servicio) y todo se ofrece.</li>
 * </ul>
 * Se calcula al momento de pedir: cambiar el plan de 100 cambia solo las solicitudes NUEVAS, porque cada cargo guarda el monto con el que se creó (TP-12).
 */
@Service
@RequiredArgsConstructor
public class PrecioPospagoService {

    public static final int PLAN_BASE = 100;

    private final PlanesTiendaService planes;
    private final ParametrosTiendaService parametros;
    private final IvaService iva;
    private final ProcesoRepository procesoRepository;

    /** Una fila de la lista pospago (la del administrador trae origen y margen; la del cliente solo precio). */
    public record Fila(Integer idProceso, String servicio, String tituloComercial, BigDecimal precio, BigDecimal iva, BigDecimal precioConIva,
                       String origen, ModoPrecioPospago modo, boolean ofrecido, BigDecimal margenPorcentaje, boolean margenBajo, String problema) {}

    /** Lo que ve un cliente pospago: sin origen ni margen. */
    public record FilaCliente(Integer idProceso, String servicio, String tituloComercial, BigDecimal precio, BigDecimal iva, BigDecimal precioConIva) {}

    public boolean activo() {
        return parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100);
    }

    public ModoPrecioPospago modo(Proceso p) {
        return p.getModoPrecioPospago() == null ? ModoPrecioPospago.PLAN_100 : p.getModoPrecioPospago();
    }

    /** ¿Aparece para clientes pospago? Con el interruptor apagado, todo se ofrece como hasta hoy. */
    public boolean ofrecido(Proceso p) {
        return !activo() || !Boolean.FALSE.equals(p.getOfrecerPospago());
    }

    /** Precio pospago vigente por unidad (sin IVA); vacío si el servicio no tiene precio con su modo. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> precioVigente(Proceso p) {
        if (!activo()) return Optional.ofNullable(p.getValor());
        if (modo(p) == ModoPrecioPospago.PROPIO) {
            return Optional.ofNullable(p.getPrecioPospagoPropio()).filter(v -> v.signum() > 0);
        }
        return planes.planesDe(p).stream().filter(pl -> pl.unidades() == PLAN_BASE).findFirst().map(PlanPublicoResponse::precioUnitario);
    }

    /** Origen que queda guardado en el cargo: PLAN_100, PROPIO, o BASE con el interruptor apagado. */
    public String codigoOrigen(Proceso p) {
        return activo() ? modo(p).name() : "BASE";
    }

    /** El precio con el que se cobra una solicitud pospago nueva. 400 si el servicio no se ofrece en pospago o no tiene precio. */
    @Transactional(readOnly = true)
    public BigDecimal precioParaCobrar(Proceso p) {
        if (!ofrecido(p)) {
            throw new ApiException("El servicio '" + p.getNombreProceso() + "' no está disponible para tu cuenta pospago", HttpStatus.BAD_REQUEST);
        }
        return precioVigente(p).orElseThrow(() -> new ApiException(
                "El proceso '" + p.getNombreProceso() + "' no tiene un precio definido para clientes pospago", HttpStatus.BAD_REQUEST));
    }

    // ───────────────────────── listas ─────────────────────────

    /** Lista completa para el administrador: todos los servicios activos con su precio, origen, IVA, si se ofrece y margen. */
    @Transactional(readOnly = true)
    public List<Fila> lista() {
        BigDecimal pct = iva.porcentaje();
        BigDecimal minimo = parametros.decimal("margenMinimoPorcentaje");
        return procesoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .sorted(Comparator.comparing(Proceso::getNombreProceso, String.CASE_INSENSITIVE_ORDER))
                .map(p -> fila(p, pct, minimo))
                .toList();
    }

    Fila fila(Proceso p, BigDecimal pct, BigDecimal minimo) {
        Optional<BigDecimal> precio = precioVigente(p);
        BigDecimal valorIva = precio.map(v -> IvaCalculadora.iva(v, pct, !Boolean.FALSE.equals(p.getAplicaIva()))).orElse(null);
        BigDecimal margen = precio.map(v -> margen(v, p.getCostoDirecto())).orElse(null);
        String origen = modo(p) == ModoPrecioPospago.PROPIO ? "Precio propio" : "Auto · plan de 100";
        String problema = precio.isEmpty() && ofrecido(p) ? "Se ofrece en pospago y no tiene precio" : null;
        return new Fila(p.getIdProceso(), p.getNombreProceso(), p.getTituloComercial(), precio.orElse(null), valorIva,
                precio.map(v -> v.add(valorIva)).orElse(null), origen, modo(p), ofrecido(p), margen, margen != null && margen.compareTo(minimo) < 0, problema);
    }

    /** Lista del cliente pospago: solo los servicios que se le ofrecen y tienen precio. 403 si el cliente no es pospago (nunca se le muestra a un prepago). */
    @Transactional(readOnly = true)
    public List<FilaCliente> listaParaCliente(Cliente cliente) {
        if (cliente == null || cliente.getTipoCliente() != TipoCliente.POSPAGO) {
            throw new ApiException("La lista de precios pospago solo está disponible para clientes pospago", HttpStatus.FORBIDDEN);
        }
        BigDecimal pct = iva.porcentaje();
        return procesoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()) && ofrecido(p))
                .sorted(Comparator.comparing(Proceso::getNombreProceso, String.CASE_INSENSITIVE_ORDER))
                .flatMap(p -> precioVigente(p).stream().map(v -> {
                    BigDecimal i = IvaCalculadora.iva(v, pct, !Boolean.FALSE.equals(p.getAplicaIva()));
                    return new FilaCliente(p.getIdProceso(), p.getNombreProceso(), p.getTituloComercial(), v, i, v.add(i));
                }))
                .toList();
    }

    public record Margen(BigDecimal margenPorcentaje, boolean margenBajo, BigDecimal minimo) {}

    /** Margen pospago de un precio con un costo directo, y si queda bajo el mínimo (alerta en rojo en el formulario). */
    public Margen analizarMargen(BigDecimal precio, BigDecimal costo) {
        BigDecimal minimo = parametros.decimal("margenMinimoPorcentaje");
        BigDecimal m = margen(precio, costo);
        return new Margen(m, m != null && m.compareTo(minimo) < 0, minimo);
    }

    /** (precio − costo) / precio, en %, con un decimal; null si no hay costo directo. */
    static BigDecimal margen(BigDecimal precio, BigDecimal costo) {
        if (costo == null || precio == null || precio.signum() <= 0) return null;
        return precio.subtract(costo).multiply(BigDecimal.valueOf(100)).divide(precio, 1, RoundingMode.HALF_UP);
    }

    // ───────────────────────── validaciones al guardar (solo con el interruptor encendido) ─────────────────────────

    /** Al guardar un servicio: ofrecido → debe haber precio con su modo; PROPIO → mayor que 0; PLAN_100 → el plan de 100 debe tener precio y estar visible. */
    @Transactional(readOnly = true)
    public void validarAlGuardar(Proceso p, Boolean ofrecer, ModoPrecioPospago modo, BigDecimal propio) {
        if (!activo()) return;
        boolean ofrece = ofrecer != null ? ofrecer : !Boolean.FALSE.equals(p.getOfrecerPospago());
        if (!ofrece) return;
        ModoPrecioPospago m = modo != null ? modo : modo(p);
        if (m == ModoPrecioPospago.PROPIO) {
            BigDecimal v = propio != null ? propio : p.getPrecioPospagoPropio();
            if (v == null || v.signum() <= 0) {
                throw new ApiException("Con precio propio, el precio pospago de '" + p.getNombreProceso() + "' debe ser mayor que 0", HttpStatus.BAD_REQUEST);
            }
        } else if (planes.planesDe(p).stream().noneMatch(pl -> pl.unidades() == PLAN_BASE)) {
            throw new ApiException("'" + p.getNombreProceso() + "' se ofrece en pospago con precio automático y no tiene precio del plan de 100 unidades "
                    + "(o ese plan está oculto). Cárgalo, o elige «precio propio», o deja de ofrecerlo en pospago.", HttpStatus.BAD_REQUEST);
        }
    }

    /** Servicios activos que se ofrecen en pospago con precio automático: dependen del plan de 100. */
    @Transactional(readOnly = true)
    public List<String> serviciosQueDependenDelPlan100() {
        return procesoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()) && !Boolean.FALSE.equals(p.getOfrecerPospago()) && modo(p) == ModoPrecioPospago.PLAN_100)
                .map(Proceso::getNombreProceso).sorted().toList();
    }

    /** No se puede ocultar el plan de 100 mientras haya servicios pospago automáticos que dependen de él. */
    @Transactional(readOnly = true)
    public void validarPlanes(List<EntradaPlan> entradas) {
        if (!activo() || entradas == null) return;
        boolean oculta100 = entradas.stream().anyMatch(e -> e.unidades() != null && e.unidades() == PLAN_BASE && !Boolean.TRUE.equals(e.visible()));
        if (!oculta100) return;
        List<String> deps = serviciosQueDependenDelPlan100();
        if (!deps.isEmpty()) {
            throw new ApiException("No se puede ocultar el plan de 100: lo usa el precio pospago automático de " + String.join(", ", deps)
                    + ". Cámbialos a «precio propio» o déjalos de ofrecer en pospago primero.", HttpStatus.BAD_REQUEST);
        }
    }

    /** No se puede dejar sin precio el plan de 100 de un servicio ofrecido en pospago automático. */
    @Transactional(readOnly = true)
    public void validarPrecios(Integer idProceso, List<EntradaPrecio> precios) {
        if (!activo() || precios == null) return;
        boolean quitaPlan100 = precios.stream().anyMatch(e -> e.unidades() != null && e.unidades() == PLAN_BASE && (e.precioUnitario() == null || e.precioUnitario().signum() <= 0));
        if (!quitaPlan100) return;
        procesoRepository.findById(idProceso).ifPresent(p -> {
            if (ofrecido(p) && modo(p) == ModoPrecioPospago.PLAN_100) {
                throw new ApiException("No se puede dejar sin precio el plan de 100 de '" + p.getNombreProceso() + "': se ofrece en pospago con precio automático.",
                        HttpStatus.BAD_REQUEST);
            }
        });
    }
}
