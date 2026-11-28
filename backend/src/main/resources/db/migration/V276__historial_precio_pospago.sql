-- LP-01: lista de precios pospago editable. Opción A (decidida): el modo, el precio manual y «ofrecido» siguen en `procesos`
-- (modo_precio_pospago PLAN_100 = Automático / PROPIO = Manual, precio_pospago_propio, ofrecer_pospago; V261 y V265), así el día 1 la lista
-- se ve igual que hoy y no hay dos fuentes del mismo dato. Esta migración SOLO agrega:
--   1) quién y cuándo cambió por última vez el precio o el «ofrecido» de un servicio (columnas de `procesos`, vacías al inicio);
--   2) el historial de la lista pospago, de SOLO INSERCIÓN (un disparador rechaza UPDATE y DELETE).
-- No cambia ningún precio, ninguna solicitud ni las tablas de precios prepago. Idempotente.

ALTER TABLE procesos ADD COLUMN IF NOT EXISTS precio_pospago_actualizado_por BIGINT;
ALTER TABLE procesos ADD COLUMN IF NOT EXISTS precio_pospago_actualizado_en  TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_procesos_precio_pospago_usuario') THEN
        ALTER TABLE procesos ADD CONSTRAINT fk_procesos_precio_pospago_usuario
            FOREIGN KEY (precio_pospago_actualizado_por) REFERENCES usuarios (id_usuario);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS catalogo_precio_pospago_historial (
    id                BIGSERIAL PRIMARY KEY,
    id_proceso        INTEGER      NOT NULL REFERENCES procesos (id_proceso),
    accion            VARCHAR(20)  NOT NULL
                      CHECK (accion IN ('PRECIO_MANUAL', 'VOLVER_AUTO', 'OFRECIDO_SI', 'OFRECIDO_NO', 'RESTABLECER_TODOS')),
    modo_anterior     VARCHAR(20),
    modo_nuevo        VARCHAR(20),
    precio_anterior   BIGINT,          -- precio EFECTIVO (sin IVA) antes y después; null = sin precio
    precio_nuevo      BIGINT,
    ofrecido_anterior BOOLEAN,
    ofrecido_nuevo    BOOLEAN,
    id_usuario        BIGINT       NOT NULL REFERENCES usuarios (id_usuario),
    fecha             TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_precio_pospago_historial_proceso ON catalogo_precio_pospago_historial (id_proceso, fecha DESC);

CREATE OR REPLACE FUNCTION rechazar_cambio_historial_precio_pospago() RETURNS trigger AS $f$
BEGIN
    RAISE EXCEPTION 'El historial de la lista pospago solo admite inserciones';
END;
$f$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_historial_precio_pospago_solo_insertar') THEN
        CREATE TRIGGER trg_historial_precio_pospago_solo_insertar
            BEFORE UPDATE OR DELETE ON catalogo_precio_pospago_historial
            FOR EACH ROW EXECUTE FUNCTION rechazar_cambio_historial_precio_pospago();
    END IF;
END $$;
