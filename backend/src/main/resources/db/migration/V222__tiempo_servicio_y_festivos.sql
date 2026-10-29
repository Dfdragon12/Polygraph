-- Tiempo de la solicitud en días hábiles de Colombia (2026-10-26, pedido del usuario; Fases 1 y 2).
--  · festivo / parametros_tiempo: el calendario hábil (Fase 1).
--  · servicios: los datos del plazo — recepción efectiva, plazo copiado del catálogo, compromiso original y vigente, pausas, estado de
--    tiempo, retraso, y al entregar la fecha real y el cumplimiento (un hecho que no se recalcula).
--  · solicitud_pausa / solicitud_reprogramacion: cuándo no corrió el reloj y cada cambio de la fecha de compromiso.
--  · tipos_novedad: si la novedad pausa el reloj y de quién es la causa.
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- ───────── calendario hábil ─────────
CREATE TABLE IF NOT EXISTS festivo (
    id                  SERIAL PRIMARY KEY,
    fecha               DATE         NOT NULL UNIQUE,
    nombre              VARCHAR(150) NOT NULL,
    origen              VARCHAR(6)   NOT NULL CHECK (origen IN ('AUTO', 'MANUAL')),
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por          VARCHAR(150),
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT NOW(),
    modificado_por      VARCHAR(150),
    fecha_modificacion  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS parametros_tiempo (
    clave               VARCHAR(50)  PRIMARY KEY,
    valor               VARCHAR(100) NOT NULL,
    descripcion         VARCHAR(250),
    modificado_por      VARCHAR(150),
    fecha_modificacion  TIMESTAMP
);

INSERT INTO parametros_tiempo (clave, valor, descripcion) VALUES
    ('zona_horaria', 'America/Bogota', 'Zona horaria con la que se cuentan los días y la hora de corte'),
    ('hora_corte', '16:00', 'Una solicitud recibida después de esta hora (o en día no hábil) cuenta como del siguiente día hábil'),
    ('umbral_por_vencer_dias', '1', 'Una solicitud está por vencer si le quedan este número de días hábiles o menos'),
    ('reprogramacion_polygraph_mide', 'ORIGINAL', 'Si una reprogramación es por causa de Polygraph el cumplimiento se mide contra la fecha ORIGINAL (o la VIGENTE); si es del cliente o del candidato, siempre contra la nueva')
ON CONFLICT (clave) DO NOTHING;

-- ───────── la solicitud (tabla servicios) ─────────
-- La fecha de entrega estimada ES la fecha de compromiso vigente: se renombra la columna (el campo Java conserva su nombre).
ALTER TABLE servicios RENAME COLUMN fecha_entrega_estimada TO fecha_compromiso;

ALTER TABLE servicios ADD COLUMN IF NOT EXISTS fecha_recepcion_efectiva  DATE;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS plazo_dias_habiles        INTEGER;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS fecha_compromiso_original DATE;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS dias_pausa_habiles        INTEGER NOT NULL DEFAULT 0;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS fecha_compromiso_ajustada DATE;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS estado_tiempo             VARCHAR(20);
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS dias_restantes_habiles    INTEGER;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS pausa_origen              VARCHAR(10) CHECK (pausa_origen IN ('CANDIDATO', 'CLIENTE'));
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS dias_retraso_habiles      INTEGER;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS fecha_entrega_real        DATE;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS cumplimiento              VARCHAR(10) CHECK (cumplimiento IN ('A_TIEMPO', 'TARDE'));
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS estado_tiempo_actualizado_en TIMESTAMP;

UPDATE servicios SET fecha_compromiso_original = fecha_compromiso WHERE fecha_compromiso_original IS NULL;

-- Lo ya entregado: la última vez que pasó a PUBLICADO es la entrega real. "Tarde" = hubo al menos un día hábil (lunes a viernes) entre
-- el compromiso y la entrega; el resto del histórico (recepción, plazo, estado de tiempo) lo completa la aplicación al arrancar,
-- con los festivos reales (TiempoServicioService.completarHistorico).
UPDATE servicios s SET fecha_entrega_real = h.f::date
FROM (SELECT id_servicio, MAX(fecha_cambio) AS f FROM historial_estados_servicio WHERE estado_nuevo = 'PUBLICADO' GROUP BY id_servicio) h
WHERE h.id_servicio = s.id_servicio AND s.estado = 'PUBLICADO';

CREATE INDEX IF NOT EXISTS ix_servicios_estado_tiempo ON servicios (estado_tiempo);
CREATE INDEX IF NOT EXISTS ix_servicios_cliente_estado_tiempo ON servicios (id_cliente, estado_tiempo);

-- ───────── pausas del reloj ─────────
-- Mientras hay una pausa abierta (fin NULL) el estado de tiempo es EN_PAUSA y la fecha de compromiso no corre.
CREATE TABLE IF NOT EXISTS solicitud_pausa (
    id            BIGSERIAL PRIMARY KEY,
    solicitud_id  INTEGER      NOT NULL REFERENCES servicios (id_servicio) ON DELETE CASCADE,
    inicio        TIMESTAMP    NOT NULL,
    fin           TIMESTAMP,
    motivo        VARCHAR(250) NOT NULL,
    origen        VARCHAR(10)  NOT NULL CHECK (origen IN ('CANDIDATO', 'CLIENTE')),
    creado_por    VARCHAR(150)
);
CREATE INDEX IF NOT EXISTS ix_solicitud_pausa_solicitud ON solicitud_pausa (solicitud_id);
CREATE INDEX IF NOT EXISTS ix_solicitud_pausa_abiertas ON solicitud_pausa (solicitud_id) WHERE fin IS NULL;

-- ───────── reprogramaciones ─────────
CREATE TABLE IF NOT EXISTS solicitud_reprogramacion (
    id              BIGSERIAL PRIMARY KEY,
    solicitud_id    INTEGER      NOT NULL REFERENCES servicios (id_servicio) ON DELETE CASCADE,
    fecha_anterior  DATE         NOT NULL,
    fecha_nueva     DATE         NOT NULL,
    motivo          VARCHAR(500) NOT NULL,
    responsable     VARCHAR(10)  NOT NULL CHECK (responsable IN ('POLYGRAPH', 'CANDIDATO', 'CLIENTE')),
    usuario         VARCHAR(150) NOT NULL,
    fecha           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_solicitud_reprogramacion_solicitud ON solicitud_reprogramacion (solicitud_id);

-- ───────── tipos de novedad ─────────
ALTER TABLE tipos_novedad ADD COLUMN IF NOT EXISTS pausa_reloj BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tipos_novedad ADD COLUMN IF NOT EXISTS imputable_a VARCHAR(10) NOT NULL DEFAULT 'POLYGRAPH'
    CHECK (imputable_a IN ('POLYGRAPH', 'CANDIDATO', 'CLIENTE'));

-- Valores iniciales sensatos (el administrador los ajusta en Catálogo → Tipos de novedad): que el candidato no llegue o entregue
-- documentos que no sirven es demora suya y detiene el reloj.
UPDATE tipos_novedad SET pausa_reloj = TRUE, imputable_a = 'CANDIDATO'
WHERE clave = 'NO_LLEGO_A_LA_POLIGRAFIA' OR clave LIKE 'DOCUMENTO\_%';

-- ───────── cola de trabajos: el recálculo diario del semáforo de tiempo ─────────
ALTER TABLE trabajos_asincronos DROP CONSTRAINT IF EXISTS trabajos_asincronos_tipo_check;
ALTER TABLE trabajos_asincronos ADD CONSTRAINT trabajos_asincronos_tipo_check
    CHECK (tipo IN ('CARGA_MASIVA', 'INFORME_PDF', 'FORMULARIO_PDF', 'RECALCULO_TIEMPO'));
