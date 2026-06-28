ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS requiere_cambio_password BOOLEAN DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS fecha_ultimo_cambio_password TIMESTAMP;
