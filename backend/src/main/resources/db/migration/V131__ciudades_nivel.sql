-- Nivel logístico de cada ciudad/municipio (PRINCIPAL / INTERMEDIA / MUNICIPIO_SECUNDARIO),
-- usado para tarifar servicios con desplazamiento. Nullable a propósito: el catálogo de ciudades
-- crece solo (clientes y evaluados pueden crear ciudades sin sesión vía POST /api/v1/ciudades),
-- así que las nuevas siempre entran sin clasificar y el admin las revisa después.
ALTER TABLE "ciudades" ADD COLUMN "nivel_ciudad" VARCHAR(30);
