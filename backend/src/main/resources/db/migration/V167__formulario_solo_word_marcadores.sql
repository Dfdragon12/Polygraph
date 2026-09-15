-- Se retira el modo CONSTRUIDA (generador de documento genérico con Apache POI): de aquí en
-- adelante toda plantilla se genera exclusivamente a partir de un .docx con marcadores subido por
-- el administrador (WORD_MARCADORES). Se elimina la columna que guardaba el modo activo (ya no
-- hay nada que elegir) y las columnas de estilo por sección (presentación tabla/texto, color de
-- texto, negrita) que solo aplicaban al generador viejo.
ALTER TABLE formulario_plantilla_config
    DROP COLUMN modo_plantilla;

ALTER TABLE formulario_secciones
    DROP COLUMN presentacion,
    DROP COLUMN color_texto,
    DROP COLUMN negrita;
