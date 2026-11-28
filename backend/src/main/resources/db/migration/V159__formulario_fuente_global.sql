-- La tipografía pasa a elegirse UNA vez por plantilla (no sección por sección) — mucho más
-- simple para el administrador. Se migra lo que ya estaba configurado: si alguna sección de un
-- tipo_progreso ya tenía una fuente, esa se vuelve la fuente global de esa plantilla.
ALTER TABLE formulario_plantilla_config ADD COLUMN fuente VARCHAR(50);

INSERT INTO formulario_plantilla_config (id_tipo_progreso, fuente)
SELECT DISTINCT ON (id_tipo_progreso) id_tipo_progreso, fuente
FROM formulario_secciones
WHERE fuente IS NOT NULL
ORDER BY id_tipo_progreso, id_seccion
ON CONFLICT (id_tipo_progreso) DO UPDATE SET fuente = EXCLUDED.fuente;

ALTER TABLE formulario_secciones DROP COLUMN fuente;
