ALTER TABLE links_candidato
    ADD COLUMN IF NOT EXISTS fecha_uso         TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ip_origen         VARCHAR(45),
    ADD COLUMN IF NOT EXISTS intentos_fallidos SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS generado_por_id   BIGINT REFERENCES usuarios(id_usuario);
