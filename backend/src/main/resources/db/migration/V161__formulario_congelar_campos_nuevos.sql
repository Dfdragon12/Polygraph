-- Cuándo se creó cada sección/columna de la plantilla (NULL = campo histórico, de antes de esta
-- migración, nunca se oculta por "congelamiento"). Un formulario que ya tiene datos guardados
-- (fecha_primer_llenado) solo muestra secciones/columnas creadas hasta ese momento — los campos
-- que el administrador agregue DESPUÉS no aparecen retroactivamente en formularios ya iniciados.
ALTER TABLE formulario_secciones ADD COLUMN fecha_creacion TIMESTAMP NULL;
ALTER TABLE formulario_columnas ADD COLUMN fecha_creacion TIMESTAMP NULL;

-- Momento en que el formulario recibió su primer dato real — se fija una sola vez, nunca se
-- actualiza después. NULL mientras el formulario esté vacío (todavía muestra todos los campos
-- activos, sin restricción).
ALTER TABLE formularios ADD COLUMN fecha_primer_llenado TIMESTAMP NULL;
