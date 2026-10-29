package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.dto.SaldoServicioResponse;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Bolsa de servicios prepago: saldo de unidades disponibles por cliente y tipo de proceso. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaldoServicioClienteService {

    private final SaldoServicioClienteRepository saldoRepository;
    private final BolsaPrepagoService bolsa;
    private final LotePrepagoRepository loteRepository;

    /** De qué lote salió la unidad y cuánto valía (NS-02). Ambos null si ningún lote la respalda (saldo anterior a los lotes). */
    public record UnidadDescontada(Long idLote, BigDecimal valorUnitario) {}

    @Transactional(readOnly = true)
    public List<SaldoServicioResponse> obtenerSaldos(Integer idCliente) {
        return bolsa.saldoPorServicio(idCliente).servicios().stream()
                .map(s -> new SaldoServicioResponse(s.idProceso(), s.nombreProceso(), s.disponible(), s.comprado(), s.consumido()))
                .toList();
    }

    /** Total de unidades disponibles en todos los servicios (Dashboard y Gestión de cuentas): el mismo cálculo de {@link BolsaPrepagoService}. */
    @Transactional(readOnly = true)
    public int totalDisponible(Integer idCliente) {
        return bolsa.saldoPorServicio(idCliente).totalDisponible();
    }

    /** Solo valida que haya al menos 1 unidad disponible; no descuenta. */
    @Transactional(readOnly = true)
    public void validarDisponibilidad(Integer idCliente, Proceso proceso) {
        int disponible = bolsa.disponible(idCliente, proceso.getIdProceso());
        if (disponible < 1) {
            throw new ApiException(
                    "No tienes saldo disponible para '" + proceso.getNombreProceso() + "' — cómpralo en Comprar Servicios",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** Descuenta 1 unidad del saldo disponible del cliente para ese proceso, del lote más antiguo con unidades (FIFO). */
    @Transactional
    public UnidadDescontada descontar(Integer idCliente, Proceso proceso) {
        // Una sola sentencia atómica con la condición de saldo: si dos envíos llegan a la vez, el segundo ve 0 filas y no gasta una unidad que ya no existe.
        int filas = saldoRepository.descontarUna(idCliente, proceso.getIdProceso(), LocalDateTime.now());
        if (filas == 0) {
            throw new ApiException(
                    "No tienes saldo disponible para '" + proceso.getNombreProceso() + "' — cómpralo en Comprar Servicios",
                    HttpStatus.BAD_REQUEST);
        }

        LotePrepago lote = AsignadorLotes.siguiente(
                loteRepository.bloquearPorClienteYProceso(idCliente, proceso.getIdProceso())).orElse(null);
        if (lote == null) return new UnidadDescontada(null, null);
        lote.setCantidadConsumida(lote.getCantidadConsumida() + 1);
        loteRepository.save(lote);
        return new UnidadDescontada(lote.getIdLote(), lote.getValorUnitario());
    }

    /** Acredita `cantidad` unidades compradas al saldo del cliente para ese proceso (crea la fila si no existe). */
    @Transactional
    public void acreditar(Cliente cliente, Proceso proceso, int cantidad) {
        acreditar(cliente, proceso, cantidad, null);
    }

    /** Igual, y si viene el ítem de la compra crea su lote (cantidad y valor unitario de esa compra, que no cambian después). */
    @Transactional
    public void acreditar(Cliente cliente, Proceso proceso, int cantidad, OrdenCompraItem item) {
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
        saldo.setFechaActualizacion(LocalDateTime.now());
        saldoRepository.save(saldo);

        if (item != null && loteRepository.findByItem_IdItem(item.getIdItem()).isEmpty()) {
            loteRepository.save(LotePrepago.builder().cliente(cliente).proceso(proceso).ordenCompra(item.getOrdenCompra()).item(item)
                    .cantidadComprada(cantidad).cantidadConsumida(0).valorUnitario(item.getValorUnitario()).valorOriginal(item.getValorOriginal())
                    .nombreDescuentoAplicado(item.getNombreDescuentoAplicado()).fechaCompra(LocalDateTime.now()).build());
        }

        log.info("Saldo acreditado: cliente={}, proceso={}, cantidad={}", cliente.getIdCliente(), proceso.getIdProceso(), cantidad);
    }
}
