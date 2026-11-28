package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
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
    private final ParametrosTiendaService parametros;
    private final NotificacionService notificacionService;
    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public MoraCliente.Resultado calcular(Integer idCliente) {
        var facturas = facturaRepository.findByCliente_IdClienteOrderByFechaGeneracionDesc(idCliente).stream()
                .map(f -> new MoraCliente.Factura(f.getReferencia(), f.getFechaVencimiento(), f.getMontoTotal(), f.getEstado() == EstadoFacturaPospago.PAGADA))
                .toList();
        // TP-14: con la mora automática encendida hay días de gracia después del vencimiento (diasGraciaMora); apagada, la regla de siempre
        int gracia = parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA) ? parametros.entero("diasGraciaMora") : 0;
        return MoraCliente.calcular(facturas, diasHabilesService.hoy(), gracia);
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
                EstadoMora anterior = p.getEstadoMora();
                p.setEstadoMora(nuevo);
                p.setDiasMora(dias);
                clientePospagoRepository.save(p);
                log.info("Mora sincronizada: cliente={}, estado={}, días={}", idCliente, nuevo, dias);
                if (anterior != nuevo && parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)) avisarCambio(idCliente, nuevo, mora.mensaje());
            }
        });
    }

    /** TP-14: avisa al cliente y a su gestor cuando pasa a «En mora» y cuando vuelve a «Al día». */
    private void avisarCambio(Integer idCliente, EstadoMora nuevo, String detalle) {
        boolean enMora = nuevo == EstadoMora.EN_MORA;
        String titulo = enMora ? "Tu cuenta pasó a mora" : "Tu cuenta está al día";
        String mensaje = enMora ? detalle : "Registramos tu pago: tu cuenta volvió a estar al día y ya puedes solicitar servicios.";
        try {
            notificacionService.crearParaCliente(idCliente, "MORA", titulo, mensaje);
            clienteRepository.findById(idCliente).ifPresent(c -> {
                if (c.getIdGestor() == null) return;
                String nombre = c.getRazonSocial() != null ? c.getRazonSocial() : ((c.getNombre() != null ? c.getNombre() : "") + " " + (c.getApellido() != null ? c.getApellido() : "")).trim();
                notificacionService.crearParaUsuario(c.getIdGestor(), "MORA", enMora ? "Cliente en mora" : "Cliente al día",
                        nombre + (enMora ? " pasó a mora. " + detalle : " volvió a estar al día."));
            });
        } catch (Exception e) {
            log.error("No se pudo avisar el cambio de mora del cliente {} — {}", idCliente, e.getClass().getSimpleName());   // un fallo al avisar no revierte la mora
        }
    }

    /** TP-14: el proceso diario recalcula a TODOS los clientes pospago (los días de gracia hacen que alguien entre en mora sin que cambie ninguna factura). */
    @Transactional
    public int sincronizarTodos() {
        int n = 0;
        for (var p : clientePospagoRepository.findAll()) {
            try {
                sincronizar(p.getIdCliente());
                n++;
            } catch (Exception e) {
                log.error("Mora diaria: falló el cliente {} — {}", p.getIdCliente(), e.getClass().getSimpleName());
            }
        }
        return n;
    }
}
