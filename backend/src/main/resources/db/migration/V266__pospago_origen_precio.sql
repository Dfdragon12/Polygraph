-- TP-12: cada cargo pospago guarda de dónde salió el precio con el que se creó: PLAN_100, PROPIO, BASE (interruptor apagado) o BOLSA
-- (la unidad salió de la bolsa prepago que el cliente tenía antes de pasar a pospago: solo se cobra el recargo, si aplica).
-- Los cargos anteriores quedan en NULL: no se inventa un origen. Idempotente.
ALTER TABLE movimientos_credito_pospago ADD COLUMN IF NOT EXISTS origen_precio VARCHAR(20);
