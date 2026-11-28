-- Enlaza explícitamente cada bloque de la plantilla con el subproceso (tipo de progreso) al que
-- pertenece — antes la relación era implícita (esta tabla solo se usaba para "Antecedentes",
-- pero nada en el esquema lo decía).
ALTER TABLE plantilla_antecedentes_bloques
    ADD COLUMN id_tipo_progreso INTEGER REFERENCES tipos_progreso (id_tipo_progreso);

UPDATE plantilla_antecedentes_bloques
SET id_tipo_progreso = (SELECT id_tipo_progreso FROM tipos_progreso WHERE nombre_progreso = 'Antecedentes')
WHERE id_tipo_progreso IS NULL;

-- Corrección 2026-09-28 (instalación limpia): si no existe el tipo de progreso "Antecedentes"
-- (base nueva, sin datos), los bloques quedaban en NULL y el SET NOT NULL fallaba. Sin ese tipo
-- los bloques no pertenecen a nada; además V156 elimina esta tabla.
DELETE FROM plantilla_antecedentes_bloques WHERE id_tipo_progreso IS NULL;

ALTER TABLE plantilla_antecedentes_bloques
    ALTER COLUMN id_tipo_progreso SET NOT NULL;

CREATE INDEX ON plantilla_antecedentes_bloques (id_tipo_progreso);
