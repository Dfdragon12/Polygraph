-- PF-03 (2026-11-02, pedido del usuario): formato de poligrafía pre empleo. Cada pregunta de la entrevista se responde Sí / No sin valor por defecto; con "No" se
-- llena una frase estándar (editable) y con "Sí" se abren las listas cortas de esa pregunta.
-- 1) Dónde queda guardada la respuesta Sí / No: una columna LISTA por pregunta, que NO se imprime ni suma (no tiene marcador en el Word ni hallazgos).
--    Las listas que ya calificaban (al_*_si, ac_*, etc.) NO cambian: la pantalla las llena sola (Sí) o las deja en blanco (No).
-- 2) Frases estándar: tabla administrable con versión. Al responder "No" el texto se COPIA al campo, así que cambiar una frase no altera informes ya enviados.

INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, 'LISTA', v.orden, 'Sí' || CHR(10) || 'No', FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
JOIN (VALUES
    ('antecedentes_laborales', 'al_robos_resp', 'Robos en empleos previos – ¿Sí o No? (no se imprime)', 100),
    ('antecedentes_laborales', 'al_complicidad_resp', 'Complicidad en ilícitos – ¿Sí o No? (no se imprime)', 101),
    ('antecedentes_laborales', 'al_alteracion_resp', 'Alteración de documentos o sistemas – ¿Sí o No? (no se imprime)', 102),
    ('antecedentes_laborales', 'al_despido_resp', 'Despido por mala conducta – ¿Sí o No? (no se imprime)', 103),
    ('antecedentes_laborales', 'al_sobornos_resp', 'Sobornos o comisiones – ¿Sí o No? (no se imprime)', 104),
    ('areas', 'ac_falsificacion_resp', 'Falsificación de documentación – ¿Sí o No? (no se imprime)', 100),
    ('areas', 'ac_antecedentes_resp', 'Antecedentes – ¿Sí o No? (no se imprime)', 101),
    ('areas', 'ac_vinculos_resp', 'Vínculos – ¿Sí o No? (no se imprime)', 102),
    ('empleos_obs', 'ex_llamados_resp', 'Llamados de atención – ¿Sí o No? (no se imprime)', 100),
    ('empleos_obs', 'ex_descargos_resp', 'Descargos – ¿Sí o No? (no se imprime)', 101)
) AS v (seccion, clave, nombre, orden) ON v.seccion = s.clave
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.activo
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion AND c.clave = v.clave);

CREATE TABLE IF NOT EXISTS frases_estandar_formato (
    clave          VARCHAR(60)  PRIMARY KEY,
    formato        VARCHAR(60)  NOT NULL,
    descripcion    VARCHAR(200) NOT NULL,
    texto          VARCHAR(500) NOT NULL,
    version        INTEGER      NOT NULL DEFAULT 1,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    actualizado_en TIMESTAMP    NOT NULL DEFAULT NOW(),
    actualizado_por BIGINT
);

INSERT INTO frases_estandar_formato (clave, formato, descripcion, texto) VALUES
    ('robos', 'POLIGRAFIA_PRE_EMPLEO', 'Robos en empleos previos (responde No)', 'Manifiesta que nunca ha tomado dinero, mercancía, materiales ni información de sus empleadores.'),
    ('complicidad', 'POLIGRAFIA_PRE_EMPLEO', 'Complicidad en ilícitos (responde No)', 'Indica que no ha participado ni encubierto ilícitos en sus empleos.'),
    ('alteracion', 'POLIGRAFIA_PRE_EMPLEO', 'Alteración de documentos o sistemas (responde No)', 'Manifiesta que no ha alterado documentos, registros ni sistemas para inducir a error a sus empleadores.'),
    ('despido', 'POLIGRAFIA_PRE_EMPLEO', 'Despido por mala conducta (responde No)', 'Indica que no ha sido despedido de ningún empleo por mala conducta o faltas a las normas laborales.'),
    ('sobornos', 'POLIGRAFIA_PRE_EMPLEO', 'Sobornos o comisiones (responde No)', 'Manifiesta que no ha recibido sobornos ni comisiones por realizar su trabajo de forma irregular.'),
    ('alcohol', 'POLIGRAFIA_PRE_EMPLEO', 'Consumo de alcohol (frecuencia Nulo)', 'Manifiesta que no consume bebidas alcohólicas.'),
    ('drogas', 'POLIGRAFIA_PRE_EMPLEO', 'Consumo de drogas (frecuencia Nulo)', 'Manifiesta que nunca ha consumido sustancias psicoactivas.'),
    ('falsificacion', 'POLIGRAFIA_PRE_EMPLEO', 'Falsificación de documentación (responde No)', 'Manifiesta que los documentos aportados en el proceso son auténticos.'),
    ('antecedentes', 'POLIGRAFIA_PRE_EMPLEO', 'Antecedentes (responde No)', 'Indica que no tiene antecedentes judiciales ni procesos en curso.'),
    ('vinculos', 'POLIGRAFIA_PRE_EMPLEO', 'Vínculos (responde No)', 'Garantiza que no tiene vínculos con grupos al margen de la ley.'),
    ('llamados', 'POLIGRAFIA_PRE_EMPLEO', 'Llamados de atención (responde No)', 'No ha tenido llamados de atención en sus empleos.'),
    ('descargos', 'POLIGRAFIA_PRE_EMPLEO', 'Descargos (responde No)', 'No ha presentado descargos.'),
    ('obs_vacia', 'POLIGRAFIA_PRE_EMPLEO', 'Observaciones de educación y de vehículos (vacías)', 'Ninguna.')
ON CONFLICT (clave) DO NOTHING;
