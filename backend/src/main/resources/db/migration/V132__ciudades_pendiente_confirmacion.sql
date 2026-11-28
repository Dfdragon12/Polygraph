-- Toda ciudad creada por el cliente/evaluado (sin sesión, vía POST /api/v1/ciudades) entra
-- directo como MUNICIPIO_SECUNDARIO — nunca se le asume el nivel más barato — y queda marcada
-- "pendiente de confirmación" para que un admin la revise en el módulo Ciudades y la acepte o
-- reclasifique. Las ciudades ya existentes sin nivel quedan igual: MUNICIPIO_SECUNDARIO + pendientes.
ALTER TABLE "ciudades" ADD COLUMN "pendiente_confirmacion" BOOLEAN NOT NULL DEFAULT false;

UPDATE "ciudades" SET "nivel_ciudad" = 'MUNICIPIO_SECUNDARIO', "pendiente_confirmacion" = true
WHERE "nivel_ciudad" IS NULL;

ALTER TABLE "ciudades" ALTER COLUMN "nivel_ciudad" SET DEFAULT 'MUNICIPIO_SECUNDARIO';
ALTER TABLE "ciudades" ALTER COLUMN "nivel_ciudad" SET NOT NULL;
