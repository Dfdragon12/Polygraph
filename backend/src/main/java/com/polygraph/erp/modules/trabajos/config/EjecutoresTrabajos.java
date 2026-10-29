package com.polygraph.erp.modules.trabajos.config;

import com.polygraph.erp.shared.enums.TipoTrabajo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Un pool de hilos FIJO y aislado por tipo de trabajo (patrón bulkhead): cinco cargas masivas
 *  no pueden dejar sin hilos a los PDF, y viceversa. Protege CPU (hilos = núcleos que se usan a
 *  lo sumo) y RAM (nunca hay más trabajos en memoria que hilos).
 *
 *  La cola "de verdad" es la tabla en PostgreSQL: el despachador solo toma de la BD tantos
 *  trabajos como hilos libres haya (ver libres()), así que la cola en memoria del pool nunca se
 *  llena. Aun así es acotada y con AbortPolicy: si por una carrera llega uno de más, se rechaza
 *  (el despachador lo devuelve a EN_COLA) en vez de acumularse en RAM.
 *
 *  No son beans de Spring a propósito: si lo fueran, @Async podría resolver uno de estos como
 *  su ejecutor por defecto (ver config/AsyncConfig). */
@Slf4j
@Component
@EnableConfigurationProperties(PropiedadesTrabajos.class)
public class EjecutoresTrabajos {

    private final Map<TipoTrabajo, ThreadPoolTaskExecutor> pools = new EnumMap<>(TipoTrabajo.class);
    private final Map<TipoTrabajo, AtomicInteger> enCurso = new EnumMap<>(TipoTrabajo.class);
    private final Map<TipoTrabajo, AtomicLong> rechazados = new EnumMap<>(TipoTrabajo.class);
    private final PropiedadesTrabajos propiedades;

    public EjecutoresTrabajos(PropiedadesTrabajos propiedades) {
        this.propiedades = propiedades;
        for (TipoTrabajo tipo : TipoTrabajo.values()) {
            int hilos = Math.max(1, propiedades.tipo(tipo).getHilos());
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setThreadNamePrefix("trabajo-" + tipo.name().toLowerCase().replace('_', '-') + "-");
            pool.setCorePoolSize(hilos);
            pool.setMaxPoolSize(hilos);
            pool.setQueueCapacity(hilos);
            pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
            // Al apagar (deploy) se deja terminar lo que está corriendo; lo que no alcance queda
            // en PROCESANDO y LimpiezaTrabajosJob lo recupera por timeout.
            pool.setWaitForTasksToCompleteOnShutdown(true);
            pool.setAwaitTerminationSeconds(30);
            pool.initialize();
            pools.put(tipo, pool);
            enCurso.put(tipo, new AtomicInteger());
            rechazados.put(tipo, new AtomicLong());
            log.info("Pool de trabajos {}: {} hilo(s)", tipo, hilos);
        }
    }

    /** Hilos libres de este tipo — cuántos trabajos puede tomar el despachador ahora. */
    public int libres(TipoTrabajo tipo) {
        return Math.max(0, pools.get(tipo).getCorePoolSize() - enCurso.get(tipo).get());
    }

    /** false si el pool lo rechazó (el llamador debe devolver el trabajo a la cola). */
    public boolean ejecutar(TipoTrabajo tipo, Runnable tarea) {
        AtomicInteger contador = enCurso.get(tipo);
        contador.incrementAndGet();
        try {
            pools.get(tipo).execute(() -> {
                try {
                    tarea.run();
                } finally {
                    contador.decrementAndGet();
                }
            });
            return true;
        } catch (TaskRejectedException e) {
            contador.decrementAndGet();
            rechazados.get(tipo).incrementAndGet();
            log.warn("Pool de trabajos {} lleno — el trabajo vuelve a la cola", tipo);
            return false;
        }
    }

    public record EstadoPool(TipoTrabajo tipo, int hilos, int enCurso, long rechazados) {}

    public EstadoPool estado(TipoTrabajo tipo) {
        return new EstadoPool(tipo, pools.get(tipo).getCorePoolSize(), enCurso.get(tipo).get(), rechazados.get(tipo).get());
    }

    public PropiedadesTrabajos propiedades() {
        return propiedades;
    }

    @PreDestroy
    public void apagar() {
        pools.values().forEach(ThreadPoolTaskExecutor::shutdown);
    }
}
