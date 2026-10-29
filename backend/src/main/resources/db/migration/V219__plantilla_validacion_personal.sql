-- Plantilla de "Validación personal" (referencias personales) DESDE CERO (2026-10-22, pedido del usuario): crea el
-- subservicio si no existe, borra todo lo que tuviera (secciones, campos, filas diligenciadas, hallazgos por opción y el
-- Word cargado con su historial) y la crea de nuevo contando desde la versión 1, a partir de docs/Validacion Personal
-- (diccionario de marcadores, formato Word y motor_per.py). El analista marca UN código por referencia validada (r_ref)
-- y el estado de cada referencia declarada (rd_estado); el sistema arma la tabla de hallazgos, la fórmula, el puntaje, el
-- semáforo, la numeración, el promedio y la calificación de cada referencia, el promedio general y las notas.
-- Genera solo: PER-03 cuando el promedio de una referencia es menor a 3 (r_promedio, regla PER-03<3), PER-05 cuando hay
-- menos de 2 referencias validadas (per_validadas, regla PER-05<2) y PER-06 por el estado "Descartada – no cumple
-- requisitos" (cuenta una vez por estudio). PER-01, PER-02 y PER-03 acumulan por referencia; PER-05 y PER-06 cuentan una vez.
-- Orden de las secciones: las validadas van ANTES de las declaradas para que la fórmula salga como en motor_per.py
-- (códigos de cada referencia, luego PER-05, luego PER-06). Las filas de las declaradas nacen de las 4 referencias que
-- registró el candidato (CandidatoInfoService.PRECARGAS).
-- El Word hay que cargarlo de nuevo en Admin → Catálogo → Plantilla (esta migración borra el anterior).
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- El subservicio (si el admin ya lo creó con este nombre, no se toca).
INSERT INTO tipos_progreso (nombre_progreso, descripcion, orden, activo, rol_responsable, requiere_documento, version_plantilla)
SELECT 'Validacion Personal', 'Verificación de referencias personales del evaluado', (SELECT COALESCE(MAX(orden), 0) + 1 FROM tipos_progreso), TRUE, 'ANALISTA_INTERNO', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal');
UPDATE tipos_progreso SET requiere_documento = TRUE WHERE nombre_progreso = 'Validacion Personal';

-- Catálogo de hallazgos de referencias personales (docs/Validacion Personal/motor_per.py).
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta)
SELECT 'GENERAL', v.codigo, v.descripcion, v.puntos, v.critico, v.por_proceso, FALSE, TRUE, (SELECT COALESCE(MAX(orden), 0) FROM codigos_calificacion) + v.n, v.alerta
FROM (VALUES
    ('PER-01', 'Referencia falsa: no conoce al evaluado o no es la persona registrada', 70, FALSE, TRUE, FALSE, 1),
    ('PER-02', 'La referencia da información que contradice lo verificado (trabajo, residencia)', 40, FALSE, TRUE, FALSE, 2),
    ('PER-03', 'Concepto desfavorable: promedio de la escala menor a 3, o la referencia expresa reservas', 50, FALSE, TRUE, FALSE, 3),
    ('PER-05', 'No verificable: no hay 2 referencias válidas y contactables dentro de 4 días hábiles', 45, FALSE, FALSE, FALSE, 4),
    ('PER-06', 'Referencia presentada sin cumplir los requisitos (familiar, conviviente, menos de 1 año o sin contacto en 30 días)', 20, FALSE, FALSE, FALSE, 5),
    ('ALR-PER-01', 'La referencia no contesta (causa ajena al evaluado; se pasa a la siguiente)', 0, FALSE, FALSE, TRUE, 6)
) AS v (codigo, descripcion, puntos, critico, por_proceso, alerta, n)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- Borrar lo que existe, en orden, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE).
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal');
DELETE FROM formulario_plantilla_word_historial WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal');
DELETE FROM formulario_plantilla_config WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal');
-- Contar desde la versión 1.
UPDATE tipos_progreso t SET version_plantilla = 1, grupo_calificacion = 'HALLAZGOS' WHERE t.nombre_progreso = 'Validacion Personal';

-- 1. Datos del evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '1. Datos del evaluado', 'CAMPOS', 0, TRUE, 'datos_evaluado', NULL, NULL, t.version_plantilla, FALSE, 'Datos del evaluado' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('estudio_numero', 'N° de estudio', 'TEXTO', 0, NULL, FALSE, 'NUMERO_ESTUDIO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('fecha_emision', 'Fecha de emisión', 'TEXTO', 1, NULL, FALSE, 'FECHA_EMISION', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_cliente', 'Cliente', 'TEXTO', 2, NULL, FALSE, 'CLIENTE', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_cargo', 'Cargo', 'TEXTO', 3, NULL, FALSE, 'CARGO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_criticidad', 'Criticidad del cargo', 'LISTA', 5, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL, NULL, TRUE, 'CRITICIDAD', NULL, NULL, NULL, NULL, NULL),
    ('ev_telefono', 'Teléfono del candidato (no se imprime)', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'datos_evaluado';

-- 2. Resultado del servicio (lo calcula el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '2. Resultado del servicio (lo calcula el sistema)', 'CAMPOS', 1, TRUE, 'resultado_servicio', NULL, NULL, t.version_plantilla, FALSE, 'Resultado de la calificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTAJE', NULL, NULL, NULL, NULL),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FORMULA', 'CON_CODIGO', NULL, NULL, NULL),
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_BAJO', NULL, NULL, NULL, NULL),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_MEDIO', NULL, NULL, NULL, NULL),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_ALTO', NULL, NULL, NULL, NULL),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 5, NULL, FALSE, NULL, 'Las alertas (ALR-PER) no suman puntos; requieren revisión del analista antes del informe final.', FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('per_validadas', 'Referencias validadas (las cuenta el sistema; no se imprime)', 'TEXTO', 6, NULL, TRUE, NULL, NULL, FALSE, NULL, 'MINIMO_FILAS', 'referencias', 'PER-05<2', 'Referencias personales', '{#} de 2 referencias validadas'),
    ('per_promedio_general', 'Promedio general de las referencias (1 a 5)', 'TEXTO', 7, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PROMEDIO_TABLA', 'referencias', NULL, NULL, NULL),
    ('ref_nota', 'Nota de referencias (la arma el sistema)', 'TEXTO', 8, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'referencias', 'No fue posible validar referencias personales.', NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'resultado_servicio';

-- 3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 2, TRUE, 'hallazgos', NULL, NULL, t.version_plantilla, TRUE, 'Resultado de la calificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('h_ref', 'Ref.', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'REF', NULL, NULL, NULL, NULL),
    ('h_descripcion', 'Hallazgo', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'DESCRIPCION', NULL, NULL, NULL, NULL),
    ('h_fuente', 'Fuente', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FUENTE', NULL, NULL, NULL, NULL),
    ('h_detalle', 'Detalle', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'DETALLE', NULL, NULL, NULL, NULL),
    ('h_puntos', 'Puntos', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTOS', NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'hallazgos';

-- 4. Hallazgos sin impacto en el nivel de riesgo
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo', 'TABLA', 3, TRUE, 'informativos', NULL, NULL, t.version_plantilla, FALSE, 'Resultado de la calificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'informativos';

-- 5. Referencias personales validadas
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '5. Referencias personales validadas', 'TABLA', 4, TRUE, 'referencias', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('r_numero', 'Referencia N°', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NUMERO_FILA', NULL, NULL, NULL, NULL),
    ('r_nombre', 'Nombre', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_parentesco', 'Parentesco o relación', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_tiempo', 'Tiempo de conocerlo', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_ultima', 'Última comunicación', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_trabajo', '¿Sabe dónde trabaja?', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_trabajo_coincide', '¿Coincide con lo verificado? (trabajo)', 'LISTA', 6, 'Coincide' || CHR(10) || 'No coincide' || CHR(10) || 'No sabe', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_residencia', '¿Sabe dónde reside?', 'TEXTO', 7, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_residencia_coincide', '¿Coincide con lo verificado? (residencia)', 'LISTA', 8, 'Coincide' || CHR(10) || 'No coincide' || CHR(10) || 'No sabe', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_a1', 'Responsabilidad (1 a 5)', 'LISTA', 9, '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4' || CHR(10) || '5', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_a2', 'Honestidad (1 a 5)', 'LISTA', 10, '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4' || CHR(10) || '5', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_a3', 'Respeto (1 a 5)', 'LISTA', 11, '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4' || CHR(10) || '5', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_a4', 'Puntualidad (1 a 5)', 'LISTA', 12, '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4' || CHR(10) || '5', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_a5', 'Estabilidad familiar (1 a 5)', 'LISTA', 13, '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4' || CHR(10) || '5', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_concepto', 'Concepto de la referencia', 'TEXTO', 14, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('r_ref', 'Código (califica; no se imprime)', 'LISTA', 15, '—' || CHR(10) || 'PER-01' || CHR(10) || 'PER-02' || CHR(10) || 'PER-03', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, 'Referencia N° {r_numero}', '{r_nombre} ({r_parentesco})'),
    ('r_promedio', 'Promedio', 'TEXTO', 16, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PROMEDIO_FILA', 'r_a1,r_a2,r_a3,r_a4,r_a5', 'PER-03<3', 'Referencia N° {r_numero}', '{r_nombre} ({r_parentesco})'),
    ('r_calificacion', 'Calificación (la arma el sistema)', 'TEXTO', 17, NULL, TRUE, NULL, NULL, FALSE, NULL, 'CODIGOS_FILA', NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'referencias';

-- Referencias personales declaradas por el evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, 'Referencias personales declaradas por el evaluado', 'TABLA', 5, TRUE, 'referencias_declaradas', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('rd_nombre', 'Nombre', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('rd_parentesco', 'Parentesco o relación', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('rd_tiempo', 'Tiempo de conocimiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('rd_telefono', 'Teléfono (no se imprime)', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('rd_estado', 'Estado de la referencia (no se imprime)', 'LISTA', 4, 'Validada' || CHR(10) || 'Descartada – no cumple requisitos' || CHR(10) || 'Descartada – no contesta' || CHR(10) || 'Descartada – número errado no corregido' || CHR(10) || 'No contactada (no fue necesaria)', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'referencias_declaradas';

-- Gestiones realizadas (control interno, no se imprime)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, 'Gestiones realizadas (control interno, no se imprime)', 'TABLA', 6, TRUE, 'gestiones', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('g_referencia', 'Referencia declarada N°', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_fecha', 'Fecha', 'FECHA', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_medio', 'Medio', 'LISTA', 2, 'Llamada' || CHR(10) || 'WhatsApp / mensaje' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_resultado', 'Resultado', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'gestiones';

-- 6. Concepto del analista
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '6. Concepto del analista', 'CAMPOS', 7, TRUE, 'concepto_analista', NULL, NULL, t.version_plantilla, FALSE, 'Cierre' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('concepto', 'Concepto del analista', 'TEXTO', 0, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 1, NULL, FALSE, 'PROFESIONAL', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_fecha', 'Fecha', 'FECHA', 2, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Personal' AND s.clave = 'concepto_analista';

-- El teléfono del candidato sale solo de su ficha (para la alerta "teléfono de referencia igual al del candidato").
UPDATE formulario_columnas SET campo_candidato = 'CELULAR'
WHERE clave = 'ev_telefono' AND id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal') AND s.clave = 'datos_evaluado');

-- Hallazgo que genera una opción del estado de la referencia declarada: descartada por no cumplir los requisitos (PER-06, una vez por estudio).
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo
FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('rd_estado', 'Descartada – no cumple requisitos', 'PER-06')
) AS v (clave, opcion, codigo)
WHERE t.nombre_progreso = 'Validacion Personal' AND c.clave = v.clave;

-- Cómo sale ese hallazgo en la tabla (la cuenta de descartadas la pone el motor en {#}).
UPDATE formulario_columnas SET fuente_hallazgo = 'Referencias declaradas',
    detalle_hallazgo = '{#} referencia(s) descartada(s) por no cumplir los requisitos (cuenta una vez)'
WHERE clave = 'rd_estado' AND id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Personal') AND s.clave = 'referencias_declaradas');
