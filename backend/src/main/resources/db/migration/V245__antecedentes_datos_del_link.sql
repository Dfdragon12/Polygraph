-- AN-02 (2026-11-03): Antecedentes legales. (1) El lugar de expedición ya no se escribe a mano: viene del link del evaluado, como la fecha de expedición.
-- Los datos que alguien tecleó antes quedan guardados pero el informe imprime el del link. (2) Tipos de novedad para el analista: «el dato no coincide con el
-- documento» (corrección que avisa al gestor sin tocar el dato del link) y «el cargo no coincide con la solicitud».
UPDATE formulario_columnas
   SET campo_candidato = 'CIUDAD_EXPEDICION'
 WHERE clave = 'ev_lugar_exp'
   AND id_seccion IN (SELECT id_seccion FROM formulario_secciones WHERE id_tipo_progreso = 1 AND clave = 'datos_evaluado');

INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, t.clave, t.nombre, t.ayuda, 0
FROM (VALUES
    ('ANT_DATO_NO_COINCIDE', 'Un dato del evaluado no coincide con el documento', 'El analista encontró que un dato del link del evaluado es distinto al del documento. El dato del link no se borra.'),
    ('ANT_CARGO_NO_COINCIDE', 'El cargo no coincide con la solicitud', 'El analista avisa que el cargo real del evaluado es distinto al de la solicitud.')
) AS t (clave, nombre, ayuda)
JOIN categorias_novedad c ON c.clave = 'INFORMACION'
WHERE NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = t.clave);

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, 'ANALISTA_INTERNO'
FROM tipos_novedad tn
WHERE tn.clave IN ('ANT_DATO_NO_COINCIDE', 'ANT_CARGO_NO_COINCIDE')
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = 'ANALISTA_INTERNO');
