-- Secciones opcionales de las plantillas (pedido del usuario 2026-10-14).
-- El administrador marca en el constructor qué secciones puede quitar quien diligencia (opcional);
-- las demás siguen siendo obligatorias. Lo que quita el analista en un formulario puntual queda en
-- formulario_secciones_omitidas: no se diligencia, no exige sus campos obligatorios y no sale en el
-- Word (bloque repeatDocPart(secciones['clave'])).

ALTER TABLE formulario_secciones ADD COLUMN opcional BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE formulario_secciones_omitidas (
    id_formulario INTEGER NOT NULL REFERENCES formularios (id_formulario) ON DELETE CASCADE,
    id_seccion    INTEGER NOT NULL REFERENCES formulario_secciones (id_seccion) ON DELETE CASCADE,
    PRIMARY KEY (id_formulario, id_seccion)
);
