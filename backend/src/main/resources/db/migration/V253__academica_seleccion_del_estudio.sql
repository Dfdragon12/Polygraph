-- AC-02 (2026-11-04): Verificación académica. (1) El lugar de expedición ya no se escribe a mano: viene del link del evaluado (como en antecedentes, V245, y laboral, V249).
-- (2) Selección del estudio a verificar (Prompt 2 original, punto 3c, que nunca se creó): aca_estudio_sel guarda el id del estudio declarado y aca_decl una copia de lo
-- que declaró el evaluado en ese momento (JSON), para compararlo con lo confirmado y avisar si después cambia su hoja de vida. Ninguna se imprime.
-- (3) Tipos de novedad para el analista: un dato del evaluado no coincide con el documento / el cargo de la solicitud tiene un error.
UPDATE formulario_columnas
   SET campo_candidato = 'CIUDAD_EXPEDICION'
 WHERE clave = 'ev_lugar_exp'
   AND id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
                       WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'datos_evaluado');

INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, 'TEXTO', v.orden, NULL, FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('aca_estudio_sel', 'Estudio seleccionado para verificar (no se imprime)',       100),
    ('aca_decl',        'Lo que declaró el evaluado de ese estudio (no se imprime)', 101)
) AS v (clave, nombre, orden)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'estudio_verificado'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = v.clave);

INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, t.clave, t.nombre, t.ayuda, 0
FROM (VALUES
    ('ACA_DATO_NO_COINCIDE', 'Un dato del evaluado no coincide con el documento', 'El analista encontró que un dato del link del evaluado es distinto al del documento. El dato del link no se borra.'),
    ('ACA_CARGO_NO_COINCIDE', 'El cargo de la solicitud tiene un error', 'El analista avisa que el cargo escrito en la solicitud parece tener un error. El cargo de la solicitud no se modifica desde el formulario.')
) AS t (clave, nombre, ayuda)
JOIN categorias_novedad c ON c.clave = 'INFORMACION'
WHERE NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = t.clave);

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, 'ANALISTA_INTERNO'
FROM tipos_novedad tn
WHERE tn.clave IN ('ACA_DATO_NO_COINCIDE', 'ACA_CARGO_NO_COINCIDE')
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = 'ANALISTA_INTERNO');
