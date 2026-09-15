package com.polygraph.erp.modules.pagos.job;

import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoMora;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Como una tarjeta de crédito real: si una factura pospago no se paga antes de su fecha de
 * vencimiento, el cliente pasa a EN_MORA automáticamente (bloqueando nuevas solicitudes vía
 * SolicitudService.validarMoraPospago) — el admin puede revertirlo a mano si hace falta, como
 * ya podía hacer con estadoMora antes de este job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacturaPospagoVencimientoJob {

    private final FacturaPospagoRepository facturaPospagoRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final NotificacionService notificacionService;

    @Scheduled(cron = "0 0 6 * * *")
    public void marcarVencidas() {
        List<FacturaPospago> vencidas = facturaPospagoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoFacturaPospago.PENDIENTE, LocalDate.now());

        if (vencidas.isEmpty()) {
            log.info("Vencimiento de facturas pospago: sin facturas PENDIENTE vencidas");
            return;
        }

        log.info("Vencimiento de facturas pospago: {} factura(s) vencida(s) — marcando VENCIDA y poniendo al cliente en mora",
                vencidas.size());

        for (FacturaPospago factura : vencidas) {
            try {
                procesarUna(factura.getIdFactura());
            } catch (Exception e) {
                log.error("Vencimiento de facturas pospago: fallo procesando factura={} — {}",
                        factura.getIdFactura(), e.getClass().getSimpleName());
            }
        }
    }

    @Transactional
    public void procesarUna(Long idFactura) {
        FacturaPospago factura = facturaPospagoRepository.findById(idFactura).orElse(null);
        if (factura == null || factura.getEstado() != EstadoFacturaPospago.PENDIENTE) {
            return; // ya cambió de estado (ej. se pagó justo antes de correr el job)
        }

        factura.setEstado(EstadoFacturaPospago.VENCIDA);
        facturaPospagoRepository.save(factura);

        ClientePospago pospago = clientePospagoRepository.findByIdCliente(factura.getCliente().getIdCliente()).orElse(null);
        if (pospago != null && pospago.getEstadoMora() != EstadoMora.SUSPENDIDO) {
            pospago.setEstadoMora(EstadoMora.EN_MORA);
            pospago.setDiasMora((pospago.getDiasMora() != null ? pospago.getDiasMora() : 0));
            clientePospagoRepository.save(pospago);
        }

        log.warn("Factura pospago vencida: id={}, cliente={}, referencia={}, monto={}",
                factura.getIdFactura(), factura.getCliente().getIdCliente(), factura.getReferencia(), factura.getMontoTotal());

        // "FACTURACION": solo ADMIN_POLYGRAPH — antes usaba el tipo "CLIENTE", que también le
        // llega a GESTOR/ANALISTA_INTERNO/POLIGRAFISTA/PROGRAMADOR/VISITADOR (ver
        // NotificacionService.ROLES_POR_TIPO), roles que no manejan cartera ni mora.
        notificacionService.crearParaAdmins("FACTURACION", "Factura pospago vencida sin pagar",
                "La factura " + factura.getReferencia() + " del cliente venció sin pagarse — se puso al cliente en mora.");

        // Y al cliente dueño de la factura — antes esto no le llegaba a nadie del lado cliente.
        notificacionService.crearParaCliente(factura.getCliente().getIdCliente(), "FACTURACION",
                "Tu factura venció sin pagarse",
                "La factura " + factura.getReferencia() + " venció sin pagarse — tu cuenta quedó en mora. Ponte al día para volver a solicitar servicios.");
    }
}
