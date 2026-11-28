ALTER TABLE "ordenes_compra" ADD COLUMN "id_ciudad" INTEGER;
ALTER TABLE "ordenes_compra" ADD FOREIGN KEY ("id_ciudad") REFERENCES "ciudades" ("id_ciudad");

ALTER TABLE "orden_compra_items" ADD COLUMN "recargo_ciudad" NUMERIC(15,2) NOT NULL DEFAULT 0;
