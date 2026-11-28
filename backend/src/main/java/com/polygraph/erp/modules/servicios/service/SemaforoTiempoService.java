package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.entity.SolicitudPausa;
import com.polygraph.erp.modules.servicios.entity.SolicitudReprogramacion;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudPausaRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudReprogramacionRepository;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.ImputableA;
import com.polygraph.erp.shared.estados.SemaforoEstados;
import com.polygraph.erp.shared.enums.OrigenPausa;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.service.ParametrosTiempoService;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator.EntradaTiempo;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator.Lapso;
import com.polygraph.erp.shared.service.SemaforoTiempoCalculator.ResultadoTiempo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * EL lugar donde se calcula y se guarda el estado de tiempo de una solicitud. Todo lo demás (listas, tableros, filtros, detalle) LEE lo que
 * se guardó aquí. Se ejecuta al crear, al cambiar de etapa, al abrir o cerrar una pausa, al reprogramar y al publicar, y todos los días
 * (00:05 y a la hora de corte, por la cola de trabajos) para las solicitudes en curso, porque el estado cambia solo con el paso del tiempo.
 *
 * Al publicar se congelan la fecha de entrega real, el cumplimiento y el retraso, y no se recalculan más (salvo que la solicitud salga
 * de PUBLICADO por una reversión: entonces se borran y vuelve a ser una solicitud abierta).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemaforoTiempoService {

    public static final String MOTIVO_HOJA_DE_VIDA = "Espera la hoja de vida del candidato";
    public static final String MOTIVO_REPROGRAMACION = "Cita reprogramada";
    static final int TAMANO_LOTE = 200;

    private final ServicioRepository servicioRepository;
    private final SolicitudPausaRepository pausaRepository;
    private final SolicitudReprogramacionRepository reprogramacionRepository;
    private final CalendarioHabilService calendario;
    private final ParametrosTiempoService parametros;
    private final PlatformTransactionManager transacciones;
    private final NotificacionesTiempoService avisos;
    private final ServicioSubprocesoRepository subprocesoRepository;

    /** procesadas: solicitudes revisadas; cambiadas: las que cambiaron de estado de tiempo o de días; errores: una línea por solicitud que falló. */
    public record Resumen(int procesadas, int cambiadas, List<String> errores) {}

    // ───────────────────────── recalcular una solicitud ─────────────────────────

    /** Calcula y guarda el estado de tiempo de la solicitud (dentro de la transacción de quien llama). Devuelve el resultado. */
    public ResultadoTiempo recalcular(Servicio s) {
        // Una solicitud se ENTREGA cuando el estudio queda completo publicado (todos sus subprocesos no cancelados), o cuando el gestor
        // la publica. Mientras haya subprocesos en FINALIZADO (o en cualquier otro estado) el reloj sigue corriendo.
        List<ServicioSubproceso> subprocesos = subprocesosDe(s);
        boolean completoPublicado = s.getEstado() != EstadoServicio.CANCELADO && estudioCompletoPublicado(subprocesos);
        boolean entregada = s.getEstado() == EstadoServicio.PUBLICADO || completoPublicado;
        if (entregada && s.getCumplimiento() != null && s.getFechaEntregaReal() != null) {
            return new ResultadoTiempo(s.getEstadoTiempo(), 0, s.getDiasRetrasoHabiles() == null ? 0 : s.getDiasRetrasoHabiles(),
                    s.getFechaCompromisoAjustada(), s.getDiasPausaHabiles() == null ? 0 : s.getDiasPausaHabiles());
        }
        ZonedDateTimeHolder ahora = new ZonedDateTimeHolder(calendario.ahora());
        if (entregada) {
            if (s.getFechaEntregaReal() == null) s.setFechaEntregaReal(completoPublicado ? fechaUltimaPublicacion(subprocesos, ahora.hoy) : ahora.hoy);
        } else if (s.getFechaEntregaReal() != null || s.getCumplimiento() != null) {
            s.setFechaEntregaReal(null);       // salió de PUBLICADO (reversión): vuelve a ser una solicitud abierta
            s.setCumplimiento(null);
        }

        EstadoTiempo estadoAnterior = s.getEstadoTiempo();
        List<SolicitudPausa> pausas = s.getIdServicio() == null ? List.of() : pausaRepository.findByServicio_IdServicioOrderByInicio(s.getIdServicio());
        ResultadoTiempo r = calcular(s, pausas, ahora, entregada);
        s.setEstadoTiempo(r.estado());
        s.setDiasRestantesHabiles(r.estado() == null ? null : r.diasRestantesHabiles());
        s.setDiasRetrasoHabiles(r.estado() == null ? null : r.diasRetrasoHabiles());
        s.setFechaCompromisoAjustada(r.fechaCompromisoAjustada());
        s.setDiasPausaHabiles(r.diasPausaHabiles());
        s.setPausaOrigen(r.estado() == EstadoTiempo.EN_PAUSA
                ? pausas.stream().filter(SolicitudPausa::abierta).map(SolicitudPausa::getOrigen).findFirst().orElse(null) : null);
        if (entregada) {
            s.setCumplimiento(r.estado() == EstadoTiempo.ENTREGADA_TARDE ? Cumplimiento.TARDE : Cumplimiento.A_TIEMPO);
        }
        s.setEstadoTiempoActualizadoEn(ahora.ahora);
        // Un aviso por transición: solo si el estado cambió (y no en el primer cálculo). El recálculo de cada noche no cambia nada, no repite nada.
        avisos.alCambiarEstadoTiempo(s, estadoAnterior, r.estado());
        return r;
    }

    /** El cálculo puro con los datos de la solicitud y sus pausas (las reprogramaciones salen de la base). No escribe nada. */
    ResultadoTiempo calcular(Servicio s, List<SolicitudPausa> pausasGuardadas, ZonedDateTimeHolder ahora) {
        return calcular(s, pausasGuardadas, ahora, s.getEstado() == EstadoServicio.PUBLICADO);
    }

    /** entregada: el estudio está publicado (por el servicio o porque todos sus subprocesos no cancelados lo están): se mide la entrega. */
    ResultadoTiempo calcular(Servicio s, List<SolicitudPausa> pausasGuardadas, ZonedDateTimeHolder ahora, boolean entregada) {
        List<Lapso> pausas = new ArrayList<>();
        ImputableA ultima = null;
        for (SolicitudPausa p : pausasGuardadas) {
            pausas.add(new Lapso(p.getInicio().toLocalDate(), p.getFin() == null ? null : p.getFin().toLocalDate()));
        }
        if (s.getIdServicio() != null) {
            List<SolicitudReprogramacion> reprogramaciones = reprogramacionRepository.findByServicio_IdServicioOrderByFechaDesc(s.getIdServicio());
            if (!reprogramaciones.isEmpty()) ultima = reprogramaciones.get(0).getResponsable();
        }
        var calculador = new SemaforoTiempoCalculator(calendario, parametros.actuales());
        return calculador.calcular(new EntradaTiempo(entregada ? EstadoServicio.PUBLICADO : s.getEstado(), s.getFechaEntregaEstimada(), s.getFechaCompromisoOriginal(), ultima, pausas,
                s.getFechaEntregaReal()), ahora.zoned);
    }

    /** "Ahora" tomado una sola vez por cálculo (así la fecha de entrega real y el estado salen del mismo instante). */
    static final class ZonedDateTimeHolder {
        final java.time.ZonedDateTime zoned;
        final java.time.LocalDate hoy;
        final LocalDateTime ahora;

        ZonedDateTimeHolder(java.time.ZonedDateTime zoned) {
            this.zoned = zoned;
            this.hoy = zoned.toLocalDate();
            this.ahora = zoned.toLocalDateTime();
        }

    }

    // ───────────────────────── entrega del estudio ─────────────────────────

    private List<ServicioSubproceso> subprocesosDe(Servicio s) {
        return s.getIdServicio() == null ? List.of() : subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(s.getIdServicio());
    }

    /** «Estudio completo publicado»: hay subprocesos no cancelados y TODOS están publicados. */
    static boolean estudioCompletoPublicado(List<ServicioSubproceso> subprocesos) {
        return SemaforoEstados.etiqueta(SemaforoEstados.avance(subprocesos.stream().map(ServicioSubproceso::getEstado).toList()))
                == SemaforoEstados.EtiquetaPublicacion.ESTUDIO_COMPLETO_PUBLICADO;
    }

    /** El día en que se publicó el último subproceso (hora de Colombia); si ninguno trae fecha, el día de respaldo. */
    static java.time.LocalDate fechaUltimaPublicacion(List<ServicioSubproceso> subprocesos, java.time.LocalDate respaldo) {
        return subprocesos.stream().filter(sp -> sp.getEstado() == EstadoSubproceso.PUBLICADO && sp.getFechaPublicado() != null)
                .map(sp -> sp.getFechaPublicado().toLocalDate()).max(java.util.Comparator.naturalOrder()).orElse(respaldo);
    }

    // ───────────────────────── pausa por reprogramación ─────────────────────────

    /**
     * Mientras algún subproceso del servicio esté en REPROGRAMADO el reloj del servicio se pausa, sea cual sea el motivo; al dejar de haber
     * alguno, la pausa se cierra y la fecha de compromiso se corre los días hábiles que duró. Idempotente: se puede llamar tras cualquier
     * cambio de estado de un subproceso.
     */
    public void sincronizarPausaPorReprogramacion(Servicio s) {
        if (s.getIdServicio() == null) return;
        boolean hayReprogramado = subprocesosDe(s).stream().anyMatch(sp -> sp.getEstado() == EstadoSubproceso.REPROGRAMADO);
        if (hayReprogramado) abrirPausa(s, OrigenPausa.REPROGRAMACION, MOTIVO_REPROGRAMACION, "sistema", null);
        else cerrarPausas(s, MOTIVO_REPROGRAMACION);
    }

    // ───────────────────────── pausas ─────────────────────────

    /** Abre una pausa (el reloj deja de correr). Si ya hay una abierta con el mismo motivo, no abre otra. Recalcula. */
    public SolicitudPausa abrirPausa(Servicio s, OrigenPausa origen, String motivo, String usuario, LocalDateTime inicio) {
        SolicitudPausa existente = pausaRepository.findByServicio_IdServicioOrderByInicio(s.getIdServicio()).stream()
                .filter(p -> p.abierta() && motivo.equals(p.getMotivo())).findFirst().orElse(null);
        if (existente != null) return existente;
        SolicitudPausa pausa = pausaRepository.saveAndFlush(SolicitudPausa.builder().servicio(s)
                .inicio(inicio != null ? inicio : calendario.ahora().toLocalDateTime()).motivo(motivo).origen(origen).creadoPor(usuario).build());
        recalcular(s);
        avisos.alAbrirPausa(s, pausa);
        log.info("Solicitud {}: pausa abierta ({}) por {}", s.getIdServicio(), motivo, origen);
        return pausa;
    }

    /** Cierra las pausas abiertas con ese motivo (null = todas). Recalcula. Devuelve cuántas cerró. */
    public int cerrarPausas(Servicio s, String motivo) {
        LocalDateTime ahora = calendario.ahora().toLocalDateTime();
        int cerradas = 0;
        List<SolicitudPausa> cerradasAhora = new ArrayList<>();
        for (SolicitudPausa p : pausaRepository.findByServicio_IdServicioOrderByInicio(s.getIdServicio())) {
            if (p.abierta() && (motivo == null || motivo.equals(p.getMotivo()))) {
                p.setFin(ahora);
                pausaRepository.save(p);
                cerradasAhora.add(p);
                cerradas++;
            }
        }
        if (cerradas > 0) {
            pausaRepository.flush();
            recalcular(s);
            cerradasAhora.forEach(p -> avisos.alCerrarPausa(s, p));
            log.info("Solicitud {}: {} pausa(s) cerrada(s)", s.getIdServicio(), cerradas);
        }
        return cerradas;
    }

    // ───────────────────────── recálculo diario ─────────────────────────

    /** Recalcula todas las solicitudes en curso, por lotes. Idempotente: se puede repetir sin efectos. Cada lote es una transacción; si un lote
     *  falla se rehace fila por fila para saber cuáles fallaron. */
    public Resumen recalcularEnCurso() {
        List<Integer> ids = servicioRepository.idsAbiertos(EstadoServicio.CERRADOS);
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        int cambiadas = 0;
        List<String> errores = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += TAMANO_LOTE) {
            List<Integer> lote = ids.subList(i, Math.min(ids.size(), i + TAMANO_LOTE));
            try {
                Integer n = tx.execute(st -> recalcularLote(lote));
                cambiadas += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.warn("Lote de recálculo de tiempo falló ({}); se rehace fila por fila", e.getMessage());
                for (Integer id : lote) {
                    try {
                        Integer n = tx.execute(st -> recalcularLote(List.of(id)));
                        cambiadas += n == null ? 0 : n;
                    } catch (RuntimeException ex) {
                        errores.add("Solicitud " + id + ": " + ex.getMessage());
                        log.error("No se pudo recalcular el tiempo de la solicitud {}: {}", id, ex.getMessage(), ex);
                    }
                }
            }
        }
        log.info("Recálculo de tiempo: {} solicitudes, {} cambiaron, {} errores", ids.size(), cambiadas, errores.size());
        return new Resumen(ids.size(), cambiadas, errores);
    }

    private int recalcularLote(List<Integer> ids) {
        int cambiadas = 0;
        for (Servicio s : servicioRepository.findAllById(ids)) {
            EstadoTiempo antes = s.getEstadoTiempo();
            Integer retrasoAntes = s.getDiasRetrasoHabiles();
            Integer restantesAntes = s.getDiasRestantesHabiles();
            recalcular(s);
            if (antes != s.getEstadoTiempo() || !java.util.Objects.equals(retrasoAntes, s.getDiasRetrasoHabiles())
                    || !java.util.Objects.equals(restantesAntes, s.getDiasRestantesHabiles())) cambiadas++;
        }
        return cambiadas;
    }
}
