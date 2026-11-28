-- Se descarta por completo el concepto de "Paquete" (armado por admin o por cliente) — no
-- funcionó ni para el cliente ni para el admin. Los descuentos (V127) se quedan intactos,
-- solo se les quita la posibilidad de apuntar a un paquete.

DELETE FROM "orden_compra_items" WHERE "id_proceso" IS NULL;

ALTER TABLE "descuentos" DROP COLUMN IF EXISTS "id_paquete";

ALTER TABLE "orden_compra_items" DROP COLUMN IF EXISTS "id_paquete";
ALTER TABLE "orden_compra_items" DROP COLUMN IF EXISTS "detalle_subprocesos";
ALTER TABLE "orden_compra_items" ALTER COLUMN "id_proceso" SET NOT NULL;

DROP TABLE IF EXISTS "paquete_subprocesos";
DROP TABLE IF EXISTS "paquete_procesos";
DROP TABLE IF EXISTS "paquetes";
