package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.tienda.service.CalculadoraVigencia;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
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
    private final RecargoPendienteService recargos;
    private final ParametrosTiendaService parametros;

    /** @return true si devolvió algo ahora; false si ya estaba devuelto o no hay nada que devolver (p. ej. pospago ya facturado). */
    @Transactional
    public boolean devolver(Servicio servicio) {
        if (servicio.getCobroDevueltoEn() != null || servicio.getCliente() == null) return false;
        var cliente = servicio.getCliente();
        LocalDateTime ahora = LocalDateTime.now();
        // TP-12: un cliente pospago que gastó una unidad de su bolsa prepago también la recupera (coberturaConsumo la marca)
        boolean usoBolsa = cliente.getTipoCliente() == TipoCliente.PREPAGO || servicio.getCoberturaConsumo() != null;
        if (usoBolsa && servicio.getProceso() != null) {
            var lote = servicio.getIdLotePrepago() != null ? loteRepository.findById(servicio.getIdLotePrepago()).orElse(null) : null;
            LocalDateTime ahoraBogota = LocalDateTime.now(ParametrosTiendaService.ZONA);
            // TP-09: si el lote de donde salió la unidad ya venció, la unidad vuelve en un lote de devolución de 1 unidad (mismo precio y cobertura)
            // que vence a diasExtraDevolucionVencida días; el lote vencido no se toca (esa unidad SÍ se usó).
            boolean loteVencido = lote != null && (lote.getFechaVencida() != null
                    || CalculadoraVigencia.vencido(lote.getFechaVencimiento(), ahoraBogota));
            // TP-08: una unidad del paquete nacional no tiene contador: solo vive en su lote
            boolean nacional = "NACIONAL".equals(servicio.getCoberturaConsumo());
            int idProceso = servicio.getProceso().getIdProceso();
            int filas;
            if (nacional) filas = lote != null ? 1 : 0;
            else if (loteVencido) filas = saldoRepository.devolverUnaEnLoteNuevo(cliente.getIdCliente(), idProceso, ahora);
            else filas = saldoRepository.devolverUna(cliente.getIdCliente(), idProceso, ahora);
            if (filas == 0) {
                log.warn("Cancelación de {}: no hay unidad consumida que devolver (cliente {})", servicio.getIdServicio(), cliente.getIdCliente());
                return false;
            }
            if (loteVencido) {
                int dias = parametros.entero("diasExtraDevolucionVencida");
                loteRepository.save(LotePrepago.builder().cliente(cliente).proceso(servicio.getProceso()).cobertura(lote.getCobertura())
                        .cantidadComprada(1).cantidadConsumida(0).valorUnitario(lote.getValorUnitario()).valorOriginal(lote.getValorOriginal())
                        .nombreDescuentoAplicado(lote.getNombreDescuentoAplicado()).fechaCompra(ahoraBogota)
                        .fechaVencimiento(CalculadoraVigencia.finDelDia(ahoraBogota.toLocalDate().plusDays(dias))).origen("DEVOLUCION").build());
                log.info("Lote vencido {}: la unidad vuelve en un lote de devolución que vence en {} días", lote.getIdLote(), dias);
            } else if (lote != null && lote.getCantidadConsumida() > 0) {
                lote.setCantidadConsumida(lote.getCantidadConsumida() - 1);   // vuelve a SU mismo lote, con su vencimiento original
                loteRepository.save(lote);
            }
            // Si se alcanzó a pagar el recargo por ciudad, se reversa (como crédito en la próxima compra); el que seguía pendiente se descarta.
            if (cliente.getTipoCliente() == TipoCliente.PREPAGO) recargos.reversarPorCancelacion(cliente, servicio.getCandidato(), servicio.getProceso());
            else anularCargoPospago(servicio, cliente, ahora);   // el recargo de un cliente pospago va al cupo: vuelve al cupo
            servicio.setCobroDevueltoEn(ahora);
            log.info("Unidad devuelta a la bolsa: servicio={}, cliente={}, lote={}", servicio.getIdServicio(), cliente.getIdCliente(), servicio.getIdLotePrepago());
            return true;
        }
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            return anularCargoPospago(servicio, cliente, ahora);
        }
        return false;
    }

    /** Anula el cargo de la solicitud (no se factura) y devuelve su valor al cupo. @return true si devolvió algo ahora. */
    private boolean anularCargoPospago(Servicio servicio, com.polygraph.erp.modules.clientes.entity.Cliente cliente, LocalDateTime ahora) {
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

    /** Se revirtió la cancelación: se cobra otra vez lo que se había devuelto. Sin saldo o sin cupo, no se puede revertir (409). */
    @Transactional
    public void volverACobrar(Servicio servicio) {
        if (servicio.getCobroDevueltoEn() == null || servicio.getCliente() == null) return;
        var cliente = servicio.getCliente();
        if ((cliente.getTipoCliente() == TipoCliente.PREPAGO || servicio.getCoberturaConsumo() != null) && servicio.getProceso() != null) {
            // la misma bolsa de donde salió (TP-08); sin dato, la de siempre
            var unidad = saldoService.descontar(cliente.getIdCliente(), servicio.getProceso(),
                    servicio.getCoberturaConsumo() != null ? servicio.getCoberturaConsumo() : "LOCAL");
            servicio.setIdLotePrepago(unidad.idLote());
            servicio.setCoberturaConsumo(unidad.cobertura());
            if (servicio.getCandidato() != null) recargos.registrarSiAplica(cliente, servicio.getCandidato(), servicio.getProceso(),
                    servicio.getCandidato().getIdCiudadResidencia(), "Recargo restablecido al revertir la cancelación", unidad.cobertura());
            servicio.setValorUnitarioPrepago(unidad.valorUnitario());
        }
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
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
