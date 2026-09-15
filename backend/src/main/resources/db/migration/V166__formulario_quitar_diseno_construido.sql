-- Se retira el diseño manual del documento (logo de encabezado, logo de pie, tipografía, marca
-- de agua) del modo CONSTRUIDA — ahora que existe el modo WORD_MARCADORES (subir un .docx real
-- diseñado en Word), ese panel quedó redundante: el diseño real vive en el documento subido.
ALTER TABLE formulario_plantilla_config
    DROP COLUMN imagen_encabezado_ruta,
    DROP COLUMN fuente,
    DROP COLUMN marca_agua_tipo,
    DROP COLUMN marca_agua_texto,
    DROP COLUMN marca_agua_imagen_ruta,
    DROP COLUMN imagen_pie_ruta;
