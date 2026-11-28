package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.dto.SaldoServicioResponse;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.tienda.service.CalculadoraCobertura;
import com.polygraph.erp.modules.tienda.service.CalculadoraVigencia;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bolsa de servicios prepago: saldo de unidades disponibles por cliente y tipo de proceso. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaldoServicioClienteService {

    private final SaldoServicioClienteRepository saldoRepository;
    private final BolsaPrepagoService bolsa;
    private final LotePrepagoRepository loteRepository;
    private final Clock clock;
    private final ParametrosTiendaService parametros;

    /** De qué lote salió la unidad y cuánto valía (NS-02). Ambos null si ningún lote la respalda (saldo anterior a los lotes). */
    public record UnidadDescontada(Long idLote, BigDecimal valorUnitario, String cobertura) {
        public UnidadDescontada(Long idLote, BigDecimal valorUnitario) { this(idLote, valorUnitario, CalculadoraCobertura.LOCAL); }
    }

    /** Hora actual de Colombia: todas las fechas de lotes (compra, vencimiento) están en esa zona. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA));
    }

    /**
     * Una fila por servicio. Las unidades por sede salen del cálculo único de {@link BolsaPrepagoService}; las del paquete NACIONAL (TP-08) viven aparte,
     * en sus lotes, y se suman al disponible (con su propio número en {@code disponibleNacional}).
     */
    @Transactional(readOnly = true)
    public List<SaldoServicioResponse> obtenerSaldos(Integer idCliente) {
        LocalDateTime ahora = ahora();
        Map<Integer, int[]> nacionales = new LinkedHashMap<>();       // idProceso -> {disponible, comprado, consumido}
        Map<Integer, String> nombres = new HashMap<>();
        for (LotePrepago l : loteRepository.findByCliente_IdClienteAndCobertura(idCliente, CalculadoraCobertura.NACIONAL)) {
            int[] n = nacionales.computeIfAbsent(l.getProceso().getIdProceso(), k -> new int[3]);
            nombres.put(l.getProceso().getIdProceso(), l.getProceso().getNombreProceso());
            n[0] += usables(l, ahora);
            n[1] += l.getCantidadComprada();
            n[2] += l.getCantidadConsumida();
        }
        List<SaldoServicioResponse> filas = new ArrayList<>();
        for (var s : bolsa.saldoPorServicio(idCliente).servicios()) {
            int[] n = nacionales.remove(s.idProceso());
            filas.add(n == null ? new SaldoServicioResponse(s.idProceso(), s.nombreProceso(), s.disponible(), s.comprado(), s.consumido())
                    : new SaldoServicioResponse(s.idProceso(), s.nombreProceso(), s.disponible() + n[0], s.comprado() + n[1], s.consumido() + n[2], n[0]));
        }
        nacionales.forEach((idProceso, n) -> filas.add(new SaldoServicioResponse(idProceso, nombres.get(idProceso), n[0], n[1], n[2], n[0])));

        // TP-09: con la vigencia encendida, el lote que vence primero de cada servicio ("vencen {fecha}")
        if (parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)) {
            Map<Integer, LotePrepago> proximo = new HashMap<>();
            for (LotePrepago l : loteRepository.findByCliente_IdCliente(idCliente)) {
                if (l.getFechaVencimiento() == null || l.getFechaVencida() != null || usables(l, ahora) <= 0) continue;
                proximo.merge(l.getProceso().getIdProceso(), l, (a, b) -> b.getFechaVencimiento().isBefore(a.getFechaVencimiento()) ? b : a);
            }
            for (int i = 0; i < filas.size(); i++) {
                LotePrepago l = proximo.get(filas.get(i).idProceso());
                if (l == null) continue;
                int unidades = loteRepository.findByCliente_IdCliente(idCliente).stream()
                        .filter(x -> x.getProceso().getIdProceso().equals(l.getProceso().getIdProceso()) && x.getFechaVencida() == null
                                && l.getFechaVencimiento().equals(x.getFechaVencimiento()))
                        .mapToInt(x -> usables(x, ahora)).sum();
                filas.set(i, filas.get(i).conVencimiento(l.getFechaVencimiento().toLocalDate(), unidades));
            }
        }
        return filas;
    }

    /** Total de unidades disponibles en todos los servicios (Dashboard y Gestión de cuentas): por sede + nacionales. */
    @Transactional(readOnly = true)
    public int totalDisponible(Integer idCliente) {
        LocalDateTime ahora = ahora();
        int nacionales = loteRepository.findByCliente_IdClienteAndCobertura(idCliente, CalculadoraCobertura.NACIONAL).stream()
                .mapToInt(l -> usables(l, ahora)).sum();
        return bolsa.saldoPorServicio(idCliente).totalDisponible() + nacionales;
    }

    /** Unidades de un lote que todavía se pueden usar: sin consumir, sin vencer y con el día de vencimiento sin pasar. */
    private static int usables(LotePrepago l, LocalDateTime ahora) {
        return CalculadoraVigencia.vencido(l.getFechaVencimiento(), ahora) ? 0 : Math.max(0, l.restante());
    }

    /** Todas las unidades que el cliente puede usar de este servicio: por sede + nacionales (TP-12: un cliente pospago con bolsa las gasta primero). */
    @Transactional(readOnly = true)
    public int disponibleTotal(Integer idCliente, Integer idProceso) {
        return bolsa.disponible(idCliente, idProceso) + disponibleNacional(idCliente, idProceso);
    }

    /** Unidades del paquete nacional que el cliente puede usar de este servicio. */
    @Transactional(readOnly = true)
    public int disponibleNacional(Integer idCliente, Integer idProceso) {
        LocalDateTime ahora = ahora();
        return loteRepository.findByCliente_IdClienteAndProceso_IdProcesoOrderByFechaCompraAscIdLoteAsc(idCliente, idProceso).stream()
                .filter(l -> CalculadoraCobertura.NACIONAL.equals(l.getCobertura())).mapToInt(l -> usables(l, ahora)).sum();
    }

    /** Solo valida que haya al menos 1 unidad disponible; no descuenta. */
    @Transactional(readOnly = true)
    public void validarDisponibilidad(Integer idCliente, Proceso proceso) {
        int disponible = bolsa.disponible(idCliente, proceso.getIdProceso()) + disponibleNacional(idCliente, proceso.getIdProceso());
        if (disponible < 1) {
            throw new ApiException(
                    "Sin unidades disponibles para '" + proceso.getNombreProceso() + "' — cómpralas en Comprar Servicios",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** Descuenta 1 unidad de la bolsa por sede (la preferida por omisión; antes de TP-08 era la única). */
    @Transactional
    public UnidadDescontada descontar(Integer idCliente, Proceso proceso) {
        return descontar(idCliente, proceso, CalculadoraCobertura.LOCAL);
    }

    /**
     * Descuenta 1 unidad del lote que vence primero (los que no vencen, al final; entre iguales, el más antiguo), de la bolsa {@code preferida}
     * (NACIONAL si la ciudad tiene recargo, LOCAL si es sede) y, si no hay de esa, de la otra (TP-08). Los lotes se bloquean antes de tocar nada:
     * dos solicitudes a la vez no gastan la misma unidad.
     */
    @Transactional
    public UnidadDescontada descontar(Integer idCliente, Proceso proceso, String preferida) {
        LocalDateTime ahora = ahora();
        List<LotePrepago> lotes = loteRepository.bloquearPorClienteYProceso(idCliente, proceso.getIdProceso());

        // Con la vigencia encendida, lo que ya venció se pasa a «vencidas» aquí mismo, sin esperar al job: no se puede gastar una unidad vencida.
        if (parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)) {
            for (LotePrepago l : lotes) {
                if (l.getFechaVencida() == null && CalculadoraVigencia.vencido(l.getFechaVencimiento(), ahora)) vencer(l, ahora);
            }
        }

        String[] orden = CalculadoraCobertura.NACIONAL.equals(preferida)
                ? new String[]{CalculadoraCobertura.NACIONAL, CalculadoraCobertura.LOCAL}
                : new String[]{CalculadoraCobertura.LOCAL, CalculadoraCobertura.NACIONAL};
        for (String cobertura : orden) {
            List<LotePrepago> deEsta = lotes.stream().filter(l -> cobertura.equals(l.getCobertura())).toList();
            if (CalculadoraCobertura.NACIONAL.equals(cobertura)) {
                // La bolsa nacional vive solo en sus lotes (bloqueados arriba): no hay contador aparte.
                LotePrepago lote = AsignadorLotes.siguiente(deEsta, ahora).orElse(null);
                if (lote == null) continue;
                return consumir(lote, cobertura);
            }
            // Por sede: una sola sentencia atómica con la condición de saldo; si dos envíos llegan a la vez, el segundo ve 0 filas.
            if (saldoRepository.descontarUna(idCliente, proceso.getIdProceso(), ahora) == 0) continue;
            LotePrepago lote = AsignadorLotes.siguiente(deEsta, ahora).orElse(null);
            if (lote == null) return new UnidadDescontada(null, null, cobertura);
            return consumir(lote, cobertura);
        }
        throw new ApiException(
                "Sin unidades disponibles para '" + proceso.getNombreProceso() + "' — cómpralas en Comprar Servicios",
                HttpStatus.BAD_REQUEST);
    }

    private UnidadDescontada consumir(LotePrepago lote, String cobertura) {
        lote.setCantidadConsumida(lote.getCantidadConsumida() + 1);
        loteRepository.save(lote);
        return new UnidadDescontada(lote.getIdLote(), lote.getValorUnitario(), cobertura);
    }

    /**
     * Pasa a «vencidas» las unidades que le quedaban al lote (TP-07): ya no se pueden usar y se reportan aparte (ingreso por vencimiento).
     * El llamador debe tener el lote bloqueado. @return cuántas unidades venció.
     */
    @Transactional
    public int vencer(LotePrepago lote, LocalDateTime ahora) {
        int n = lote.restante();
        lote.setFechaVencida(ahora);
        if (n > 0) {
            lote.setCantidadVencida((lote.getCantidadVencida() == null ? 0 : lote.getCantidadVencida()) + n);
            // El paquete nacional no tiene contador: sus unidades viven solo en el lote
            if (!CalculadoraCobertura.NACIONAL.equals(lote.getCobertura())
                    && saldoRepository.vencerUnidades(lote.getCliente().getIdCliente(), lote.getProceso().getIdProceso(), n, ahora) == 0) {
                log.warn("Lote {} vencido: el saldo del cliente {} no tenía {} unidades disponibles que descontar", lote.getIdLote(),
                        lote.getCliente().getIdCliente(), n);
            }
            log.info("Lote vencido: lote={}, cliente={}, proceso={}, unidades vencidas={}", lote.getIdLote(), lote.getCliente().getIdCliente(),
                    lote.getProceso().getIdProceso(), n);
        }
        loteRepository.save(lote);
        return n;
    }

    /** Acredita `cantidad` unidades compradas al saldo del cliente para ese proceso (crea la fila si no existe). */
    @Transactional
    public void acreditar(Cliente cliente, Proceso proceso, int cantidad) {
        acreditar(cliente, proceso, cantidad, null, null);
    }

    @Transactional
    public void acreditar(Cliente cliente, Proceso proceso, int cantidad, OrdenCompraItem item) {
        acreditar(cliente, proceso, cantidad, item, null);
    }

    /**
     * Igual, y si viene el ítem de la compra crea su lote (cantidad y valor unitario de esa compra, que no cambian después), con su vencimiento
     * (fecha de aprobación + vigencia del ítem). Es idempotente por ítem y por (transacción de pago, ítem): si el lote ya existe, no acredita nada de nuevo.
     */
    @Transactional
    public void acreditar(Cliente cliente, Proceso proceso, int cantidad, OrdenCompraItem item, String idTransaccionPago) {
        if (item != null && (loteRepository.existsByItem_IdItem(item.getIdItem())
                || (idTransaccionPago != null && loteRepository.existsByIdTransaccionPagoAndItem_IdItem(idTransaccionPago, item.getIdItem())))) {
            log.info("Lote del ítem {} ya existía: no se acredita otra vez (transacción {})", item.getIdItem(), idTransaccionPago);
            return;
        }

        LocalDateTime ahora = ahora();
        boolean nacional = item != null && CalculadoraCobertura.NACIONAL.equals(item.getCobertura());
        if (!nacional) {   // el paquete nacional (TP-08) es una bolsa aparte: vive en su lote, no en el contador por sede
            SaldoServicioCliente saldo = saldoRepository
                    .findByCliente_IdClienteAndProceso_IdProceso(cliente.getIdCliente(), proceso.getIdProceso())
                    .orElseGet(() -> SaldoServicioCliente.builder()
                            .cliente(cliente)
                            .proceso(proceso)
                            .cantidadComprada(0)
                            .cantidadConsumida(0)
                            .cantidadDisponible(0)
                            .build());

            saldo.setCantidadComprada(saldo.getCantidadComprada() + cantidad);
            saldo.setCantidadDisponible(saldo.getCantidadDisponible() + cantidad);
            saldo.setFechaActualizacion(ahora);
            saldoRepository.save(saldo);
        }

        if (item != null) {
            // Cada PAQUETE comprado es su propio lote: 1 Plan Básico + 2 Plan Inicial = un lote de 5 y dos lotes de 1, con su precio y su vencimiento.
            boolean porPaquete = item.getUnidadesPlan() != null && item.getCantidadPaquetes() != null && item.getCantidadPaquetes() > 0;
            int lotes = porPaquete ? item.getCantidadPaquetes() : 1;
            int unidadesPorLote = porPaquete ? item.getUnidadesPlan() : cantidad;
            for (int i = 0; i < lotes; i++) {
                loteRepository.save(LotePrepago.builder().cliente(cliente).proceso(proceso).ordenCompra(item.getOrdenCompra()).item(item).indicePaquete(i)
                        .cantidadComprada(unidadesPorLote).cantidadConsumida(0).valorUnitario(item.getValorUnitario()).valorOriginal(item.getValorOriginal())
                        .nombreDescuentoAplicado(item.getNombreDescuentoAplicado()).fechaCompra(ahora)
                        .cobertura(item.getCobertura() != null ? item.getCobertura() : "LOCAL")
                        .fechaVencimiento(CalculadoraVigencia.vencimiento(ahora.toLocalDate(), item.getVigenciaMeses()))
                        .idTransaccionPago(idTransaccionPago).build());
            }
        }

        log.info("Saldo acreditado: cliente={}, proceso={}, cantidad={}", cliente.getIdCliente(), proceso.getIdProceso(), cantidad);
    }
}
