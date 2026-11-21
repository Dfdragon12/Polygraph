-- PB-04: enlace corto (solo Antecedentes) y seguimiento del enlace. Idempotente; no borra datos.

-- 1) Qué preguntas de declaración pide el enlace CORTO: las tres judiciales (procesos judiciales; órdenes de captura o asuntos pendientes; sanciones).
--    Las dos financieras (mora o reportes; embargos) y las demás quedan solo en el enlace completo. El administrador puede cambiarlo desde el catálogo.
ALTER TABLE items_hoja_vida ADD COLUMN IF NOT EXISTS enlace_corto BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE items_hoja_vida SET enlace_corto = TRUE
 WHERE lista = 'PREGUNTA' AND enlace_corto = FALSE
   AND (nombre LIKE '¿Tiene o ha tenido procesos judiciales%'
     OR nombre LIKE '¿Tiene o ha tenido órdenes de captura%'
     OR nombre LIKE '¿Tiene o ha tenido sanciones disciplinarias%');

-- 2) Recordatorio automático: uno por enlace y por periodo de 24 h. enviado_en = la primera vez que Polygraph lo envió.
ALTER TABLE links_candidato ADD COLUMN IF NOT EXISTS enviado_en TIMESTAMP;
ALTER TABLE links_candidato ADD COLUMN IF NOT EXISTS ultimo_recordatorio_en TIMESTAMP;
ALTER TABLE links_candidato ADD COLUMN IF NOT EXISTS recordatorios_enviados INTEGER NOT NULL DEFAULT 0;

-- 3) Cada envío del enlace: por qué canal, a qué destino, quién (o el sistema) y cuándo. Solo se inserta.
CREATE TABLE IF NOT EXISTS envios_enlace (
    id          BIGSERIAL PRIMARY KEY,
    id_link     BIGINT       NOT NULL,
    canal       VARCHAR(20)  NOT NULL CHECK (canal IN ('CORREO', 'WHATSAPP', 'COPIA', 'RECORDATORIO', 'CAMBIO_CONTACTO', 'VIGENCIA')),
    destino     VARCHAR(200),
    detalle     TEXT,
    id_usuario  BIGINT,
    automatico  BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_envios_enlace_link ON envios_enlace (id_link, fecha);
