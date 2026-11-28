ALTER TABLE "orden_compra_items" ADD COLUMN "valor_original" NUMERIC(15,2);
UPDATE "orden_compra_items" SET "valor_original" = "valor_unitario" WHERE "valor_original" IS NULL;
ALTER TABLE "orden_compra_items" ALTER COLUMN "valor_original" SET NOT NULL;
