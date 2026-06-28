-- Quién realizó la acción que generó la notificación (nullable: sistema o registro anónimo)
ALTER TABLE notificaciones
    ADD COLUMN IF NOT EXISTS realizado_por_id BIGINT;

ALTER TABLE notificaciones
    ADD CONSTRAINT fk_notificaciones_realizado_por
    FOREIGN KEY (realizado_por_id) REFERENCES usuarios(id_usuario)
    DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX ON notificaciones (realizado_por_id);
