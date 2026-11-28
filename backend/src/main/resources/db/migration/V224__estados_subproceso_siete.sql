-- SE-01: un solo modelo de estados. Los subprocesos pasan de cinco estados (PENDIENTE, ASIGNADO, COMPLETADO,
-- PUBLICADO, CANCELADO) a los siete de EstadoSubproceso: PENDIENTE, PROGRAMANDO, EN_EJECUCION, REPROGRAMADO,
-- FINALIZADO, PUBLICADO, CANCELADO.
--
-- Mapeo (la primera regla que aplique):
--   CANCELADO                                                            -> CANCELADO
--   PUBLICADO                                                            -> PUBLICADO
--   COMPLETADO                                                           -> FINALIZADO
--   PENDIENTE de un servicio CANCELADO                                   -> CANCELADO
--   ASIGNADO                                                             -> EN_EJECUCION
--   PENDIENTE de poligrafía/visita, ya encargado a un programador y sin
--   profesional asignado                                                 -> PROGRAMANDO
--   PENDIENTE (lo demás)                                                 -> PENDIENTE
-- REPROGRAMADO no se asigna a nadie: en los datos viejos "reprogramado" era del servicio completo (cambio de fecha
-- de entrega), no de una cita; ese estado nace con la no-asistencia de SE-07.
--
-- Solo transforma datos (no borra). Deja un reporte por subproceso en migracion_estados_subproceso_reporte:
-- estado anterior, estado nuevo y, si algo merece revisión, la observación. "estado_nuevo" NULL = no se pudo
-- mapear (el subproceso no se toca).

CREATE TABLE IF NOT EXISTS migracion_estados_subproceso_reporte (
    id_subproceso   INTEGER      PRIMARY KEY,
    id_servicio     INTEGER,
    estado_anterior VARCHAR(20)  NOT NULL,
    estado_nuevo    VARCHAR(20),
    observacion     VARCHAR(200),
    migrado_en      TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO migracion_estados_subproceso_reporte (id_subproceso, id_servicio, estado_anterior, estado_nuevo, observacion)
SELECT x.id, x.id_servicio, x.estado, x.nuevo,
       CASE
           WHEN x.nuevo IS NULL THEN 'Estado desconocido: no se mapeó'
           WHEN x.estado = 'ASIGNADO' AND x.servicio_estado = 'CANCELADO' THEN 'Asignado en un servicio cancelado: revisar'
           WHEN x.estado = 'ASIGNADO' AND x.rol IN ('POLIGRAFISTA', 'VISITADOR') AND x.fecha_programada IS NULL
               THEN 'Cita sin fecha programada: revisar'
       END
FROM (
    SELECT ss.id, ss.id_servicio, ss.estado, ss.fecha_programada, s.estado AS servicio_estado, tp.rol_responsable AS rol,
           CASE
               WHEN ss.estado = 'CANCELADO'  THEN 'CANCELADO'
               WHEN ss.estado = 'PUBLICADO'  THEN 'PUBLICADO'
               WHEN ss.estado = 'COMPLETADO' THEN 'FINALIZADO'
               WHEN ss.estado = 'PENDIENTE' AND s.estado = 'CANCELADO' THEN 'CANCELADO'
               WHEN ss.estado = 'ASIGNADO'   THEN 'EN_EJECUCION'
               WHEN ss.estado = 'PENDIENTE' AND tp.rol_responsable IN ('POLIGRAFISTA', 'VISITADOR')
                    AND ss.id_programador_responsable IS NOT NULL AND ss.id_usuario_asignado IS NULL THEN 'PROGRAMANDO'
               WHEN ss.estado = 'PENDIENTE'  THEN 'PENDIENTE'
           END AS nuevo
    FROM servicio_subprocesos ss
    JOIN servicios s ON s.id_servicio = ss.id_servicio
    JOIN tipos_progreso tp ON tp.id_tipo_progreso = ss.id_tipo_progreso
    WHERE ss.estado IN ('PENDIENTE', 'ASIGNADO', 'COMPLETADO', 'PUBLICADO', 'CANCELADO')
       OR ss.estado NOT IN ('PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO', 'FINALIZADO')
) x
ON CONFLICT (id_subproceso) DO NOTHING;

UPDATE servicio_subprocesos ss
SET estado = r.estado_nuevo
FROM migracion_estados_subproceso_reporte r
WHERE r.id_subproceso = ss.id
  AND r.estado_nuevo IS NOT NULL
  AND ss.estado = r.estado_anterior
  AND ss.estado <> r.estado_nuevo;

-- Solo los siete estados de ahora en adelante (NOT VALID: si quedara algún subproceso sin mapear no impide migrar,
-- pero ninguna fila nueva o modificada puede traer un estado fuera de los siete).
ALTER TABLE servicio_subprocesos DROP CONSTRAINT IF EXISTS ck_servicio_subprocesos_estado;
ALTER TABLE servicio_subprocesos ADD CONSTRAINT ck_servicio_subprocesos_estado
    CHECK (estado IN ('PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'REPROGRAMADO', 'FINALIZADO', 'PUBLICADO', 'CANCELADO')) NOT VALID;
