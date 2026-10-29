package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.formularios.service.DocumentoPdfConverter;
import com.polygraph.erp.modules.trabajos.config.EjecutoresTrabajos;
import com.polygraph.erp.modules.trabajos.dto.MetricasTrabajosResponse;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

/** Arma la foto del panel Admin → Trabajos en segundo plano. Todo es lectura barata: un GROUP
 *  BY sobre la tabla (con índice) y contadores que ya están en memoria. */
@Service
@RequiredArgsConstructor
public class MetricasTrabajosService {

    private static final long MB = 1024 * 1024;

    private final TrabajoAsincronoRepository repository;
    private final EjecutoresTrabajos ejecutores;
    private final DocumentoPdfConverter pdfConverter;
    private final DataSource dataSource;
    /** El único ThreadPoolTaskExecutor del contexto: el de @Async (config/AsyncConfig). Los
     *  pools de trabajos no son beans a propósito. */
    private final ThreadPoolTaskExecutor ejecutorAsync;

    @Transactional(readOnly = true)
    public MetricasTrabajosResponse obtener() {
        Map<String, Map<String, Long>> conteos = new LinkedHashMap<>();
        for (TipoTrabajo tipo : TipoTrabajo.values()) {
            Map<String, Long> porEstado = new LinkedHashMap<>();
            for (EstadoTrabajo estado : EstadoTrabajo.values()) porEstado.put(estado.name(), 0L);
            conteos.put(tipo.name(), porEstado);
        }
        for (Object[] fila : repository.contarPorTipoYEstado()) {
            conteos.get(((TipoTrabajo) fila[0]).name()).put(((EstadoTrabajo) fila[1]).name(), (Long) fila[2]);
        }

        LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
        List<MetricasTrabajosResponse.Pool> pools = new ArrayList<>();
        for (TipoTrabajo tipo : TipoTrabajo.values()) {
            EjecutoresTrabajos.EstadoPool e = ejecutores.estado(tipo);
            pools.add(new MetricasTrabajosResponse.Pool(tipo.name(), e.hilos(), e.enCurso(), 0, 0, e.rechazados()));
        }

        var executor = ejecutorAsync.getThreadPoolExecutor();
        var asincrono = new MetricasTrabajosResponse.Pool("ASYNC_GENERAL (correos, notificaciones, pagos)",
                ejecutorAsync.getMaxPoolSize(), ejecutorAsync.getActiveCount(),
                executor.getQueue().size(), executor.getQueue().size() + executor.getQueue().remainingCapacity(), 0);

        var libreOffice = new MetricasTrabajosResponse.Recurso(pdfConverter.maxConcurrentes(), pdfConverter.enUso(), pdfConverter.esperando());

        Runtime rt = Runtime.getRuntime();
        var memoria = new MetricasTrabajosResponse.Memoria((rt.totalMemory() - rt.freeMemory()) / MB, rt.maxMemory() / MB);

        return new MetricasTrabajosResponse(conteos,
                repository.countByEstadoAndFechaFinAfter(EstadoTrabajo.COMPLETADO, hace24h),
                repository.countByEstadoAndFechaFinAfter(EstadoTrabajo.FALLIDO, hace24h),
                pools, asincrono, libreOffice, conexionesBd(), memoria);
    }

    private MetricasTrabajosResponse.Recurso conexionesBd() {
        if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            return new MetricasTrabajosResponse.Recurso(hikari.getMaximumPoolSize(), pool.getActiveConnections(),
                    pool.getThreadsAwaitingConnection());
        }
        return null;
    }
}
