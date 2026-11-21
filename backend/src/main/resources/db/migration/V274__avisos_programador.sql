-- PB-05: «Avisar al programador» (dashboard del gestor). Registro de SOLO inserción: quién avisó, a qué programador, cuántas citas y cuáles.
-- Idempotente y sin tocar datos existentes.
CREATE TABLE IF NOT EXISTS avisos_programador (
    id              BIGSERIAL PRIMARY KEY,
    id_gestor       BIGINT       NOT NULL REFERENCES usuarios (id_usuario),
    id_programador  BIGINT       NOT NULL REFERENCES usuarios (id_usuario),
    cantidad        INTEGER      NOT NULL,
    subprocesos     TEXT         NOT NULL,
    fecha           TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_avisos_programador_gestor ON avisos_programador (id_gestor, fecha DESC);
