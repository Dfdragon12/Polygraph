ALTER TABLE links_candidato
    ADD COLUMN IF NOT EXISTS fecha_primer_ingreso TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ip_ingreso           VARCHAR(45);
