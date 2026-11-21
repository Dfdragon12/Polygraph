-- TP-11: lista de precios pospago (detrás del interruptor pospagoPlan100).
-- modo_precio_pospago: PLAN_100 (automático: el precio por unidad del plan de 100 de la lista prepago) o PROPIO (precio_pospago_propio).
-- Un servicio que ya traía precio_pospago_propio (importado en TP-05) queda en PROPIO para no perder ese precio; el resto, en PLAN_100.
-- No cambia ningún precio. Idempotente.

ALTER TABLE procesos ADD COLUMN IF NOT EXISTS modo_precio_pospago VARCHAR(20) NOT NULL DEFAULT 'PLAN_100';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_procesos_modo_precio_pospago') THEN
        ALTER TABLE procesos ADD CONSTRAINT chk_procesos_modo_precio_pospago CHECK (modo_precio_pospago IN ('PLAN_100', 'PROPIO'));
    END IF;
END $$;

UPDATE procesos SET modo_precio_pospago = 'PROPIO'
WHERE precio_pospago_propio IS NOT NULL AND precio_pospago_propio > 0 AND modo_precio_pospago = 'PLAN_100';
