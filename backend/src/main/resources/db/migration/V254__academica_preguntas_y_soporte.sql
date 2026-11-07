-- AC-03 (2026-11-05): Verificación académica, fase 2. Dos columnas nuevas en «5. Estudio verificado» que NO se imprimen:
--   aca_preguntas  las respuestas a las cinco preguntas que llevan al código (JSON); el código en sí sigue siendo aca_ref
--   aca_soporte    la respuesta de la institución o la consulta pública (archivo). Obligatorio para cerrar, salvo con ACA-04 / ALR-ACA-02 (ahí son las gestiones).
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, NULL, FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('aca_preguntas', 'Respuestas a las preguntas para el código (no se imprime)', 'TEXTO',   102),
    ('aca_soporte',   'Soporte de la verificación (no se imprime)',                'ARCHIVO', 103)
) AS v (clave, nombre, tipo_dato, orden)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'estudio_verificado'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = v.clave);
