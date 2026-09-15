-- Nuevo tipo de columna ARCHIVO — permite adjuntar un documento/imagen a cualquier campo o fila
-- de cualquier sección, igual que cualquier otra columna (el administrador la agrega desde la
-- plantilla como cualquier otra). El valor guardado en formulario_datos es la ruta relativa del
-- archivo bajo backend/uploads (no el archivo en sí).
ALTER TABLE formulario_columnas DROP CONSTRAINT formulario_columnas_tipo_dato_check;
ALTER TABLE formulario_columnas ADD CONSTRAINT formulario_columnas_tipo_dato_check
    CHECK (tipo_dato IN ('TEXTO', 'NUMERO', 'FECHA', 'BOOLEANO', 'ARCHIVO'));
