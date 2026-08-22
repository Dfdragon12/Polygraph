-- Agrega el tiempo de entrega en días hábiles a cada proceso
ALTER TABLE procesos
    ADD COLUMN dias_habiles_entrega INTEGER NOT NULL DEFAULT 5;
