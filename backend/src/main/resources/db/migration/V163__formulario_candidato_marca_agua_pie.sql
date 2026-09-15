-- Config global de la plantilla (una fila por tipo de subproceso, igual que el logo/tipografía):
-- qué datos del candidato se muestran (lista de claves separadas por coma, ej.
-- "NOMBRE_COMPLETO,CEDULA,CELULAR"; NULL/vacío = comportamiento de siempre: nombre + cédula),
-- marca de agua (imagen o texto) y logo de pie de página.
ALTER TABLE formulario_plantilla_config
    ADD COLUMN campos_candidato       TEXT,
    ADD COLUMN marca_agua_tipo        VARCHAR(10) CHECK (marca_agua_tipo IN ('IMAGEN', 'TEXTO')),
    ADD COLUMN marca_agua_texto       VARCHAR(100),
    ADD COLUMN marca_agua_imagen_ruta VARCHAR(255),
    ADD COLUMN imagen_pie_ruta        VARCHAR(255);
