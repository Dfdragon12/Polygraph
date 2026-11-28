-- Modo alternativo de generación del documento: en vez de que el sistema arme el .docx
-- genéricamente (CONSTRUIDA, comportamiento de siempre), el admin puede subir un .docx diseñado
-- a mano en Word con marcadores (sintaxis tipo "campos punto clave") que la plantilla llena en
-- vivo (WORD_MARCADORES). El constructor de secciones/columnas sigue siendo el mismo en ambos
-- modos. Nota: evitar escribir el símbolo de dólar seguido de llaves en comentarios de este
-- archivo — Flyway lo interpreta como un placeholder propio y rompe la migración.
ALTER TABLE formulario_plantilla_config
    ADD COLUMN modo_plantilla     VARCHAR(20) NOT NULL DEFAULT 'CONSTRUIDA'
        CHECK (modo_plantilla IN ('CONSTRUIDA', 'WORD_MARCADORES')),
    ADD COLUMN plantilla_word_ruta VARCHAR(255);

-- Clave de texto estable para referenciar la columna desde un marcador de Word (ej. "Observación"
-- -> observacion). Se genera siempre que se crea/edita una columna, aunque el modo de la
-- plantilla sea CONSTRUIDA — así queda lista si más adelante cambian a WORD_MARCADORES.
ALTER TABLE formulario_columnas ADD COLUMN clave VARCHAR(50);

-- Lo mismo para secciones tipo TABLA: su clave se usa para repeatTableRow (una lista de filas).
ALTER TABLE formulario_secciones ADD COLUMN clave VARCHAR(50);
