package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Mantenimiento de la cola:
 *  · Rescate: trabajos que quedaron en PROCESANDO porque el backend se reinició o murió a mitad
 *    (despliegue, OOM, kill del contenedor). Pasado el timeout de su tipo vuelven a la cola, o
 *    quedan FALLIDOS si ya no les quedan intentos.
 *  · Limpieza: el estado de cada trabajo vive en PostgreSQL, no en un Map en RAM, así que no hay
 *    fuga de memoria por uuids viejos; aun así, las filas y archivos terminados se borran pasado
 *    su tiempo de retención para que la tabla y el disco no crezcan sin límite. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaTrabajosJob {

    private final TrabajoAsincronoRepository repository;
    private final PropiedadesTrabajos propiedades;
    private final AlmacenTrabajos almacen;
    private final ColaTrabajosService cola;
    private final TransactionTemplate transaccion;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void rescatarAtascados() {
        if (!propiedades.isHabilitado()) return;
        for (TipoTrabajo tipo : TipoTrabajo.values()) {
            LocalDateTime limite = LocalDateTime.now().minusMinutes(propiedades.tipo(tipo).getTimeoutMinutos());
            try {
                transaccion.executeWithoutResult(s -> repository
                        .findByTipoAndEstadoAndFechaInicioBefore(tipo, EstadoTrabajo.PROCESANDO, limite)
                        .forEach(this::rescatar));
            } catch (Exception e) {
                log.error("No se pudieron rescatar los trabajos atascados de {}: {}", tipo, e.getMessage(), e);
            }
        }
    }

    private void rescatar(TrabajoAsincrono t) {
        t.setErrorTecnico("Interrumpido: lleva más de " + propiedades.tipo(t.getTipo()).getTimeoutMinutos()
                + " min en PROCESANDO (¿reinicio del backend?).");
        if (t.getIntentos() < t.getMaxIntentos()) {
            t.setEstado(EstadoTrabajo.EN_COLA);
            t.setDisponibleDesde(LocalDateTime.now());
            t.setFechaInicio(null);
            log.warn("Trabajo {} {} atascado — vuelve a la cola", t.getTipo(), t.getUuid());
        } else {
            t.setEstado(EstadoTrabajo.FALLIDO);
            t.setFechaFin(LocalDateTime.now());
            t.setMensajeUsuario(DespachadorTrabajos.MENSAJE_FALLA_TECNICA);
            log.error("Trabajo {} {} atascado y sin intentos — FALLIDO", t.getTipo(), t.getUuid());
        }
    }

    /** Cada hora (minuto 15): los completados ya no sirven una vez descargados, así que se van
     *  pronto (retencion-horas-completados); los fallidos se guardan más tiempo para revisarlos. */
    @Scheduled(cron = "${app.trabajos.cron-limpieza:0 15 * * * *}")
    public void limpiar() {
        if (!propiedades.isHabilitado()) return;
        int completados = borrarTerminados(EstadoTrabajo.COMPLETADO,
                LocalDateTime.now().minusHours(propiedades.getRetencionHorasCompletados()));
        int fallidos = borrarTerminados(EstadoTrabajo.FALLIDO,
                LocalDateTime.now().minusDays(propiedades.getRetencionDiasFallidos()));
        int cache = almacen.borrarViejos(AlmacenTrabajos.CACHE_INFORMES,
                Instant.now().minus(Duration.ofDays(propiedades.getRetencionDiasCache())));
        if (completados + fallidos + cache > 0) {
            log.info("Limpieza de trabajos: {} completados, {} fallidos, {} PDF de caché borrados", completados, fallidos, cache);
        }
    }

    /** Botón "Borrar completados" del panel. Respeta los últimos MINUTOS_PROTEGIDOS: un trabajo
     *  recién completado puede estar a punto de descargarse (el frontend baja el archivo justo
     *  después de verlo COMPLETADO) y borrarlo ahí le rompería la descarga a esa persona. */
    public static final int MINUTOS_PROTEGIDOS = 10;

    public int borrarCompletadosAhora() {
        int borrados = borrarTerminados(EstadoTrabajo.COMPLETADO, LocalDateTime.now().minusMinutes(MINUTOS_PROTEGIDOS));
        log.info("Borrado manual de trabajos completados: {}", borrados);
        return borrados;
    }

    /** Por lotes de 200, cada lote en su transacción: no carga miles de filas en memoria ni
     *  tiene una transacción larga bloqueando la tabla. Solo borra archivos PROPIOS del trabajo
     *  (resultados/, entradas/); los PDF de caché compartidos tienen su propia retención. */
    private int borrarTerminados(EstadoTrabajo estado, LocalDateTime limite) {
        int total = 0;
        while (true) {
            Integer borrados = transaccion.execute(s -> {
                List<TrabajoAsincrono> lote = repository.findTop200ByEstadoAndFechaFinBeforeOrderByIdAsc(estado, limite);
                for (TrabajoAsincrono t : lote) {
                    if (AlmacenTrabajos.esResultadoPropio(t.getResultadoRuta())) almacen.borrar(t.getResultadoRuta());
                    borrarEntrada(t);
                }
                repository.deleteAllInBatch(lote);
                return lote.size();
            });
            if (borrados == null || borrados == 0) return total;
            total += borrados;
        }
    }

    private void borrarEntrada(TrabajoAsincrono t) {
        try {
            if (cola.parametros(t).get("rutaEntrada") instanceof String ruta) almacen.borrar(ruta);
        } catch (Exception e) {
            log.warn("No se pudo leer la entrada del trabajo {} para borrarla: {}", t.getUuid(), e.getMessage());
        }
    }
}
