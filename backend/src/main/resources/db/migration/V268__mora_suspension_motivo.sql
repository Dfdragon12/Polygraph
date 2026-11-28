-- TP-14: la suspensión es una decisión manual del administrador, con motivo escrito (la plataforma nunca suspende sola).
-- Se guarda el motivo, quién y cuándo. No cambia ningún estado existente. Idempotente.
ALTER TABLE clientes_pospago ADD COLUMN IF NOT EXISTS motivo_suspension VARCHAR(1000);
ALTER TABLE clientes_pospago ADD COLUMN IF NOT EXISTS suspendido_por    VARCHAR(150);
ALTER TABLE clientes_pospago ADD COLUMN IF NOT EXISTS suspendido_en     TIMESTAMP;
