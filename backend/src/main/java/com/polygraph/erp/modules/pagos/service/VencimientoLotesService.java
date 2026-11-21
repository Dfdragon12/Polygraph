package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.tienda.service.CalculadoraVigencia;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TP-07: vencimiento de lotes. Todo esto solo actúa con el interruptor {@code vigenciaPorPlan} encendido.
 * <ul>
 *   <li>Vence: las unidades no usadas de un lote cuyo día de vencimiento ya pasó pasan a «vencidas» (no se pueden usar y se reportan aparte).</li>
 *   <li>Alerta: el cliente ve (Dashboard) y recibe una notificación de los lotes que vencen dentro de {@code diasAlertaVencimiento} días.</li>
 *   <li>Reporte: ingreso por vencimiento = unidades vencidas × precio unitario del lote, para finanzas.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VencimientoLotesService {

    public static final String DIAS_ALERTA = "diasAlertaVencimiento";

    private final LotePrepagoRepository loteRepository;
    private final SaldoServicioClienteService saldoService;
    private final ParametrosTiendaService parametros;
    private final NotificacionService notificacionService;
    private final Clock clock;

    /** Un lote del cliente cuyas unidades vencen pronto. */
    public record LotePorVencer(Long idLote, Integer idProceso, String nombreProceso, int unidades, LocalDate fechaVencimiento, long diasRestantes) {}

    /** Una fila del reporte de unidades vencidas (ingreso por vencimiento). */
    public record FilaVencimiento(Long idLote, Integer idCliente, Integer idProceso, String nombreProceso, int unidadesVencidas,
                                  BigDecimal valorUnitario, BigDecimal montoVencido, LocalDateTime fechaVencida, String origen) {}

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA));
    }

    private boolean activa() {
        return parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN);
    }

    /** Lo que corre el job: vence lo que ya venció y avisa lo que está por vencer. @return unidades vencidas ahora. */
    public int ejecutar() {
        if (!activa()) return 0;
        LocalDateTime ahora = ahora();
        int unidades = 0;
        Set<String> pares = new LinkedHashSet<>();
        for (LotePrepago l : loteRepository.pendientesDeVencer(ahora)) {
            pares.add(l.getCliente().getIdCliente() + ":" + l.getProceso().getIdProceso());
        }
        for (String par : pares) {
            String[] p = par.split(":");
            try {
                unidades += vencerDe(Integer.parseInt(p[0]), Integer.parseInt(p[1]), ahora);
            } catch (Exception e) {
                log.error("Vencimiento de lotes: falló cliente={} proceso={} — {}", p[0], p[1], e.getClass().getSimpleName());
            }
        }
        try {
            avisar(ahora);
        } catch (Exception e) {
            log.error("Aviso de lotes por vencer falló — {}", e.getClass().getSimpleName());
        }
        return unidades;
    }

    /** Bloquea los lotes del cliente y servicio y vence los que ya pasaron. */
    @Transactional
    public int vencerDe(Integer idCliente, Integer idProceso, LocalDateTime ahora) {
        int unidades = 0;
        for (LotePrepago l : loteRepository.bloquearPorClienteYProceso(idCliente, idProceso)) {
            if (l.getFechaVencida() == null && CalculadoraVigencia.vencido(l.getFechaVencimiento(), ahora)) {
                unidades += saldoService.vencer(l, ahora);
            }
        }
        return unidades;
    }

    /** Una notificación por lote que entra en la ventana de alerta (una sola vez por lote). */
    @Transactional
    public int avisar(LocalDateTime ahora) {
        int dias = parametros.entero(DIAS_ALERTA);
        int avisados = 0;
        for (LotePrepago l : loteRepository.porAvisar(ahora, ahora.plusDays(dias))) {
            long restantes = diasRestantes(ahora, l.getFechaVencimiento());
            notificacionService.crearParaCliente(l.getCliente().getIdCliente(), "LOTE_POR_VENCER", "Unidades por vencer",
                    "Tienes " + l.restante() + " unidad(es) de " + l.getProceso().getNombreProceso() + " que vencen el "
                            + l.getFechaVencimiento().toLocalDate() + " (" + (restantes == 0 ? "hoy" : "en " + restantes + " día(s)")
                            + "). Úsalas antes de esa fecha.");
            l.setAvisoVencimientoEn(ahora);
            loteRepository.save(l);
            avisados++;
        }
        return avisados;
    }

    /** Lotes del cliente con unidades que vencen dentro de la ventana de alerta, para el Dashboard. Vacío con el interruptor apagado. */
    @Transactional(readOnly = true)
    public List<LotePorVencer> lotesPorVencer(Integer idCliente) {
        if (!activa()) return List.of();
        LocalDateTime ahora = ahora();
        return loteRepository.porVencerDelCliente(idCliente, ahora, ahora.plusDays(parametros.entero(DIAS_ALERTA))).stream()
                .map(l -> new LotePorVencer(l.getIdLote(), l.getProceso().getIdProceso(), l.getProceso().getNombreProceso(), l.restante(),
                        l.getFechaVencimiento().toLocalDate(), diasRestantes(ahora, l.getFechaVencimiento())))
                .toList();
    }

    /** Ingreso por vencimiento entre dos fechas (inclusive), para finanzas. */
    @Transactional(readOnly = true)
    public List<FilaVencimiento> reporte(LocalDate desde, LocalDate hasta) {
        return loteRepository.vencidosEntre(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()).stream()
                .map(l -> new FilaVencimiento(l.getIdLote(), l.getCliente().getIdCliente(), l.getProceso().getIdProceso(),
                        l.getProceso().getNombreProceso(), l.getCantidadVencida(), l.getValorUnitario(),
                        l.getValorUnitario().multiply(BigDecimal.valueOf(l.getCantidadVencida())), l.getFechaVencida(), l.getOrigen()))
                .toList();
    }

    static long diasRestantes(LocalDateTime ahora, LocalDateTime vencimiento) {
        return Math.max(0, ChronoUnit.DAYS.between(ahora.toLocalDate(), vencimiento.toLocalDate()));
    }
}
