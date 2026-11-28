package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.pagos.dto.RecargosPendientesResponse;
import com.polygraph.erp.modules.pagos.entity.OrdenCompra;
import com.polygraph.erp.modules.pagos.entity.RecargoPendiente;
import com.polygraph.erp.modules.pagos.repository.RecargoPendienteRepository;
import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.shared.enums.EstadoRecargoPendiente;
import com.polygraph.erp.modules.tienda.service.CalculadoraCobertura;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El recargo por ciudad de un proceso NO se cobra en el momento de la solicitud (ahí no hay pago,
 * solo se descuenta saldo o se factura pospago) — se registra como deuda pendiente del cliente y
 * se cobra automáticamente en su siguiente compra en Comprar Servicios, sumado al total de esa orden.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecargoPendienteService {

    private final RecargoPendienteRepository recargoPendienteRepository;
    private final PrecioCiudadProcesoRepository precioCiudadProcesoRepository;
    private final CiudadRepository ciudadRepository;

    private final com.polygraph.erp.modules.tienda.service.ParametrosTiendaService parametros;

    /** Recargo adicional según el nivel logístico de la ciudad — 0 si no hay ciudad o el proceso no aplica recargo por ciudad. */
    @Transactional(readOnly = true)
    public BigDecimal calcular(Proceso proceso, Ciudad ciudad) {
        return calcular(proceso, ciudad, CalculadoraCobertura.LOCAL);
    }

    /**
     * Recargo por UNIDAD consumida (sin IVA). Con coberturaPorSede encendido: ciudad sede = 0, principal que no es sede e intermedia = recargo
     * intermedio, municipio = recargo de municipio, y una unidad NACIONAL nunca paga recargo. Apagado: la regla de siempre (la fila del nivel de la ciudad).
     */
    @Transactional(readOnly = true)
    public BigDecimal calcular(Proceso proceso, Ciudad ciudad, String cobertura) {
        if (ciudad == null || !Boolean.TRUE.equals(proceso.getAplicaPrecioCiudad())) return BigDecimal.ZERO;
        if (!parametros.activo(com.polygraph.erp.modules.tienda.service.ParametrosTiendaService.COBERTURA_POR_SEDE)) {
            return precioCiudadProcesoRepository.findByProceso_IdProcesoAndNivelCiudad(proceso.getIdProceso(), ciudad.getNivelCiudad())
                    .map(PrecioCiudadProceso::getValor)
                    .orElse(BigDecimal.ZERO);
        }
        return CalculadoraCobertura.recargo(true, Boolean.TRUE.equals(ciudad.getEsSede()), ciudad.getNivelCiudad(), cobertura, recargosDe(proceso));
    }

    /** Recargo de ciudad intermedia del servicio (base del precio del paquete nacional); 0 si no aplica o no está cargado. */
    @Transactional(readOnly = true)
    public BigDecimal recargoIntermedio(Proceso proceso) {
        if (!Boolean.TRUE.equals(proceso.getAplicaPrecioCiudad())) return BigDecimal.ZERO;
        return recargosDe(proceso).getOrDefault(com.polygraph.erp.shared.enums.NivelCiudad.INTERMEDIA, BigDecimal.ZERO);
    }

    private java.util.Map<com.polygraph.erp.shared.enums.NivelCiudad, BigDecimal> recargosDe(Proceso proceso) {
        java.util.Map<com.polygraph.erp.shared.enums.NivelCiudad, BigDecimal> m = new java.util.EnumMap<>(com.polygraph.erp.shared.enums.NivelCiudad.class);
        for (PrecioCiudadProceso pc : precioCiudadProcesoRepository.findByProceso_IdProcesoOrderByNivelCiudadAsc(proceso.getIdProceso())) {
            m.put(pc.getNivelCiudad(), pc.getValor());
        }
        return m;
    }

    /** Se llama al crear una solicitud — si aplica recargo para la ciudad del candidato, queda registrado como pendiente. */
    @Transactional
    public void registrarSiAplica(Cliente cliente, Candidato candidato, Proceso proceso, Integer idCiudad) {
        registrarSiAplica(cliente, candidato, proceso, idCiudad, null);
    }

    @Transactional
    public void registrarSiAplica(Cliente cliente, Candidato candidato, Proceso proceso, Integer idCiudad, String motivo) {
        registrarSiAplica(cliente, candidato, proceso, idCiudad, motivo, CalculadoraCobertura.LOCAL);
    }

    /** `cobertura` es la bolsa de la que salió la unidad: una unidad NACIONAL nunca genera recargo (TP-08). */
    @Transactional
    public void registrarSiAplica(Cliente cliente, Candidato candidato, Proceso proceso, Integer idCiudad, String motivo, String cobertura) {
        if (idCiudad == null) return;
        Ciudad ciudad = ciudadRepository.findById(idCiudad).orElse(null);
        if (ciudad == null) return;

        BigDecimal monto = calcular(proceso, ciudad, cobertura);
        if (monto.signum() <= 0) return;

        recargoPendienteRepository.save(RecargoPendiente.builder()
                .cliente(cliente)
                .candidato(candidato)
                .proceso(proceso)
                .ciudad(ciudad)
                .monto(monto)
                .motivo(motivo)
                .estado(EstadoRecargoPendiente.PENDIENTE)
                .fechaGenerado(LocalDateTime.now())
                .build());
        log.info("Recargo por ciudad pendiente registrado: cliente={}, candidato={}, proceso={}, ciudad={}, monto={}",
                cliente.getIdCliente(), candidato.getIdCandidato(), proceso.getIdProceso(), ciudad.getNombreCiudad(), monto);
    }

    /**
     * El candidato cambió de ciudad, posiblemente más de una vez, después de que ya se había
     * registrado (o incluso cobrado) un recargo. En vez de mirar solo la última fila, se recalcula
     * el saldo neto para este candidato+proceso: lo que ya quedó PAGADO (cobrado en una compra
     * anterior) contra lo que debería costar la ciudad nueva. Todas las filas PENDIENTE existentes
     * (cargos sin cobrar o créditos sin aplicar de correcciones previas) se reemplazan por un único
     * ajuste con esa diferencia — así un crédito pendiente por volver a una ciudad ya pagada no se
     * pierde si el candidato vuelve a cambiar de ciudad antes de la siguiente compra.
     */
    @Transactional
    public void reconciliarPorCambioCiudad(Cliente cliente, Candidato candidato, Proceso proceso, Integer idCiudadNueva) {
        reconciliarPorCambioCiudad(cliente, candidato, proceso, idCiudadNueva, CalculadoraCobertura.LOCAL);
    }

    /** `cobertura`: la bolsa de la que salió la unidad; una unidad NACIONAL no paga recargo en la ciudad nueva (TP-08). */
    @Transactional
    public void reconciliarPorCambioCiudad(Cliente cliente, Candidato candidato, Proceso proceso, Integer idCiudadNueva, String cobertura) {
        List<RecargoPendiente> historial = recargoPendienteRepository
                .findByCandidato_IdCandidatoAndProceso_IdProceso(candidato.getIdCandidato(), proceso.getIdProceso());

        BigDecimal yaPagado = historial.stream()
                .filter(r -> r.getEstado() == EstadoRecargoPendiente.PAGADO)
                .map(RecargoPendiente::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RecargoPendiente> pendientesExistentes = historial.stream()
                .filter(r -> r.getEstado() == EstadoRecargoPendiente.PENDIENTE)
                .toList();
        if (!pendientesExistentes.isEmpty()) {
            recargoPendienteRepository.deleteAll(pendientesExistentes);
        }

        Ciudad ciudadNueva = idCiudadNueva != null ? ciudadRepository.findById(idCiudadNueva).orElse(null) : null;
        BigDecimal costoCiudadNueva = ciudadNueva != null ? calcular(proceso, ciudadNueva, cobertura) : BigDecimal.ZERO;

        BigDecimal ajuste = costoCiudadNueva.subtract(yaPagado);
        if (ajuste.signum() == 0 || ciudadNueva == null) {
            log.info("Recargo por ciudad sin ajuste tras corrección: candidato={}, proceso={}, ciudad={}, ya pagado={}",
                    candidato.getIdCandidato(), proceso.getIdProceso(), idCiudadNueva, yaPagado);
            return;
        }

        recargoPendienteRepository.save(RecargoPendiente.builder()
                .cliente(cliente)
                .candidato(candidato)
                .proceso(proceso)
                .ciudad(ciudadNueva)
                .monto(ajuste)
                .motivo(ajuste.signum() < 0
                        ? "Crédito por corrección de ciudad — ya se había pagado más recargo del que corresponde"
                        : "Recargo recalculado por corrección de ciudad")
                .estado(EstadoRecargoPendiente.PENDIENTE)
                .fechaGenerado(LocalDateTime.now())
                .build());
        log.info("Recargo por ciudad ajustado por corrección: candidato={}, proceso={}, ciudad nueva={}, ya pagado={}, ajuste={}",
                candidato.getIdCandidato(), proceso.getIdProceso(), ciudadNueva.getNombreCiudad(), yaPagado, ajuste);
    }

    /**
     * TP-09: la solicitud se canceló. El recargo que seguía pendiente (nunca se cobró) se descarta y el que ya se pagó se reversa como un crédito
     * pendiente (negativo) que baja la próxima compra. Idempotente: no duplica un crédito que ya está pendiente.
     */
    @Transactional
    public void reversarPorCancelacion(Cliente cliente, Candidato candidato, Proceso proceso) {
        if (candidato == null) return;
        List<RecargoPendiente> historial = recargoPendienteRepository
                .findByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoAsc(candidato.getIdCandidato(), proceso.getIdProceso());
        if (historial.isEmpty()) return;
        List<RecargoPendiente> cargosSinCobrar = historial.stream()
                .filter(r -> r.getEstado() == EstadoRecargoPendiente.PENDIENTE && r.getMonto().signum() > 0).toList();
        if (!cargosSinCobrar.isEmpty()) recargoPendienteRepository.deleteAll(cargosSinCobrar);

        BigDecimal pagado = historial.stream().filter(r -> r.getEstado() == EstadoRecargoPendiente.PAGADO)
                .map(RecargoPendiente::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditosPendientes = historial.stream()
                .filter(r -> r.getEstado() == EstadoRecargoPendiente.PENDIENTE && r.getMonto().signum() < 0)
                .map(RecargoPendiente::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aReversar = pagado.add(creditosPendientes);
        if (aReversar.signum() <= 0) return;

        recargoPendienteRepository.save(RecargoPendiente.builder()
                .cliente(cliente).candidato(candidato).proceso(proceso).ciudad(historial.get(historial.size() - 1).getCiudad())
                .monto(aReversar.negate()).motivo("Reverso por cancelación de la solicitud — el recargo por ciudad ya estaba pagado")
                .estado(EstadoRecargoPendiente.PENDIENTE).fechaGenerado(LocalDateTime.now()).build());
        log.info("Recargo por ciudad reversado por cancelación: candidato={}, proceso={}, monto={}", candidato.getIdCandidato(), proceso.getIdProceso(), aReversar);
    }

    /** El más reciente para este candidato+proceso, sin importar el estado — para mostrar en el detalle de la solicitud. */
    @Transactional(readOnly = true)
    public RecargoPendiente buscarPara(Long idCandidato, Integer idProceso) {
        return recargoPendienteRepository.findTopByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoDesc(idCandidato, idProceso)
                .orElse(null);
    }

    /** Todo el historial de movimientos de recargo por ciudad de este candidato+proceso, en orden cronológico — para mostrar en el detalle de la solicitud. */
    @Transactional(readOnly = true)
    public List<RecargoPendiente> historial(Long idCandidato, Integer idProceso) {
        return recargoPendienteRepository.findByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoAsc(idCandidato, idProceso);
    }

    @Transactional(readOnly = true)
    public List<RecargoPendiente> listarPendientes(Integer idCliente) {
        return recargoPendienteRepository.findByCliente_IdClienteAndEstadoOrderByFechaGeneradoAsc(idCliente, EstadoRecargoPendiente.PENDIENTE);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPendiente(Integer idCliente) {
        return listarPendientes(idCliente).stream().map(RecargoPendiente::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public RecargosPendientesResponse obtenerPendientes(Integer idCliente) {
        List<RecargoPendiente> pendientes = listarPendientes(idCliente);
        List<RecargosPendientesResponse.Item> items = pendientes.stream()
                .map(r -> new RecargosPendientesResponse.Item(
                        r.getIdRecargoPendiente(),
                        r.getProceso().getNombreProceso(),
                        r.getCandidato().getNombres() + " " + r.getCandidato().getApellidos(),
                        r.getCiudad().getNombreCiudad(),
                        r.getMonto(),
                        r.getMotivo(),
                        r.getFechaGenerado()))
                .toList();
        BigDecimal total = items.stream().map(RecargosPendientesResponse.Item::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RecargosPendientesResponse(total, items);
    }

    /** IVA de los recargos pendientes, calculado recargo por recargo (cada uno redondeado a pesos); respeta aplicaIva del servicio. */
    @Transactional(readOnly = true)
    public BigDecimal ivaPendiente(Integer idCliente, BigDecimal porcentaje) {
        return listarPendientes(idCliente).stream()
                .map(r -> com.polygraph.erp.modules.tienda.service.IvaCalculadora.iva(r.getMonto(), porcentaje,
                        !Boolean.FALSE.equals(r.getProceso().getAplicaIva())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Salda TODO lo que siga pendiente del cliente al aprobarse una orden — se cobra junto con esa compra. */
    @Transactional
    public void saldarPendientes(Cliente cliente, OrdenCompra orden) {
        List<RecargoPendiente> pendientes = listarPendientes(cliente.getIdCliente());
        if (pendientes.isEmpty()) return;

        LocalDateTime ahora = LocalDateTime.now();
        for (RecargoPendiente r : pendientes) {
            r.setEstado(EstadoRecargoPendiente.PAGADO);
            r.setOrdenCompra(orden);
            r.setFechaPagado(ahora);
        }
        recargoPendienteRepository.saveAll(pendientes);
        log.info("Recargos pendientes saldados: cliente={}, orden={}, cantidad={}", cliente.getIdCliente(), orden.getIdOrdenCompra(), pendientes.size());
    }
}
