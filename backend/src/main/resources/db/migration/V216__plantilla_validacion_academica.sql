-- Plantilla de "Validación académica" DESDE CERO (2026-10-15, pedido del usuario): borra todo lo que tenía
-- el subservicio (secciones, campos, filas diligenciadas, hallazgos por opción y el Word cargado con su
-- historial) y la crea de nuevo contando desde la versión 1, a partir de docs/Validacion Academica
-- (diccionario de marcadores, formato Word y motor_aca.py). Califica por hallazgos como Antecedentes: el
-- analista escoge UN código en "Calificación" (aca_ref) y el sistema arma la tabla, la fórmula, el puntaje
-- y el semáforo. Lo que NO se imprime (criticidad, sustento del ACA-01, fechas del plazo y las gestiones) se
-- guarda para control interno.
-- El Word hay que cargarlo de nuevo en Admin → Catálogo → Plantilla (esta migración borra el anterior).
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- Catálogo de hallazgos de la académica (docs/Validacion Academica/motor_aca.py).
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta)
SELECT 'GENERAL', v.codigo, v.descripcion, v.puntos, v.critico, FALSE, FALSE, TRUE, (SELECT COALESCE(MAX(orden), 0) FROM codigos_calificacion) + v.n, v.alerta
FROM (VALUES
    ('ACA-01', 'Documento falso o adulterado', CAST(NULL AS INTEGER), TRUE, FALSE, 1),
    ('ACA-02', 'La institución no confirma el título declarado', 70, FALSE, FALSE, 2),
    ('ACA-03', 'Datos que no coinciden con lo declarado (diferencia de fondo)', 45, FALSE, FALSE, 3),
    ('ACA-04', 'No verificable: el evaluado no aportó lo solicitado dentro del plazo', 45, FALSE, FALSE, 4),
    ('ACA-05', 'Institución no reconocida o título sin validez en Colombia', 30, FALSE, FALSE, 5),
    ('ALR-ACA-01', 'Indicios de alteración sin confirmar', 0, FALSE, TRUE, 6),
    ('ALR-ACA-02', 'Verificación pendiente por causa ajena al evaluado', 0, FALSE, TRUE, 7)
) AS v (codigo, descripcion, puntos, critico, alerta, n)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- Borrar lo que existe, en orden, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE).
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
DELETE FROM formulario_plantilla_word_historial WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
DELETE FROM formulario_plantilla_config WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica');
-- Contar desde la versión 1.
UPDATE tipos_progreso t SET version_plantilla = 1, grupo_calificacion = 'HALLAZGOS' WHERE t.nombre_progreso = 'Validacion Academica';

-- 1. Datos del evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, '1. Datos del evaluado', 'CAMPOS', 0, TRUE, 'datos_evaluado', NULL, NULL, t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('estudio_numero', 'N° de estudio', 'TEXTO', 0, NULL, FALSE, 'NUMERO_ESTUDIO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('fecha_emision', 'Fecha de emisión', 'TEXTO', 1, NULL, FALSE, 'FECHA_EMISION', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_cliente', 'Cliente', 'TEXTO', 2, NULL, FALSE, 'CLIENTE', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_cargo', 'Cargo', 'TEXTO', 3, NULL, FALSE, 'CARGO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_criticidad', 'Criticidad del cargo', 'LISTA', 5, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL, NULL, TRUE, 'CRITICIDAD', NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'datos_evaluado';

-- Información académica declarada por el evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, 'Información académica declarada por el evaluado', 'TABLA', 1, TRUE, 'educacion', 'educacion', 'fecha_inicio,en_curso,ciudad,en_exterior,pais_exterior', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';

-- 2. Resultado del servicio (lo calcula el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, '2. Resultado del servicio (lo calcula el sistema)', 'CAMPOS', 2, TRUE, 'resultado_servicio', NULL, NULL, t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTAJE', NULL, NULL, NULL, NULL),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FORMULA', NULL, NULL, NULL, NULL),
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_BAJO', NULL, NULL, NULL, NULL),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_MEDIO', NULL, NULL, NULL, NULL),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_ALTO', NULL, NULL, NULL, NULL),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 5, NULL, FALSE, NULL, 'Las alertas (ALR-ACA) no suman puntos; requieren revisión del analista antes del informe final.', FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'resultado_servicio';

-- 3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 3, TRUE, 'hallazgos', NULL, NULL, t.version_plantilla, TRUE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
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
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'hallazgos';

-- 4. Hallazgos sin impacto en el nivel de riesgo
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo', 'TABLA', 4, TRUE, 'informativos', NULL, NULL, t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'informativos';

-- 5. Estudio verificado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, '5. Estudio verificado', 'CAMPOS', 5, TRUE, 'estudio_verificado', NULL, NULL, t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('aca_criterio', 'Criterio de verificación', 'LISTA', 0, 'Último estudio finalizado' || CHR(10) || 'Por solicitud del cliente' || CHR(10) || 'Último estudio no verificable en el plazo', FALSE, NULL, 'Último estudio finalizado', TRUE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_institucion', 'Institución', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_ciudad', 'Ciudad (y país si es del exterior)', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_categoria', 'Categoría', 'LISTA', 3, 'Educación básica y media' || CHR(10) || 'Educación superior' || CHR(10) || 'Educación para el trabajo y el desarrollo humano (SENA, ETDH)' || CHR(10) || 'Estudios en el exterior', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_nivel', 'Nivel específico', 'LISTA', 4, 'Bachiller' || CHR(10) || 'Técnico laboral' || CHR(10) || 'Técnico profesional' || CHR(10) || 'Tecnólogo' || CHR(10) || 'Profesional' || CHR(10) || 'Especialización' || CHR(10) || 'Maestría' || CHR(10) || 'Doctorado', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_fecha_inicio', 'Fecha de inicio', 'FECHA', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_fecha_fin', 'Fecha de finalización', 'FECHA', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_titulo', 'Título obtenido', 'TEXTO', 7, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_estado', 'Estado del estudio', 'LISTA', 8, 'Graduado' || CHR(10) || 'Terminó materias, pendiente de grado' || CHR(10) || 'En curso' || CHR(10) || 'Retirado sin graduarse' || CHR(10) || 'Aplazado', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_documento', 'Documento aportado', 'LISTA', 9, 'Diploma' || CHR(10) || 'Acta de grado' || CHR(10) || 'Certificado de terminación de materias' || CHR(10) || 'Certificado de estudios' || CHR(10) || 'Tarjeta profesional' || CHR(10) || 'Ninguno', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_medio', 'Medio de verificación', 'LISTA', 10, 'Base de datos de la institución' || CHR(10) || 'Página web o consulta pública' || CHR(10) || 'Código QR del documento' || CHR(10) || 'Correo electrónico' || CHR(10) || 'Llamada telefónica' || CHR(10) || 'Chat institucional' || CHR(10) || 'ICFES' || CHR(10) || 'Consejo profesional (COPNIA, JCC, otros)' || CHR(10) || 'SENA (certificados / Sofía Plus)' || CHR(10) || 'Ministerio de Educación (SNIES / convalidaciones)' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_contacto', 'Nombre del contacto', 'TEXTO', 11, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_contacto_cargo', 'Cargo del contacto', 'TEXTO', 12, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_contacto_tel', 'Teléfono / correo', 'TEXTO', 13, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_fecha_respuesta', 'Fecha de respuesta', 'FECHA', 14, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_ref', 'Calificación', 'LISTA', 15, '—' || CHR(10) || 'ACA-01' || CHR(10) || 'ACA-02' || CHR(10) || 'ACA-03' || CHR(10) || 'ACA-04' || CHR(10) || 'ACA-05' || CHR(10) || 'ALR-ACA-01' || CHR(10) || 'ALR-ACA-02', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, '{aca_medio}', '{aca_titulo} – {aca_institucion}'),
    ('aca_vobo', 'Visto bueno 2.º analista', 'TEXTO', 16, NULL, FALSE, NULL, '—', FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_observaciones', 'Observaciones', 'TEXTO', 17, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_ac01_base', 'Sustento del ACA-01 (no se imprime)', 'LISTA', 18, 'Confirmado por la institución' || CHR(10) || 'Alteración evidente en inspección visual', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_fecha_sol_eval', 'Fecha de solicitud al evaluado (no se imprime)', 'FECHA', 19, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('aca_fecha_limite', 'Fecha límite, 4 días hábiles (no se imprime)', 'FECHA', 20, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'estudio_verificado';

-- 6. Concepto del analista
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, '6. Concepto del analista', 'CAMPOS', 6, TRUE, 'concepto_analista', NULL, NULL, t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('concepto', 'Concepto del analista', 'TEXTO', 0, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 1, NULL, FALSE, 'PROFESIONAL', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_fecha', 'Fecha', 'FECHA', 2, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'concepto_analista';

-- Gestiones realizadas (control interno, no se imprime)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada) SELECT t.id_tipo_progreso, 'Gestiones realizadas (control interno, no se imprime)', 'TABLA', 7, TRUE, 'gestiones', NULL, NULL, t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Academica';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('g_fecha', 'Fecha', 'FECHA', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_medio', 'Medio', 'LISTA', 1, 'Llamada' || CHR(10) || 'Correo electrónico' || CHR(10) || 'Chat' || CHR(10) || 'Página web' || CHR(10) || 'Presencial' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_destino', 'Dirigida a', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_resultado', 'Gestión y respuesta', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Academica' AND s.clave = 'gestiones';

