-- Cierre: un lote por PAQUETE comprado (1 Plan Básico + 2 Plan Inicial = un lote de 5 y dos de 1), no un lote por ítem.
-- Antes id_item era único; ahora lo es (id_item, indice_paquete). Los lotes que ya existen quedan con indice_paquete = 0 (un lote por ítem), sin tocarlos.
-- Idempotente.
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS indice_paquete INTEGER NOT NULL DEFAULT 0;
ALTER TABLE lotes_prepago DROP CONSTRAINT IF EXISTS lotes_prepago_id_item_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_lotes_prepago_item_paquete ON lotes_prepago (id_item, indice_paquete) WHERE id_item IS NOT NULL;
DROP INDEX IF EXISTS ux_lotes_prepago_transaccion_item;
CREATE UNIQUE INDEX IF NOT EXISTS ux_lotes_prepago_transaccion_item_paquete ON lotes_prepago (id_transaccion_pago, id_item, indice_paquete) WHERE id_transaccion_pago IS NOT NULL;
