-- SE-03: registro de cada cambio de estado o de marca de un subproceso. Es de SOLO INSERCIÓN: un disparador impide actualizar
-- o borrar filas. Guarda servicio, subproceso y tipo, estado anterior y nuevo, marca, motivo y texto, responsable del
-- subproceso y usuario que hizo el cambio, y la fecha y hora en America/Bogota (fecha_bogota = hora de pared de Colombia;
-- fecha_hora = el mismo instante con zona).
--
-- Sin llaves foráneas a propósito: el registro no depende de que el subproceso o el usuario sigan existiendo.
-- Migración del historial: lo que se pueda de historial_asignaciones (acciones que cambian estado o marca). El estado anterior
-- de ASIGNADO/DESASIGNADO/CANCELADO no se guardaba: se reconstruye (ver la columna origen = 'MIGRADO'). La hora de
-- historial_asignaciones.fecha_evento se tomó como hora de Colombia (así se escribió mientras el servidor corría en esa zona).
-- REASIGNADO no cambia estado ni marca: no se migra. Reporte en migracion_registro_cambios_reporte.

CREATE TABLE IF NOT EXISTS registro_cambios_subproceso (
    id                      BIGSERIAL PRIMARY KEY,
    id_servicio             INTEGER      NOT NULL,
    id_servicio_subproceso  BIGINT       NOT NULL,
    id_tipo_progreso        INTEGER,
    tipo_subproceso         VARCHAR(150),
    estado_anterior         VARCHAR(20),
    estado_nuevo            VARCHAR(20)  NOT NULL,
    marca                   VARCHAR(30),
    motivo                  VARCHAR(400),
    texto_adicional         TEXT,
    id_responsable          BIGINT,
    id_usuario              BIGINT,
    fecha_hora              TIMESTAMPTZ  NOT NULL,
    fecha_bogota            TIMESTAMP    NOT NULL,
    origen                  VARCHAR(10)  NOT NULL DEFAULT 'EN_VIVO' CHECK (origen IN ('EN_VIVO', 'MIGRADO')),
    id_historial_asignacion BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_registro_cambios_historial ON registro_cambios_subproceso (id_historial_asignacion)
    WHERE id_historial_asignacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_registro_cambios_servicio ON registro_cambios_subproceso (id_servicio, fecha_hora);
CREATE INDEX IF NOT EXISTS ix_registro_cambios_subproceso ON registro_cambios_subproceso (id_servicio_subproceso, fecha_hora);
CREATE INDEX IF NOT EXISTS ix_registro_cambios_fecha ON registro_cambios_subproceso (fecha_bogota);

CREATE OR REPLACE FUNCTION registro_cambios_subproceso_solo_insercion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'registro_cambios_subproceso es de solo inserción: no se actualiza ni se borra';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_registro_cambios_subproceso_solo_insercion ON registro_cambios_subproceso;
CREATE TRIGGER trg_registro_cambios_subproceso_solo_insercion
    BEFORE UPDATE OR DELETE ON registro_cambios_subproceso
    FOR EACH ROW EXECUTE FUNCTION registro_cambios_subproceso_solo_insercion();

-- Migración del historial de asignaciones.
INSERT INTO registro_cambios_subproceso (id_servicio, id_servicio_subproceso, id_tipo_progreso, tipo_subproceso, estado_anterior,
                                         estado_nuevo, marca, motivo, texto_adicional, id_responsable, id_usuario, fecha_hora,
                                         fecha_bogota, origen, id_historial_asignacion)
SELECT ss.id_servicio, h.id_servicio_subproceso, ss.id_tipo_progreso, tp.nombre_progreso,
       CASE h.accion
           WHEN 'ASIGNADO'    THEN CASE WHEN tp.rol_responsable IN ('POLIGRAFISTA', 'VISITADOR') AND ss.id_programador_responsable IS NOT NULL
                                        THEN 'PROGRAMANDO' ELSE 'PENDIENTE' END
           WHEN 'COMPLETADO'  THEN 'EN_EJECUCION'
           WHEN 'DEVUELTO'    THEN 'FINALIZADO'
           WHEN 'PUBLICADO'   THEN 'FINALIZADO'
           WHEN 'DESASIGNADO' THEN 'EN_EJECUCION'
           WHEN 'REPROGRAMADO' THEN 'EN_EJECUCION'
           ELSE NULL                                         -- CANCELADO: no se guardaba de dónde venía
       END,
       CASE h.accion
           WHEN 'ASIGNADO'    THEN 'EN_EJECUCION'
           WHEN 'COMPLETADO'  THEN 'FINALIZADO'
           WHEN 'DEVUELTO'    THEN 'EN_EJECUCION'
           WHEN 'PUBLICADO'   THEN 'PUBLICADO'
           WHEN 'DESASIGNADO' THEN CASE WHEN tp.rol_responsable IN ('POLIGRAFISTA', 'VISITADOR') AND ss.id_programador_responsable IS NOT NULL
                                        THEN 'PROGRAMANDO' ELSE 'PENDIENTE' END
           WHEN 'REPROGRAMADO' THEN 'EN_EJECUCION'
           WHEN 'CANCELADO'   THEN 'CANCELADO'
       END,
       CASE h.accion WHEN 'DEVUELTO' THEN 'DEVUELTO' WHEN 'REPROGRAMADO' THEN 'REPROGRAMADA' WHEN 'CANCELADO' THEN 'CANCELADA' END,
       NULL, h.observaciones,
       COALESCE(h.id_usuario_nuevo, h.id_usuario_anterior), h.id_actor,
       h.fecha_evento AT TIME ZONE 'America/Bogota', h.fecha_evento, 'MIGRADO', h.id
FROM historial_asignaciones h
JOIN servicio_subprocesos ss ON ss.id = h.id_servicio_subproceso
JOIN tipos_progreso tp ON tp.id_tipo_progreso = ss.id_tipo_progreso
WHERE h.accion IN ('ASIGNADO', 'COMPLETADO', 'DEVUELTO', 'PUBLICADO', 'DESASIGNADO', 'REPROGRAMADO', 'CANCELADO')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS migracion_registro_cambios_reporte (
    accion      VARCHAR(20) PRIMARY KEY,
    en_historial INTEGER NOT NULL,
    migradas     INTEGER NOT NULL,
    observacion  VARCHAR(200),
    migrado_en   TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO migracion_registro_cambios_reporte (accion, en_historial, migradas, observacion)
SELECT h.accion, COUNT(*),
       COUNT(r.id),
       CASE h.accion
           WHEN 'REASIGNADO'   THEN 'No cambia estado ni marca: no se migra'
           WHEN 'CANCELADO'    THEN 'Estado anterior desconocido (queda vacío)'
           WHEN 'ASIGNADO'     THEN 'Estado anterior reconstruido (Pendiente o Programando)'
           WHEN 'DESASIGNADO'  THEN 'Estado nuevo reconstruido (Pendiente o Programando)'
           WHEN 'REPROGRAMADO' THEN 'Marca REPROGRAMADA; el estado no cambia'
       END
FROM historial_asignaciones h
LEFT JOIN registro_cambios_subproceso r ON r.id_historial_asignacion = h.id
GROUP BY h.accion
ON CONFLICT (accion) DO NOTHING;
