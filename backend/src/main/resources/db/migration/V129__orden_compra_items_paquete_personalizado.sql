-- Un ítem de orden que viene de un paquete personalizado ya no representa un solo Proceso
-- (ahora el paquete se arma de subprocesos, no de procesos) — pasa a ser una sola línea por
-- paquete comprado, con id_proceso en null y el detalle de subprocesos incluidos como texto
-- (snapshot, igual que valor_unitario/subtotal ya son snapshots del precio al comprar).

ALTER TABLE "orden_compra_items" ALTER COLUMN "id_proceso" DROP NOT NULL;
ALTER TABLE "orden_compra_items" ADD COLUMN "detalle_subprocesos" TEXT;
