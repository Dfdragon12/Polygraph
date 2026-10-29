package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.mora.MoraCliente;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.service.DiasHabilesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La mora de un cliente, sacada de sus facturas (NS-05) con la fecha de hoy en Colombia. Ver {@link MoraCliente}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoraService {

    private final FacturaPospagoRepository facturaRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final DiasHabilesService diasHabilesService;

    @Transactional(readOnly = true)
    public MoraCliente.Resultado calcular(Integer idCliente) {
        var facturas = facturaRepository.findByCliente_IdClienteOrderByFechaGeneracionDesc(idCliente).stream()
                .map(f -> new MoraCliente.Factura(f.getReferencia(), f.getFechaVencimiento(), f.getMontoTotal(), f.getEstado() == EstadoFacturaPospago.PAGADA))
                .toList();
        return MoraCliente.calcular(facturas, diasHabilesService.hoy());
    }

    /**
     * Deja la marca guardada (que ven las pantallas de administración) igual a lo calculado. Una SUSPENSIÓN decidida por el administrador no se toca:
     * es una decisión, no una consecuencia de las facturas.
     */
    @Transactional
    public void sincronizar(Integer idCliente) {
        clientePospagoRepository.findByIdCliente(idCliente).ifPresent(p -> {
            if (p.getEstadoMora() == EstadoMora.SUSPENDIDO) return;
            var mora = calcular(idCliente);
            EstadoMora nuevo = mora.enMora() ? EstadoMora.EN_MORA : EstadoMora.NORMAL;
            int dias = (int) mora.diasMora();
            if (p.getEstadoMora() != nuevo || p.getDiasMora() == null || p.getDiasMora() != dias) {
                p.setEstadoMora(nuevo);
                p.setDiasMora(dias);
                clientePospagoRepository.save(p);
                log.info("Mora sincronizada: cliente={}, estado={}, días={}", idCliente, nuevo, dias);
            }
        });
    }
}
