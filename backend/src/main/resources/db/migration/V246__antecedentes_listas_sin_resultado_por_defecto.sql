-- AN-03 (2026-11-03): en las consultas en listas (secciones 7 y 8 de Antecedentes) el resultado ya no sale «Sin novedad» por defecto: lo marca el analista.
-- El resultado pasa a ser obligatorio en cada fila y el soporte de las internacionales también (el de las nacionales ya lo era). NO toca ningún dato guardado:
-- las filas que ya tienen «Sin novedad» guardado siguen igual hasta que se revisen aparte.
UPDATE formulario_columnas
   SET valor_defecto = NULL, obligatorio = TRUE
 WHERE clave IN ('ln_resultado', 'li_resultado')
   AND id_seccion IN (SELECT id_seccion FROM formulario_secciones WHERE id_tipo_progreso = 1 AND clave IN ('listas_nacionales', 'listas_internacionales'));

UPDATE formulario_columnas
   SET obligatorio = TRUE
 WHERE clave = 'li_soporte'
   AND id_seccion IN (SELECT id_seccion FROM formulario_secciones WHERE id_tipo_progreso = 1 AND clave = 'listas_internacionales');
