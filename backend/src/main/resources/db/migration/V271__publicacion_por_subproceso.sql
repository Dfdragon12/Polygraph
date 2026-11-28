-- PB-02: publicación por subproceso con conclusión y calificación editables por el gestor, y su historial. Idempotente; no borra datos.
-- «conclusion»: la que escribe quien ejecuta el subproceso (un solo campo por formato, en el paso «Revisión y envío»).
-- «conclusion_publicada»: la que ve el cliente (la del ejecutor, o la ajustada por el gestor). El texto original queda en el historial.
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS conclusion TEXT;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS conclusion_publicada TEXT;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS nivel_publicado VARCHAR(10);
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS puntaje_publicado NUMERIC(7,2);
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS publicado_por BIGINT;

-- Todo cambio hecho por una persona sobre un informe: quién, cuándo, valor anterior, valor nuevo y motivo.
CREATE TABLE IF NOT EXISTS historial_publicacion_subproceso (
    id                    BIGSERIAL PRIMARY KEY,
    id_servicio_subproceso BIGINT      NOT NULL,
    tipo                  VARCHAR(30)  NOT NULL,   -- PUBLICAR, DEVOLVER, CAMBIO_CONCLUSION, CAMBIO_NIVEL, SOLICITUD_CORRECCION
    valor_anterior        TEXT,
    valor_nuevo           TEXT,
    motivo                TEXT,
    id_usuario            BIGINT,
    fecha                 TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_hist_pub_sp ON historial_publicacion_subproceso (id_servicio_subproceso, fecha);
