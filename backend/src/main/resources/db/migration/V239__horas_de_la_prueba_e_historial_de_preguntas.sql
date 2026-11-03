-- PF-05 (2026-11-02, pedido del usuario): fase «La prueba» del formato de poligrafía pre empleo.
-- 1) Hora de inicio y de fin de la prueba: la plantilla no tenía dónde capturarlas. Dos campos de texto (HH:mm) que NO se imprimen: el Word
--    actual no trae marcador para las horas (hay que agregarlos al .docx si se quieren imprimir).
-- 2) Registro de cambios de las preguntas relevantes (qué, quién, cuándo): solo inserción. Nace del texto que el cliente escogió; cada vez que el
--    poligrafista cambia el texto de una pregunta guardada, o agrega una, queda una fila.

INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, calculado, obligatorio, activo, califica_riesgo, longitud_maxima)
SELECT s.id_seccion, v.clave, v.nombre, 'TEXTO', v.orden, FALSE, FALSE, TRUE, FALSE, 5
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
JOIN (VALUES
    ('datos_prueba', 'pol_hora_inicio', 'Hora de inicio de la prueba (no se imprime)', 100),
    ('datos_prueba', 'pol_hora_fin', 'Hora de fin de la prueba (no se imprime)', 101)
) AS v (seccion, clave, nombre, orden) ON v.seccion = s.clave
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.activo
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion AND c.clave = v.clave);

CREATE TABLE IF NOT EXISTS preguntas_relevantes_historial (
    id              BIGSERIAL PRIMARY KEY,
    id_formulario   INTEGER      NOT NULL REFERENCES formularios (id_formulario) ON DELETE CASCADE,
    posicion        INTEGER      NOT NULL,
    texto_anterior  TEXT,
    texto_nuevo     TEXT         NOT NULL,
    id_usuario      BIGINT,
    nombre_usuario  VARCHAR(200),
    fecha           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_preguntas_hist_formulario ON preguntas_relevantes_historial (id_formulario);
