-- Reemplaza el panel global "datos del candidato" (checkboxes en Diseño del documento) por algo
-- más flexible: cualquier columna, en cualquier sección, se puede marcar como "dato del
-- candidato" en vez de un campo manual — así se arma libremente, como en Word, en el orden y
-- lugar del documento que el administrador quiera.
ALTER TABLE formulario_plantilla_config DROP COLUMN campos_candidato;

ALTER TABLE formulario_columnas ADD COLUMN campo_candidato VARCHAR(50);

-- El título de una columna deja de ser obligatorio: si se deja vacío, el documento muestra solo
-- el valor, sin etiqueta — útil para secciones que son párrafos corridos, no todas necesitan un
-- rótulo por campo. (El nombre de la SECCIÓN sigue siendo obligatorio, eso no cambió.)
ALTER TABLE formulario_columnas ALTER COLUMN nombre DROP NOT NULL;
