-- Personalización visual de la plantilla: cómo se ve cada sección en el documento generado
-- (tabla vs texto corrido) y su estilo de letra/color, más una imagen de encabezado (logo) por
-- plantilla. Todo opcional — si el admin no toca nada, el documento se ve exactamente igual que
-- antes (TABLA, sin estilo especial, sin encabezado).

ALTER TABLE formulario_secciones
    ADD COLUMN presentacion VARCHAR(20) NOT NULL DEFAULT 'TABLA'
        CHECK (presentacion IN ('TABLA', 'TEXTO')),
    ADD COLUMN fuente       VARCHAR(50),
    ADD COLUMN color_texto  VARCHAR(7),
    ADD COLUMN negrita      BOOLEAN NOT NULL DEFAULT FALSE;

-- Una imagen de encabezado (logo) por plantilla — se muestra al inicio del documento generado,
-- antes del título. Fila opcional: solo existe si el admin subió algo.
CREATE TABLE formulario_plantilla_config (
    id_tipo_progreso        INTEGER PRIMARY KEY REFERENCES tipos_progreso (id_tipo_progreso) ON DELETE CASCADE,
    imagen_encabezado_ruta  VARCHAR(255)
);
