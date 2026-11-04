-- AN-06 (2026-11-03): Procesos judiciales de Antecedentes.
-- 1) LEG-05 (penal activo o con pena vigente, relacionado con el cargo) faltaba entre las opciones de la calificación de Rama Judicial; la hoja «Listas» del
--    diccionario la incluye (y las bases privadas ya la tenían).
-- 2) Columnas nuevas que NO se imprimen y no cambian el Word: radicado de las bases privadas (para detectar el mismo proceso en las dos fuentes), el soporte de
--    cada proceso, y las constancias «No se encontraron procesos» de cada fuente (sin ellas una tabla vacía no se distingue de una sección sin mirar).
-- También el tipo de proceso y el motivo de terminación de las bases privadas (como los de Rama Judicial), necesarios para sugerir el código. No toca ningún dato guardado.
UPDATE formulario_columnas
   SET opciones = replace(opciones, 'LEG-11', E'LEG-05\nLEG-11')
 WHERE clave = 'p_calificacion'
   AND opciones NOT LIKE '%LEG-05%'
   AND id_seccion IN (SELECT id_seccion FROM formulario_secciones WHERE id_tipo_progreso = 1 AND clave = 'procesos');

INSERT INTO formulario_columnas (id_seccion, nombre, tipo_dato, orden, activo, clave, opciones, califica_riesgo, calculado, obligatorio)
SELECT s.id_seccion, v.nombre, v.tipo_dato,
       COALESCE((SELECT MAX(c.orden) FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion), -1) + v.posicion,
       TRUE, v.clave, v.opciones, FALSE, FALSE, FALSE
FROM formulario_secciones s
JOIN (VALUES
    ('procesos',         'p_soporte',               'Soporte (no se imprime)',                      'ARCHIVO', NULL, 1),
    ('procesos_privados','pp_radicado',             'Radicado (no se imprime)',                     'TEXTO',   NULL, 1),
    ('procesos_privados','pp_soporte',              'Soporte (no se imprime)',                      'ARCHIVO', NULL, 2),
    ('notas_procesos',   'procesos_revisados',      'Rama Judicial revisada (no se imprime)',       'LISTA',   'Sí', 1),
    ('notas_procesos',   'procesos_priv_revisados', 'Bases privadas revisadas (no se imprime)',     'LISTA',   'Sí', 2)
) AS v (seccion, clave, nombre, tipo_dato, opciones, posicion) ON v.seccion = s.clave
WHERE s.id_tipo_progreso = 1
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = v.clave);

-- Tipo y motivo de las bases privadas: las mismas opciones que ya tiene Rama Judicial.
INSERT INTO formulario_columnas (id_seccion, nombre, tipo_dato, orden, activo, clave, opciones, califica_riesgo, calculado, obligatorio)
SELECT s.id_seccion, v.nombre, 'LISTA',
       COALESCE((SELECT MAX(c.orden) FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion), -1) + v.posicion,
       TRUE, v.clave,
       (SELECT o.opciones FROM formulario_columnas o JOIN formulario_secciones so ON so.id_seccion = o.id_seccion
         WHERE so.id_tipo_progreso = 1 AND so.clave = 'procesos' AND o.clave = v.origen AND o.activo LIMIT 1),
       FALSE, FALSE, FALSE
FROM formulario_secciones s
JOIN (VALUES
    ('pp_tipo',   'Tipo (no se imprime)',                   'p_tipo',   1),
    ('pp_motivo', 'Motivo de terminación (no se imprime)',  'p_motivo', 2)
) AS v (clave, nombre, origen, posicion) ON TRUE
WHERE s.id_tipo_progreso = 1 AND s.clave = 'procesos_privados'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = v.clave);
