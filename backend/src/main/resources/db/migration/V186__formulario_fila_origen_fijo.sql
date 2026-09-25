-- Permite que una FormularioFila represente la fila de VALIDACIÓN (analista/poligrafista/
-- visitador) correlacionada con una fila real de la hoja de vida del candidato (referencia,
-- educación, experiencia laboral o inactividad), sin tocar esas tablas originales.
-- Nullable + sin FK: id_origen_fijo apunta a una de 4 tablas distintas según
-- formulario_secciones.origen_fijo de la sección dueña de esta fila (referencias_personales,
-- educacion_evaluado, experiencia_laboral_evaluado o inactividad_laboral_evaluado) — referencia
-- débil, mismo criterio ya usado en el proyecto para "clave" (texto estable sin FK real).
ALTER TABLE formulario_filas ADD COLUMN id_origen_fijo BIGINT;
