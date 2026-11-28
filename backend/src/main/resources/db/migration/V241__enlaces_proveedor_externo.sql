-- PF-09 · Modo proveedor externo: el programador genera un enlace por evaluación de poligrafía y el proveedor la
-- diligencia sin usuario ni contraseña. Del token solo se guarda su huella (SHA-256): si alguien ve la base de datos
-- no puede reconstruir el enlace. Vence al enviar el informe o a las 72 h (lo que ocurra primero); el programador
-- puede revocarlo o generar otro (el anterior queda revocado). Esta tabla es también el historial de quién lo generó,
-- cuándo y cuándo se usó.
CREATE TABLE enlaces_proveedor (
    id                     BIGSERIAL PRIMARY KEY,
    id_servicio_subproceso BIGINT       NOT NULL REFERENCES servicio_subprocesos (id),
    token_hash             VARCHAR(64)  NOT NULL,
    creado_por             BIGINT       NOT NULL REFERENCES usuarios (id_usuario),
    creado_en              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expira_en              TIMESTAMPTZ  NOT NULL,
    primer_uso_en          TIMESTAMPTZ,
    ultimo_uso_en          TIMESTAMPTZ,
    usos                   INTEGER      NOT NULL DEFAULT 0,
    revocado_en            TIMESTAMPTZ,
    revocado_por           BIGINT REFERENCES usuarios (id_usuario),
    -- ENVIADO (el informe salió) o REEMPLAZADO (se generó otro enlace); la revocación manual va en revocado_en.
    cerrado_en             TIMESTAMPTZ,
    motivo_cierre          VARCHAR(20),
    CONSTRAINT ck_enlace_proveedor_motivo CHECK (motivo_cierre IS NULL OR motivo_cierre IN ('ENVIADO', 'REEMPLAZADO'))
);

CREATE UNIQUE INDEX ux_enlaces_proveedor_hash ON enlaces_proveedor (token_hash);
CREATE INDEX ix_enlaces_proveedor_subproceso ON enlaces_proveedor (id_servicio_subproceso, creado_en DESC);
-- A lo sumo un enlace vigente por evaluación.
CREATE UNIQUE INDEX ux_enlaces_proveedor_vigente ON enlaces_proveedor (id_servicio_subproceso)
    WHERE revocado_en IS NULL AND cerrado_en IS NULL;
