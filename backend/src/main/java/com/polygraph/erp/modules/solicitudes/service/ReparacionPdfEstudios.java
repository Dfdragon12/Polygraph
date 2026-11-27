package com.polygraph.erp.modules.solicitudes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * PC-07, al arrancar: V280 deja en {@code migracion_reparacion_pb_reporte} una fila {@code PDF_POR_REGENERAR} por cada PDF FINAL que nunca se armó (el del #42). Aquí cada uno se
 * reemplaza por una versión nueva que va por la cola de trabajos ({@link EmisionEstudioService#regenerarComoNuevaVersion}) y se anota {@code PDF_REGENERADO}: así corre UNA sola vez
 * por PDF, aunque el servidor se reinicie. Si la tabla no existe (otro entorno) o algo falla, no impide arrancar: se avisa en el registro y queda pendiente para el próximo arranque.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReparacionPdfEstudios {

    private final JdbcTemplate jdbc;
    private final EmisionEstudioService emision;

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            regenerarPendientes();
        } catch (RuntimeException e) {
            log.debug("Sin reparación de PDF del estudio por hacer (o no se pudo leer el reporte): {}", e.getMessage());
        }
    }

    /** Devuelve cuántos PDF se mandaron a regenerar. */
    public int regenerarPendientes() {
        List<Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT r.id_servicio, r.despues FROM migracion_reparacion_pb_reporte r WHERE r.tipo = 'PDF_POR_REGENERAR' "
                        + "AND NOT EXISTS (SELECT 1 FROM migracion_reparacion_pb_reporte x WHERE x.tipo = 'PDF_REGENERADO' AND x.id_servicio = r.id_servicio AND x.antes = r.despues)");
        int hechos = 0;
        for (Map<String, Object> fila : pendientes) {
            Integer idServicio = ((Number) fila.get("id_servicio")).intValue();
            String idDocumento = String.valueOf(fila.get("despues"));
            try {
                int version = emision.regenerarComoNuevaVersion(Long.valueOf(idDocumento));
                jdbc.update("INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, antes, despues) VALUES ('PDF_REGENERADO', ?, ?, ?)",
                        idServicio, idDocumento, version > 0 ? "versión " + version + " en la cola" : "no hacía falta");
                if (version > 0) hechos++;
            } catch (RuntimeException e) {
                log.warn("No se pudo regenerar el PDF del estudio {} (documento {}): {}", idServicio, idDocumento, e.getMessage());
            }
        }
        if (hechos > 0) log.info("Reparación PC-07: {} PDF del estudio mandados a regenerar por la cola", hechos);
        return hechos;
    }
}
