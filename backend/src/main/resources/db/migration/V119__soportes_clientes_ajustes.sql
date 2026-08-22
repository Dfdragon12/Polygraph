-- Activa la tabla soportes_clientes (hasta ahora huérfana, sin código) para el módulo
-- "Documentos legales del cliente" (/cliente/documentos): un slot por tipo de soporte
-- (cédula ampliada, RUT, cámara de comercio, habeas data), que se reemplaza al resubir —
-- por eso el índice único por (id_cliente, tipo_soporte) en vez de historial append-only.
ALTER TABLE soportes_clientes
    ADD CONSTRAINT uq_soportes_clientes_cliente_tipo UNIQUE (id_cliente, tipo_soporte);

-- El resto del proyecto usa estados en mayúsculas (PENDIENTE/VALIDADO/...); el default
-- original quedó en minúscula desde el esquema base sin código que lo usara.
ALTER TABLE soportes_clientes ALTER COLUMN estado SET DEFAULT 'PENDIENTE';
UPDATE soportes_clientes SET estado = 'PENDIENTE' WHERE estado = 'pendiente';
