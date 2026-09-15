-- 'LISTA' (lista desplegable) ya existe como tipo de dato válido en el código (ver
-- TipoDatoFormulario, admin/catálogo → Plantilla) desde hace tiempo, pero esta restricción nunca
-- se actualizó para permitirlo — cualquier intento de guardar una columna tipo Lista desplegable
-- fallaba contra la base de datos con "viola la restricción check formulario_columnas_tipo_dato_check".
ALTER TABLE formulario_columnas DROP CONSTRAINT formulario_columnas_tipo_dato_check;
ALTER TABLE formulario_columnas ADD CONSTRAINT formulario_columnas_tipo_dato_check
    CHECK (tipo_dato IN ('TEXTO', 'NUMERO', 'FECHA', 'BOOLEANO', 'ARCHIVO', 'LISTA'));
