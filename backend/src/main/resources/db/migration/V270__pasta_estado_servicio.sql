-- PB-01: indicador de la pasta final del estudio (no es un estado). Valor inicial PENDIENTE.
-- Los servicios que ya tienen hecho el «Publicar estudio» (servicio PUBLICADO) quedan EMITIDA. Idempotente; no borra nada.
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS pasta_estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
ALTER TABLE servicios DROP CONSTRAINT IF EXISTS chk_servicios_pasta_estado;
ALTER TABLE servicios ADD CONSTRAINT chk_servicios_pasta_estado CHECK (pasta_estado IN ('PENDIENTE', 'PRELIMINAR', 'EMITIDA'));
UPDATE servicios SET pasta_estado = 'EMITIDA' WHERE estado = 'PUBLICADO' AND pasta_estado = 'PENDIENTE';
