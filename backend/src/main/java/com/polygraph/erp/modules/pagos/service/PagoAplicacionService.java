package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.event.PagoAprobadoEvent;
import com.polygraph.erp.shared.enums.EstadoMora;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Efectos de dominio de un pago Wompi aprobado. Se ejecuta de forma asíncrona después de que
 * el commit del webhook sea visible en BD (@TransactionalEventListener AFTER_COMMIT), para que
 * el controller pueda responder 200 a Wompi sin esperar este post-procesamiento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoAplicacionService {

    private final ClientePospagoRepository clientePospagoRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alPagoAprobado(PagoAprobadoEvent evento) {
        levantarRestriccionPorDeuda(evento.idCliente());
    }

    /** Regla de negocio: un pago APPROVED levanta la restricción de acceso por deuda del cliente. */
    public void levantarRestriccionPorDeuda(Integer idCliente) {
        clientePospagoRepository.findByIdCliente(idCliente).ifPresent(this::normalizarMora);
    }

    private void normalizarMora(ClientePospago clientePospago) {
        if (clientePospago.getEstadoMora() != EstadoMora.NORMAL) {
            clientePospago.setEstadoMora(EstadoMora.NORMAL);
            clientePospago.setDiasMora(0);
            clientePospagoRepository.save(clientePospago);
            log.info("Restricción de acceso por deuda levantada para cliente {}", clientePospago.getIdCliente());
        }
    }
}
