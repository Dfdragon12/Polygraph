package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UN solo cálculo del saldo de la bolsa prepago (NS-01). Dashboard, Comprar servicios, Gestión de cuentas y Nueva solicitud leen de aquí.
 * <pre>disponible = comprado (compras pagadas) − consumido − reservado</pre>
 * Una compra que no está APROBADA (pendiente, rechazada, expirada) no suma: {@code SaldoServicioClienteService.acreditar} solo se llama al aprobarla.
 * «Reservado» todavía no tiene un flujo que lo use: vale 0 hasta que exista (queda el campo para no cambiar las pantallas después).
 */
@Service
@RequiredArgsConstructor
public class BolsaPrepagoService {

    private final SaldoServicioClienteRepository saldoRepository;
    private final OrdenCompraItemRepository itemRepository;

    public record SaldoServicio(Integer idProceso, String nombreProceso, int comprado, int consumido, int reservado, int disponible) {}

    public record Bolsa(Integer idCliente, List<SaldoServicio> servicios, int totalComprado, int totalConsumido, int totalReservado, int totalDisponible) {}

    /** Una fila por servicio que el cliente haya comprado alguna vez, también los agotados (disponible 0). */
    @Transactional(readOnly = true)
    public Bolsa saldoPorServicio(Integer idCliente) {
        List<SaldoServicio> filas = saldoRepository.findByCliente_IdCliente(idCliente).stream()
                .filter(s -> orCero(s.getCantidadComprada()) > 0 || orCero(s.getCantidadDisponible()) > 0)
                .map(BolsaPrepagoService::fila)
                .toList();
        return totales(idCliente, filas);
    }

    @Transactional(readOnly = true)
    public int disponible(Integer idCliente, Integer idProceso) {
        return saldoRepository.findByCliente_IdClienteAndProceso_IdProceso(idCliente, idProceso).map(s -> fila(s).disponible()).orElse(0);
    }

    // ───────────────────────── cálculo puro ─────────────────────────

    static SaldoServicio fila(SaldoServicioCliente s) {
        int comprado = orCero(s.getCantidadComprada()), consumido = orCero(s.getCantidadConsumida());
        return new SaldoServicio(s.getProceso().getIdProceso(), s.getProceso().getNombreProceso(), comprado, consumido, 0,
                calcularDisponible(comprado, consumido, 0));
    }

    public static int calcularDisponible(int comprado, int consumido, int reservado) {
        return comprado - consumido - reservado;
    }

    static Bolsa totales(Integer idCliente, List<SaldoServicio> filas) {
        return new Bolsa(idCliente, filas, filas.stream().mapToInt(SaldoServicio::comprado).sum(), filas.stream().mapToInt(SaldoServicio::consumido).sum(),
                filas.stream().mapToInt(SaldoServicio::reservado).sum(), filas.stream().mapToInt(SaldoServicio::disponible).sum());
    }

    private static int orCero(Integer n) { return n == null ? 0 : n; }

    // ───────────────────────── reporte previo a aplicar ─────────────────────────

    /** Un servicio de un cliente cuyo saldo guardado no coincide con lo que dicen las compras APROBADAS. */
    public record Diferencia(Integer idCliente, Integer idProceso, String nombreProceso, int compradoGuardado, int compradoSegunCompras,
                             int disponibleGuardado, int disponibleCalculado) {}

    /**
     * Solo LEE y no corrige nada: compara lo guardado con la suma de las compras aprobadas. Una diferencia no siempre es un error (una nota crédito
     * total quita unidades de lo guardado), por eso es un reporte para revisar a mano antes de tocar saldos.
     */
    @Transactional(readOnly = true)
    public List<Diferencia> diferenciasConComprasAprobadas() {
        Map<String, Integer> aprobadas = new HashMap<>();
        for (Object[] f : itemRepository.sumarCantidadPorClienteYProceso(EstadoOrdenCompra.APROBADA)) {
            aprobadas.put(f[0] + ":" + f[1], ((Number) f[2]).intValue());
        }
        List<Diferencia> r = new ArrayList<>();
        for (SaldoServicioCliente s : saldoRepository.findAll()) {
            int segunCompras = aprobadas.getOrDefault(s.getCliente().getIdCliente() + ":" + s.getProceso().getIdProceso(), 0);
            SaldoServicio calculado = fila(s);
            if (segunCompras != calculado.comprado() || orCero(s.getCantidadDisponible()) != calculado.disponible()) {
                r.add(new Diferencia(s.getCliente().getIdCliente(), s.getProceso().getIdProceso(), s.getProceso().getNombreProceso(),
                        calculado.comprado(), segunCompras, orCero(s.getCantidadDisponible()), calculado.disponible()));
            }
        }
        return r;
    }
}
