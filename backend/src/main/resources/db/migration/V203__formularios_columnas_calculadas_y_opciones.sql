-- Constructor de formularios: lo que necesitan los subservicios que califican con códigos propios
-- (2026-10-05, docs/antecedentes/). Es genérico: sirve para cualquier plantilla, no solo Antecedentes.
--
-- · opciones: ahora una opción por línea (antes separadas por coma, y una opción con coma — "Manejo
--   de dinero, valores o inventarios" — se partía en dos). Las existentes no podían tener comas, así
--   que pasarlas a líneas no cambia ninguna opción. TEXT: la lista de cargos del Anexo 2 pasa de 1000.
-- · calculado: el valor lo pone el código del subservicio (fórmula, puntaje, semáforo, notas); el
--   analista no lo edita y nunca se guarda en formulario_datos.
-- · campo_servicio: dato del estudio (N° de estudio, fecha de emisión, cliente, cargo, profesional)
--   que se resuelve en vivo, igual que campo_candidato/campo_cliente. Ver CampoServicio.
-- · valor_defecto: lo que muestra el campo mientras nadie lo cambie (ej. "—" en las columnas Ref.).
-- · obligatorio: no se puede completar el subproceso con este campo vacío.
-- · formulario_secciones.calculada: tabla que llena el código (ej. hallazgos), no el analista.

ALTER TABLE formulario_columnas ALTER COLUMN opciones TYPE TEXT;
UPDATE formulario_columnas
SET opciones = regexp_replace(opciones, '\s*,\s*', E'\n', 'g')
WHERE opciones IS NOT NULL;

ALTER TABLE formulario_columnas ADD COLUMN calculado      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE formulario_columnas ADD COLUMN campo_servicio VARCHAR(50);
ALTER TABLE formulario_columnas ADD COLUMN valor_defecto  VARCHAR(200);
ALTER TABLE formulario_columnas ADD COLUMN obligatorio    BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE formulario_secciones ADD COLUMN calculada BOOLEAN NOT NULL DEFAULT FALSE;
