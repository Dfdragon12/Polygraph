package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cancelar una solicitud devuelve lo que costó (NS-04) y NO se factura. Prepago: la unidad vuelve a la bolsa y al MISMO lote de donde salió.
 * Pospago: el cargo se anula (no entra en la factura) y el cupo vuelve. Una sola vez por solicitud ({@code servicios.cobro_devuelto_en}).
 * Si se revierte la cancelación (aprobada por el administrador), {@link #volverACobrar} lo cobra de nuevo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevolucionCobroService {

    private final SaldoServicioClienteRepository saldoRepository;
    private final LotePrepagoRepository loteRepository;
    private final MovimientoCreditoPospagoRepository movimientoRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final SaldoServicioClienteService saldoService;

    /** @return true si devolvió algo ahora; false si ya estaba devuelto o no hay nada que devolver (p. ej. pospago ya facturado). */
    @Transactional
    public boolean devolver(Servicio servicio) {
        if (servicio.getCobroDevueltoEn() != null || servicio.getCliente() == null) return false;
        var cliente = servicio.getCliente();
        LocalDateTime ahora = LocalDateTime.now();
        if (cliente.getTipoCliente() == TipoCliente.PREPAGO && servicio.getProceso() != null) {
            if (saldoRepository.devolverUna(cliente.getIdCliente(), servicio.getProceso().getIdProceso(), ahora) == 0) {
                log.warn("Cancelación de {}: no hay unidad consumida que devolver (cliente {})", servicio.getIdServicio(), cliente.getIdCliente());
                return false;
            }
            if (servicio.getIdLotePrepago() != null) {
                loteRepository.findById(servicio.getIdLotePrepago()).ifPresent(l -> {
                    if (l.getCantidadConsumida() > 0) { l.setCantidadConsumida(l.getCantidadConsumida() - 1); loteRepository.save(l); }
                });
            }
            servicio.setCobroDevueltoEn(ahora);
            log.info("Unidad devuelta a la bolsa: servicio={}, cliente={}, lote={}", servicio.getIdServicio(), cliente.getIdCliente(), servicio.getIdLotePrepago());
            return true;
        }
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            var mov = movimientoRepository.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(servicio.getIdServicio()).orElse(null);
            if (mov == null) { servicio.setCobroDevueltoEn(ahora); return false; }
            if (mov.getAnuladoEn() != null) return false;
            if (mov.getFactura() != null) {
                log.warn("Cancelación de {}: el cargo ya está en la factura {}; se necesita una nota crédito", servicio.getIdServicio(), mov.getFactura().getReferencia());
                return false;
            }
            mov.setAnuladoEn(ahora);
            movimientoRepository.save(mov);
            clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).ifPresent(p -> {
                BigDecimal actual = p.getCreditoDisponible() != null ? p.getCreditoDisponible() : BigDecimal.ZERO;
                p.setCreditoDisponible(actual.add(mov.getMontoTotal()));
                clientePospagoRepository.save(p);
            });
            servicio.setCobroDevueltoEn(ahora);
            log.info("Cupo devuelto: servicio={}, cliente={}, monto={}", servicio.getIdServicio(), cliente.getIdCliente(), mov.getMontoTotal());
            return true;
        }
        return false;
    }

    /** Se revirtió la cancelación: se cobra otra vez lo que se había devuelto. Sin saldo o sin cupo, no se puede revertir (409). */
    @Transactional
    public void volverACobrar(Servicio servicio) {
        if (servicio.getCobroDevueltoEn() == null || servicio.getCliente() == null) return;
        var cliente = servicio.getCliente();
        if (cliente.getTipoCliente() == TipoCliente.PREPAGO && servicio.getProceso() != null) {
            var unidad = saldoService.descontar(cliente.getIdCliente(), servicio.getProceso());
            servicio.setIdLotePrepago(unidad.idLote());
            servicio.setValorUnitarioPrepago(unidad.valorUnitario());
        } else if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            var mov = movimientoRepository.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(servicio.getIdServicio()).orElse(null);
            if (mov != null && mov.getAnuladoEn() != null) {
                var p = clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).orElse(null);
                BigDecimal actual = p != null && p.getCreditoDisponible() != null ? p.getCreditoDisponible() : BigDecimal.ZERO;
                if (p == null || actual.compareTo(mov.getMontoTotal()) < 0) {
                    throw new ApiException("El cliente ya no tiene cupo para volver a cobrar esta solicitud; no se puede revertir la cancelación.", HttpStatus.CONFLICT);
                }
                p.setCreditoDisponible(actual.subtract(mov.getMontoTotal()));
                clientePospagoRepository.save(p);
                mov.setAnuladoEn(null);
                movimientoRepository.save(mov);
            }
        }
        servicio.setCobroDevueltoEn(null);
    }
}
