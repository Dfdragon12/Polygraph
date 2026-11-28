-- LB-04 (2026-11-04): Verificación laboral. Los periodos de inactividad (más de 30 días sin vinculación dentro del periodo validado) los calcula la pantalla con las fechas
-- CONFIRMADAS de las vinculaciones y se guardan como filas de «7. Periodos de inactividad laboral». Dos columnas de control que no se imprimen:
--   in_origen   Calculado (lo arma el sistema) · Manual (lo agregó el analista, p. ej. «nunca trabajó formalmente») · Descartado (ya no hay inactividad en ese periodo; un analista no
--               puede borrar filas guardadas, así que la fila queda vacía y la pantalla no la muestra)
--   in_aviso    avisos de la fila: «Calculado con fecha declarada», «El periodo cambió…»
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, 'TEXTO', v.orden, NULL, FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('in_origen', 'Origen del periodo (no se imprime)', 5),
    ('in_aviso',  'Aviso del periodo (no se imprime)',  6)
) AS v (clave, nombre, orden)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'inactividad'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = v.clave);
