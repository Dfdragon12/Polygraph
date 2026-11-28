-- TP-07: vigencia y lotes de compra (detrás del interruptor vigenciaPorPlan).
-- 1) El ítem de la orden guarda la vigencia (en meses) que se le mostró al cliente: es la que rige aunque el plan cambie después.
-- 2) El lote gana cobertura, fecha de vencimiento, unidades vencidas, la transacción de pago que lo creó y su origen.
--    Un mismo aviso de pago nunca crea dos veces el lote: único por (transacción de pago, ítem).
-- 3) El saldo guarda las unidades vencidas aparte: disponible = comprado - consumido - vencido.
-- 4) Saldo migrado: lo que el contador del saldo tiene disponible y ningún lote respalda pasa a UN lote «Saldo migrado» por cliente y servicio,
--    con el promedio ponderado de las compras pagadas y SIN vencimiento. Los lotes de V229 tampoco vencen. Queda en el reporte.
-- No borra nada. Idempotente.

ALTER TABLE orden_compra_items ADD COLUMN IF NOT EXISTS vigencia_meses INTEGER;

ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS cobertura           VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS fecha_vencimiento   TIMESTAMP;
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS cantidad_vencida    INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS fecha_vencida       TIMESTAMP;
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS id_transaccion_pago VARCHAR(100);
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS origen              VARCHAR(20) NOT NULL DEFAULT 'COMPRA';
ALTER TABLE lotes_prepago ADD COLUMN IF NOT EXISTS aviso_vencimiento_en TIMESTAMP;

-- Un lote de saldo migrado no viene de una compra concreta
ALTER TABLE lotes_prepago ALTER COLUMN id_orden_compra DROP NOT NULL;
ALTER TABLE lotes_prepago ALTER COLUMN id_item DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_lotes_prepago_vencidas') THEN
        ALTER TABLE lotes_prepago ADD CONSTRAINT chk_lotes_prepago_vencidas
            CHECK (cantidad_vencida >= 0 AND cantidad_consumida + cantidad_vencida <= cantidad_comprada);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_lotes_prepago_transaccion_item ON lotes_prepago (id_transaccion_pago, id_item) WHERE id_transaccion_pago IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_lotes_prepago_vencimiento ON lotes_prepago (fecha_vencimiento) WHERE fecha_vencimiento IS NOT NULL AND fecha_vencida IS NULL;

ALTER TABLE saldos_servicio_cliente ADD COLUMN IF NOT EXISTS cantidad_vencida INTEGER NOT NULL DEFAULT 0;

-- La cobertura de los lotes ya creados es la del ítem (compras por plan) o LOCAL
UPDATE lotes_prepago l SET cobertura = COALESCE(i.cobertura, 'LOCAL')
FROM orden_compra_items i WHERE i.id_item = l.id_item AND l.cobertura = 'LOCAL' AND i.cobertura IS NOT NULL;

-- Saldo migrado
INSERT INTO lotes_prepago (id_cliente, id_proceso, id_orden_compra, id_item, cantidad_comprada, cantidad_consumida, valor_unitario, valor_original,
                           fecha_compra, origen, cobertura)
SELECT g.id_cliente, g.id_proceso, NULL, NULL, g.faltante, 0,
       COALESCE(ROUND(g.promedio), p.valor, 0), COALESCE(ROUND(g.promedio), p.valor, 0), NOW(), 'SALDO_MIGRADO', 'LOCAL'
FROM (
    SELECT s.id_cliente, s.id_proceso,
           s.cantidad_disponible - COALESCE(SUM(l.cantidad_comprada - l.cantidad_consumida - l.cantidad_vencida), 0) AS faltante,
           CASE WHEN COALESCE(SUM(l.cantidad_comprada) FILTER (WHERE l.origen = 'COMPRA'), 0) > 0
                THEN SUM(l.valor_unitario * l.cantidad_comprada) FILTER (WHERE l.origen = 'COMPRA')
                     / SUM(l.cantidad_comprada) FILTER (WHERE l.origen = 'COMPRA') END AS promedio
    FROM saldos_servicio_cliente s
    LEFT JOIN lotes_prepago l ON l.id_cliente = s.id_cliente AND l.id_proceso = s.id_proceso
    GROUP BY s.id_cliente, s.id_proceso, s.cantidad_disponible
) g
JOIN procesos p ON p.id_proceso = g.id_proceso
WHERE g.faltante > 0
  AND NOT EXISTS (SELECT 1 FROM lotes_prepago x WHERE x.id_cliente = g.id_cliente AND x.id_proceso = g.id_proceso AND x.origen = 'SALDO_MIGRADO');

INSERT INTO migracion_lotes_prepago_reporte (tipo, id_cliente, id_proceso, id_lote, cantidad, observacion)
SELECT 'SALDO_MIGRADO', l.id_cliente, l.id_proceso, l.id_lote, l.cantidad_comprada,
       'Lote «Saldo migrado» de ' || l.cantidad_comprada || ' unidades a ' || l.valor_unitario || ' (promedio ponderado de las compras pagadas), sin vencimiento'
FROM lotes_prepago l
WHERE l.origen = 'SALDO_MIGRADO'
  AND NOT EXISTS (SELECT 1 FROM migracion_lotes_prepago_reporte r WHERE r.tipo = 'SALDO_MIGRADO' AND r.id_lote = l.id_lote);
