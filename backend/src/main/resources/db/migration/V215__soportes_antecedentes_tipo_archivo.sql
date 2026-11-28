-- Soportes de las listas de Antecedentes (secciones 7 y 8): la columna "Soporte" pasa de TEXTO a
-- ARCHIVO para que el analista suba la captura de la consulta. En el Word, la celda de la tabla
-- dice "Ver soporte N" (enlace) y la imagen sale completa en el anexo "Soportes de las
-- consultas" del final (SoportesAnexoWord).
-- Solo cambia las que sigan en TEXTO (idempotente; si el admin ya la cambió, no hace nada). Los
-- valores de texto que ya tenían ("Adjunto"...) se conservan: en el Word salen como "Anexo: <texto>".
UPDATE formulario_columnas c
SET tipo_dato = 'ARCHIVO'
FROM formulario_secciones s
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
WHERE c.id_seccion = s.id_seccion
  AND t.grupo_calificacion IS NOT NULL
  AND s.clave IN ('listas_nacionales', 'listas_internacionales')
  AND c.clave IN ('ln_soporte', 'li_soporte')
  AND c.tipo_dato = 'TEXTO';
