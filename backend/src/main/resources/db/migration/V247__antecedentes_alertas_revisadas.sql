-- AN-05 (2026-11-03): en Identidad y contacto el analista puede dejar constancia de que revisó Reconocer / Datacrédito y NO hay alertas («Sin alertas»).
-- Sin ese dato una tabla vacía no se distingue de una sección sin mirar. La casilla no se imprime y no cambia el Word: la nota «Sin alertas reportadas.» la sigue
-- escribiendo el cálculo cuando la tabla está vacía.
INSERT INTO formulario_columnas (id_seccion, nombre, tipo_dato, orden, activo, clave, opciones, califica_riesgo, calculado, obligatorio)
SELECT s.id_seccion, 'Alertas revisadas (no se imprime)', 'LISTA',
       COALESCE((SELECT MAX(c.orden) FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion), -1) + 1,
       TRUE, 'alertas_revisadas', 'Sí', FALSE, FALSE, FALSE
FROM formulario_secciones s
WHERE s.id_tipo_progreso = 1 AND s.clave = 'identidad_contacto'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = 'alertas_revisadas');
