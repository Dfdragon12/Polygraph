-- SE-02: marcas del subproceso, dato "iniciado", desistimiento del candidato y lista de motivos configurable.
--
-- 1) servicio_subprocesos.iniciado / fecha_iniciado: el trabajo ya empezó. Se activa con la primera de estas acciones: el
--    responsable guarda avance o adjunto, la cita se confirma como realizada, o el subproceso pasa a FINALIZADO o PUBLICADO.
--    Un subproceso iniciado ya no se cancela (si el candidato desiste, los iniciados siguen hasta PUBLICADO).
-- 2) Marcas (no son estados): devuelto, reprogramaciones (contador de veces que se reprogramó la cita). Las marcas
--    ASIGNADA_NO_REALIZADA y CANCELADA van al historial; CANDIDATO_DESISTIO es del servicio.
-- 3) motivos_subproceso: listas de motivos que administra el admin (REPROGRAMACION, CANCELACION, DEVOLUCION).
--    "requiere_texto" = el motivo "Otro": exige que quien actúa escriba cuál.
-- Idempotente: solo agrega y rellena datos.

ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS iniciado       BOOLEAN   NOT NULL DEFAULT FALSE;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS fecha_iniciado TIMESTAMP;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS devuelto       BOOLEAN   NOT NULL DEFAULT FALSE;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS reprogramaciones INTEGER NOT NULL DEFAULT 0;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS candidato_desistio BOOLEAN NOT NULL DEFAULT FALSE;

-- Lo que ya terminó, o ya tiene avance guardado, cuenta como iniciado.
UPDATE servicio_subprocesos
SET iniciado = TRUE,
    fecha_iniciado = COALESCE(fecha_completado, fecha_publicado, fecha_asignacion, fecha_creacion)
WHERE iniciado = FALSE
  AND (estado IN ('FINALIZADO', 'PUBLICADO')
       OR EXISTS (SELECT 1 FROM formularios f JOIN formulario_datos d ON d.id_formulario = f.id_formulario
                  WHERE f.id_servicio_subproceso = servicio_subprocesos.id));

-- Devuelto: el último hecho del historial es una devolución y sigue en ejecución.
UPDATE servicio_subprocesos ss
SET devuelto = TRUE
WHERE ss.devuelto = FALSE
  AND ss.estado = 'EN_EJECUCION'
  AND (SELECT h.accion FROM historial_asignaciones h WHERE h.id_servicio_subproceso = ss.id
       ORDER BY h.fecha_evento DESC, h.id DESC LIMIT 1) = 'DEVUELTO';

UPDATE servicio_subprocesos ss
SET reprogramaciones = (SELECT COUNT(*) FROM historial_asignaciones h
                        WHERE h.id_servicio_subproceso = ss.id AND h.accion = 'REPROGRAMADO')
WHERE ss.reprogramaciones = 0
  AND EXISTS (SELECT 1 FROM historial_asignaciones h WHERE h.id_servicio_subproceso = ss.id AND h.accion = 'REPROGRAMADO');

CREATE TABLE IF NOT EXISTS motivos_subproceso (
    id             SERIAL PRIMARY KEY,
    tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('REPROGRAMACION', 'CANCELACION', 'DEVOLUCION')),
    nombre         VARCHAR(150) NOT NULL,
    requiere_texto BOOLEAN      NOT NULL DEFAULT FALSE,
    orden          INTEGER      NOT NULL DEFAULT 0,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_motivos_subproceso_tipo_nombre ON motivos_subproceso (tipo, LOWER(nombre));

INSERT INTO motivos_subproceso (tipo, nombre, requiere_texto, orden) VALUES
    ('REPROGRAMACION', 'El candidato no se presentó (pasaron 15 min)', FALSE, 1),
    ('REPROGRAMACION', 'El candidato avisó que no asistirá', FALSE, 2),
    ('REPROGRAMACION', 'El candidato pidió cambiar la fecha', FALSE, 3),
    ('REPROGRAMACION', 'El candidato no estaba apto para la prueba', FALSE, 4),
    ('REPROGRAMACION', 'El profesional no asistió', FALSE, 5),
    ('REPROGRAMACION', 'Falla técnica o de conexión', FALSE, 6),
    ('REPROGRAMACION', 'Dirección errada o zona de acceso restringido', FALSE, 7),
    ('REPROGRAMACION', 'Otro', TRUE, 8),
    ('CANCELACION', 'El candidato desistió', FALSE, 1),
    ('CANCELACION', 'El cliente canceló el proceso de selección', FALSE, 2),
    ('CANCELACION', 'Solicitud duplicada', FALSE, 3),
    ('CANCELACION', 'Datos del candidato errados', FALSE, 4),
    ('CANCELACION', 'Otro', TRUE, 5),
    ('DEVOLUCION', 'Informe incompleto', FALSE, 1),
    ('DEVOLUCION', 'Datos del evaluado errados', FALSE, 2),
    ('DEVOLUCION', 'Soporte faltante o ilegible', FALSE, 3),
    ('DEVOLUCION', 'Hallazgo mal calificado', FALSE, 4),
    ('DEVOLUCION', 'Redacción o formato del informe', FALSE, 5),
    ('DEVOLUCION', 'Otro', TRUE, 6)
ON CONFLICT DO NOTHING;
