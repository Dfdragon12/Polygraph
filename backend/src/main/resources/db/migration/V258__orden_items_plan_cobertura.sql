-- TP-02: cada ítem de compra guarda el plan, la cobertura y los paquetes comprados (snapshot: no cambia con el catálogo).
-- Las compras anteriores quedan con NULL (se compraron por cantidad libre). Solo agrega columnas.
ALTER TABLE "orden_compra_items" ADD COLUMN IF NOT EXISTS "unidades_plan" INTEGER;
ALTER TABLE "orden_compra_items" ADD COLUMN IF NOT EXISTS "cantidad_paquetes" INTEGER;
ALTER TABLE "orden_compra_items" ADD COLUMN IF NOT EXISTS "cobertura" VARCHAR(20);
