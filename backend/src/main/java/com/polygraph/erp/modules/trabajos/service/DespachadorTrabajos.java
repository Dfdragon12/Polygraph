package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.trabajos.config.EjecutoresTrabajos;
import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/** El "worker": saca trabajos de la tabla y los corre en el pool de su tipo.
 *
 *  Cómo resuelve cada riesgo:
 *  · CPU/RAM — solo toma de la BD tantos trabajos como hilos libres tenga el pool de ese tipo
 *    (backpressure): con 1.000 trabajos en cola, en memoria hay a lo sumo "hilos" de ellos; el
 *    resto espera en PostgreSQL, que es donde sobra espacio.
 *  · BD — la toma es UNA sentencia (UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED)
 *    RETURNING id) en autocommit: no retiene conexión. El procesador corre sin transacción
 *    abierta; cada paso que toca la BD abre y cierra la suya.
 *  · Aislamiento de fallas — una excepción en un trabajo solo afecta a ese trabajo: se
 *    reintenta con espera creciente o pasa a FALLIDO (la DLQ), y el hilo sigue con el siguiente.
 *  · Varias instancias — SKIP LOCKED hace que dos backends nunca tomen el mismo trabajo. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DespachadorTrabajos {

    private static final String SQL_TOMAR = """
            UPDATE trabajos_asincronos
               SET estado = 'PROCESANDO', fecha_inicio = ?, intentos = intentos + 1
             WHERE id IN (SELECT id FROM trabajos_asincronos
                           WHERE tipo = ? AND estado = 'EN_COLA' AND disponible_desde <= ?
                           ORDER BY id
                           LIMIT ?
                           FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private static final String SQL_DEVOLVER = """
            UPDATE trabajos_asincronos
               SET estado = 'EN_COLA', fecha_inicio = NULL, intentos = intentos - 1
             WHERE id = ? AND estado = 'PROCESANDO'
            """;

    private static final int MAX_ERROR_TECNICO = 4000;
    static final String MENSAJE_FALLA_TECNICA =
            "No se pudo completar el proceso por un error del sistema. Intenta de nuevo más tarde o contacta a soporte.";

    private final TrabajoAsincronoRepository repository;
    private final EjecutoresTrabajos ejecutores;
    private final ProcesadoresTrabajos procesadores;
    private final ColaTrabajosService cola;
    private final PropiedadesTrabajos propiedades;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    @Scheduled(fixedDelayString = "${app.trabajos.intervalo-sondeo-ms:2000}", initialDelay = 5000)
    public void sondear() {
        if (propiedades.isHabilitado()) despachar();
    }

    /** Recién encolado algo: se atiende ya, sin esperar al siguiente sondeo. */
    @TransactionalEventListener(fallbackExecution = true)
    public void alEncolar(ColaTrabajosService.TrabajoEncolado evento) {
        if (propiedades.isHabilitado()) despachar();
    }

    /** synchronized: el sondeo y un encolado simultáneo no calculan los hilos libres a la vez. */
    public synchronized void despachar() {
        for (TipoTrabajo tipo : TipoTrabajo.values()) {
            int libres = ejecutores.libres(tipo);
            if (libres == 0) continue;
            List<Long> ids;
            try {
                LocalDateTime ahora = LocalDateTime.now();
                ids = jdbc.queryForList(SQL_TOMAR, Long.class, ahora, tipo.name(), ahora, libres);
            } catch (Exception e) {
                log.error("No se pudo leer la cola de {}: {}", tipo, e.getMessage());
                continue;
            }
            for (Long id : ids) {
                if (!ejecutores.ejecutar(tipo, () -> ejecutar(id))) {
                    jdbc.update(SQL_DEVOLVER, id);
                }
            }
        }
    }

    /** Corre en un hilo del pool del tipo. Nunca deja escapar una excepción: todo termina en
     *  COMPLETADO, reintento o FALLIDO. */
    void ejecutar(Long id) {
        TrabajoAsincrono trabajo;
        try {
            trabajo = repository.findConUsuarioById(id).orElse(null);
        } catch (Exception e) {
            // BD caída justo ahora: queda en PROCESANDO y la limpieza lo recupera por timeout.
            log.error("No se pudo cargar el trabajo {}: {}", id, e.getMessage());
            return;
        }
        if (trabajo == null) return;
        long inicio = System.currentTimeMillis();
        log.info("Trabajo {} {} — intento {}/{}", trabajo.getTipo(), trabajo.getUuid(), trabajo.getIntentos(), trabajo.getMaxIntentos());
        try {
            ProcesadorTrabajo procesador = procesadores.de(trabajo.getTipo());
            ResultadoTrabajo resultado = procesador.procesar(trabajo, cola.parametros(trabajo), progresoDe(id));
            completar(id, resultado);
            log.info("Trabajo {} {} completado en {} ms", trabajo.getTipo(), trabajo.getUuid(), System.currentTimeMillis() - inicio);
        } catch (Exception e) {
            try {
                fallar(id, e);
            } catch (Exception e2) {
                log.error("No se pudo registrar la falla del trabajo {}: {}", id, e2.getMessage(), e2);
            }
        }
    }

    private ProcesadorTrabajo.ContextoTrabajo progresoDe(Long id) {
        return new ProcesadorTrabajo.ContextoTrabajo() {
            private int ultimo = -1;

            @Override
            public void progreso(int porcentaje) {
                int valor = Math.max(0, Math.min(99, porcentaje));
                // Solo escribe si cambió: una carga de 1.000 filas no hace 1.000 UPDATEs.
                if (valor == ultimo) return;
                ultimo = valor;
                try {
                    repository.actualizarProgreso(id, valor);
                } catch (Exception e) {
                    log.debug("No se pudo actualizar el progreso de {}: {}", id, e.getMessage());
                }
            }
        };
    }

    private void completar(Long id, ResultadoTrabajo resultado) {
        transaccion.executeWithoutResult(s -> repository.findById(id).ifPresent(t -> {
            cola.aplicarResultado(t, resultado);
            t.setEstado(EstadoTrabajo.COMPLETADO);
            t.setProgreso(100);
            t.setMensajeUsuario(null);
            t.setFechaFin(LocalDateTime.now());
        }));
    }

    /** Error del usuario (ApiException 4xx: archivo inválido, sin permiso, informe no
     *  publicado...) → FALLIDO de una, con su mensaje. Falla técnica → reintento con espera
     *  creciente mientras queden intentos; después, FALLIDO con un mensaje genérico. */
    private void fallar(Long id, Exception e) {
        boolean errorDelUsuario = e instanceof ApiException api && api.getStatus().is4xxClientError();
        transaccion.executeWithoutResult(s -> repository.findById(id).ifPresent(t -> {
            t.setErrorTecnico(resumir(e));
            boolean quedanIntentos = t.getIntentos() < t.getMaxIntentos();
            if (!errorDelUsuario && quedanIntentos) {
                long espera = (long) propiedades.tipo(t.getTipo()).getEsperaBaseSegundos() * (long) Math.pow(4, t.getIntentos() - 1);
                t.setEstado(EstadoTrabajo.EN_COLA);
                t.setDisponibleDesde(LocalDateTime.now().plusSeconds(espera));
                t.setFechaInicio(null);
                t.setMensajeUsuario("Hubo un problema; se reintentará automáticamente.");
                log.warn("Trabajo {} {} falló (intento {}/{}), se reintenta en {}s: {}",
                        t.getTipo(), t.getUuid(), t.getIntentos(), t.getMaxIntentos(), espera, e.getMessage());
            } else {
                t.setEstado(EstadoTrabajo.FALLIDO);
                t.setFechaFin(LocalDateTime.now());
                t.setMensajeUsuario(errorDelUsuario ? recortar(e.getMessage(), 500) : MENSAJE_FALLA_TECNICA);
                if (errorDelUsuario) {
                    log.info("Trabajo {} {} rechazado: {}", t.getTipo(), t.getUuid(), e.getMessage());
                } else {
                    log.error("Trabajo {} {} FALLIDO tras {} intento(s): {}", t.getTipo(), t.getUuid(), t.getIntentos(), e.getMessage(), e);
                }
            }
        }));
    }

    private static String resumir(Exception e) {
        StringBuilder sb = new StringBuilder(e.getClass().getName()).append(": ").append(e.getMessage());
        Throwable causa = e.getCause();
        while (causa != null && causa != causa.getCause()) {
            sb.append("\n  causado por ").append(causa.getClass().getName()).append(": ").append(causa.getMessage());
            causa = causa.getCause();
        }
        StackTraceElement[] pila = e.getStackTrace();
        for (int i = 0; i < Math.min(8, pila.length); i++) sb.append("\n    at ").append(pila[i]);
        return recortar(sb.toString(), MAX_ERROR_TECNICO);
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
