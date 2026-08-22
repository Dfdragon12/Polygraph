package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.dto.SaldoServicioResponse;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Bolsa de servicios prepago: saldo de unidades disponibles por cliente y tipo de proceso. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaldoServicioClienteService {

    private final SaldoServicioClienteRepository saldoRepository;

    @Transactional(readOnly = true)
    public List<SaldoServicioResponse> obtenerSaldos(Integer idCliente) {
        return saldoRepository.findByCliente_IdCliente(idCliente).stream()
                .filter(s -> s.getCantidadDisponible() > 0 || s.getCantidadComprada() > 0)
                .map(s -> new SaldoServicioResponse(
                        s.getProceso().getIdProceso(),
                        s.getProceso().getNombreProceso(),
                        s.getCantidadDisponible(),
                        s.getCantidadComprada(),
                        s.getCantidadConsumida()))
                .toList();
    }

    /** Solo valida que haya al menos 1 unidad disponible; no descuenta. */
    @Transactional(readOnly = true)
    public void validarDisponibilidad(Integer idCliente, Proceso proceso) {
        int disponible = saldoRepository.findByCliente_IdClienteAndProceso_IdProceso(idCliente, proceso.getIdProceso())
                .map(SaldoServicioCliente::getCantidadDisponible)
                .orElse(0);
        if (disponible < 1) {
            throw new ApiException(
                    "No tienes saldo disponible para '" + proceso.getNombreProceso() + "' — cómpralo en Comprar Servicios",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** Descuenta 1 unidad del saldo disponible del cliente para ese proceso. */
    @Transactional
    public void descontar(Integer idCliente, Proceso proceso) {
        SaldoServicioCliente saldo = saldoRepository
                .findByCliente_IdClienteAndProceso_IdProceso(idCliente, proceso.getIdProceso())
                .orElseThrow(() -> new ApiException(
                        "No tienes saldo disponible para '" + proceso.getNombreProceso() + "' — cómpralo en Comprar Servicios",
                        HttpStatus.BAD_REQUEST));

        if (saldo.getCantidadDisponible() < 1) {
            throw new ApiException(
                    "No tienes saldo disponible para '" + proceso.getNombreProceso() + "' — cómpralo en Comprar Servicios",
                    HttpStatus.BAD_REQUEST);
        }

        saldo.setCantidadDisponible(saldo.getCantidadDisponible() - 1);
        saldo.setCantidadConsumida(saldo.getCantidadConsumida() + 1);
        saldo.setFechaActualizacion(LocalDateTime.now());
        saldoRepository.save(saldo);
    }

    /** Acredita `cantidad` unidades compradas al saldo del cliente para ese proceso (crea la fila si no existe). */
    @Transactional
    public void acreditar(Cliente cliente, Proceso proceso, int cantidad) {
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

        log.info("Saldo acreditado: cliente={}, proceso={}, cantidad={}", cliente.getIdCliente(), proceso.getIdProceso(), cantidad);
    }
}
