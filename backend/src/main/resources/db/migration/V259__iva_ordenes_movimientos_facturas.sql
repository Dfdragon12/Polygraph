-- TP-03: IVA por línea en compras prepago y en consumo/facturas pospago.
-- Las filas existentes se transforman sin perder nada: el subtotal pasa a ser el total que ya tenían y el IVA queda en 0
-- (se cobraron sin IVA). Solo las compras y consumos nuevos, con el interruptor cobroConIva encendido, llevan IVA.

ALTER TABLE "orden_compra_items" ADD COLUMN IF NOT EXISTS "monto_iva" NUMERIC(15,2) NOT NULL DEFAULT 0;

ALTER TABLE "ordenes_compra" ADD COLUMN IF NOT EXISTS "monto_subtotal" NUMERIC(15,2);
ALTER TABLE "ordenes_compra" ADD COLUMN IF NOT EXISTS "monto_iva" NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE "ordenes_compra" ADD COLUMN IF NOT EXISTS "monto_iva_recargos" NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE "ordenes_compra" ADD COLUMN IF NOT EXISTS "iva_porcentaje" NUMERIC(5,2) NOT NULL DEFAULT 0;
UPDATE "ordenes_compra" SET "monto_subtotal" = "monto_total" WHERE "monto_subtotal" IS NULL;
ALTER TABLE "ordenes_compra" ALTER COLUMN "monto_subtotal" SET NOT NULL;

ALTER TABLE "movimientos_credito_pospago" ADD COLUMN IF NOT EXISTS "monto_iva_base" NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE "movimientos_credito_pospago" ADD COLUMN IF NOT EXISTS "monto_iva_recargo" NUMERIC(15,2) NOT NULL DEFAULT 0;

ALTER TABLE "facturas_pospago" ADD COLUMN IF NOT EXISTS "monto_subtotal" NUMERIC(15,2);
ALTER TABLE "facturas_pospago" ADD COLUMN IF NOT EXISTS "monto_iva" NUMERIC(15,2) NOT NULL DEFAULT 0;
UPDATE "facturas_pospago" SET "monto_subtotal" = "monto_total" WHERE "monto_subtotal" IS NULL;
ALTER TABLE "facturas_pospago" ALTER COLUMN "monto_subtotal" SET NOT NULL;
