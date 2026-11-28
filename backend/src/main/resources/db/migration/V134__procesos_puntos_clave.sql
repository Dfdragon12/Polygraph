-- Puntos clave cortos para la tarjeta de la tienda (ej. "Registros penales", "Boletines de
-- contraloría"), en vez de mostrar la descripción larga completa. Se guardan como texto con un
-- punto por línea — el backend los parte/junta al leer/escribir, no hace falta una tabla aparte.
ALTER TABLE "procesos" ADD COLUMN "puntos_clave" TEXT;
