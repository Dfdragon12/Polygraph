-- La tabla «Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)» de Poligrafía Pre Empleo (V235) y de Visita domiciliaria presencial (V234) se creó con sus
-- columnas Ref./Hallazgo/Fuente/Detalle/Puntos calculadas pero SIN marcar la sección como calculada: el sistema no la llenaba y el constructor rechazaba guardar la
-- plantilla («ese resultado no aplica aquí»). Se marca como tabla que arma el sistema, igual que en las demás plantillas.
UPDATE formulario_secciones s
SET calculada = TRUE
WHERE s.clave = 'hallazgos' AND s.tipo = 'TABLA' AND s.origen_fijo IS NULL AND COALESCE(s.calculada, FALSE) = FALSE
  AND EXISTS (SELECT 1 FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion AND c.calculo = 'REF');
