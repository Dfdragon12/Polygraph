-- Clasificación de las secciones de una plantilla en grupos (2026-10-15, pedido del usuario): solo organiza
-- el índice del formulario y del constructor; ningún cálculo ni marcador del Word depende de ello.
ALTER TABLE formulario_secciones ADD COLUMN grupo VARCHAR(60);

-- Validación académica: los grupos de sus 8 secciones.
UPDATE formulario_secciones SET grupo = 'Datos del evaluado'
WHERE clave IN ('datos_evaluado', 'educacion')
  AND id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
UPDATE formulario_secciones SET grupo = 'Resultado de la calificación'
WHERE clave IN ('resultado_servicio', 'hallazgos', 'informativos')
  AND id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
UPDATE formulario_secciones SET grupo = 'Verificación'
WHERE clave IN ('estudio_verificado', 'gestiones')
  AND id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
UPDATE formulario_secciones SET grupo = 'Cierre'
WHERE clave = 'concepto_analista'
  AND id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
