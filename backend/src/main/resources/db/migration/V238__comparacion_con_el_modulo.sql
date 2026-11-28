-- PF-04 (2026-11-02, pedido del usuario): en historia financiera (2 preguntas) y en antecedentes (3 preguntas) el formato imprime «Respondió en el módulo» frente a
-- «Manifestó en la prueba». El poligrafista ve lo que el candidato respondió en su link y elige «Lo confirma» o «Difiere».
-- Dónde queda guardada esa elección: una columna LISTA por pregunta, que NO se imprime ni suma (sin marcador en el Word ni hallazgos).

INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, 'LISTA', v.orden, 'Lo confirma' || CHR(10) || 'Difiere', FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
JOIN (VALUES
    ('historia_financiera', 'fd1_cmp', 'Obligaciones en mora o reportes negativos – ¿lo confirma o difiere? (no se imprime)', 100),
    ('historia_financiera', 'fd2_cmp', 'Embargos o cobro jurídico en curso – ¿lo confirma o difiere? (no se imprime)', 101),
    ('areas', 'pd1_cmp', 'Procesos judiciales y calidad – ¿lo confirma o difiere? (no se imprime)', 100),
    ('areas', 'pd2_cmp', 'Órdenes de captura o asuntos pendientes – ¿lo confirma o difiere? (no se imprime)', 101),
    ('areas', 'pd3_cmp', 'Sanciones disciplinarias o fiscales – ¿lo confirma o difiere? (no se imprime)', 102)
) AS v (seccion, clave, nombre, orden) ON v.seccion = s.clave
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.activo
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion AND c.clave = v.clave);
