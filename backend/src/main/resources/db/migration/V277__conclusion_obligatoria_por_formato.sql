-- PC-02 · La conclusión que lee el cliente sale del formato del ejecutor (ver ConclusionFormato.java):
--   · Antecedentes, Validación académica, laboral, personal y Análisis financiero: «Concepto del analista» (concepto_analista.concepto), ya obligatorio.
--   · Poligrafía Pre Empleo: «Conclusión» (firma.pol_conclusion) existía pero era opcional: pasa a ser obligatoria al enviar a revisión.
--   · Visita domiciliaria presencial: no tenía UNA conclusión (solo las de dinámica familiar, vivienda y economía): se agrega «Conclusión» (concepto_final.cf_conclusion), obligatoria.
-- No se borra ni se cambia ningún dato diligenciado. El Word de la visita no la imprime (no tiene el marcador): es lo que lee el cliente en la plataforma.

UPDATE formulario_columnas
SET obligatorio = TRUE
WHERE clave = 'pol_conclusion'
  AND id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
                     WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'firma');

-- Visita: «Conclusión» queda después de las tres conclusiones por aspecto (orden 6); lo que seguía se corre un lugar.
UPDATE formulario_columnas
SET orden = orden + 1
WHERE orden >= 6
  AND id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
                     WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'concepto_final')
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = formulario_columnas.id_seccion AND x.clave = 'cf_conclusion');

INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, 'cf_conclusion', 'Conclusión', 'TEXTO', 6, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3000, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'concepto_final'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = 'cf_conclusion');
