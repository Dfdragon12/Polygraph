-- Engancha país y barrio a candidatos y clientes. Aditivo: no toca nada de lo que ya existe.
-- País va en "candidatos" porque ahí es donde ya viven id_ciudad_nacimiento/id_ciudad_residencia
-- (los escribe EvaluadoService directo sobre el candidato). Barrio, en cambio, va en
-- "hoja_vida_evaluado" — el texto libre "barrio" que reemplaza vive ahí, no en candidatos.barrio_residencia
-- (esa columna quedó sin usarse hace rato: la hoja de vida real la escribe aparte).
ALTER TABLE "candidatos" ADD COLUMN "id_pais_nacimiento" INTEGER REFERENCES "paises"("id_pais");
ALTER TABLE "candidatos" ADD COLUMN "id_pais_residencia" INTEGER REFERENCES "paises"("id_pais");
ALTER TABLE "hoja_vida_evaluado" ADD COLUMN "id_barrio" INTEGER REFERENCES "barrios"("id_barrio");

ALTER TABLE "clientes" ADD COLUMN "id_pais" INTEGER REFERENCES "paises"("id_pais");
ALTER TABLE "clientes" ADD COLUMN "id_barrio" INTEGER REFERENCES "barrios"("id_barrio");

-- Todo lo que ya existe es colombiano — se deja explícito en vez de nulo, para que el dato quede
-- completo desde ya sin obligar a nadie a volver a editarlo.
UPDATE "candidatos" SET "id_pais_nacimiento" = (SELECT "id_pais" FROM "paises" WHERE "codigo_iso" = 'CO');
UPDATE "candidatos" SET "id_pais_residencia" = (SELECT "id_pais" FROM "paises" WHERE "codigo_iso" = 'CO');
UPDATE "clientes" SET "id_pais" = (SELECT "id_pais" FROM "paises" WHERE "codigo_iso" = 'CO');
