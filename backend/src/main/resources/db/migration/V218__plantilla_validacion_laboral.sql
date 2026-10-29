-- Plantilla de "Validación laboral" DESDE CERO (2026-10-15, pedido del usuario): borra todo lo que tenía el
-- subservicio (secciones, campos, filas diligenciadas, hallazgos por opción y el Word cargado con su historial)
-- y la crea de nuevo contando desde la versión 1, a partir de docs/Validacion Laboral (diccionario de
-- marcadores, formato Word y motor_lab.py). El analista marca hasta dos códigos por vinculación (v_ref1, v_ref2)
-- y cómo se corroboró cada periodo de inactividad; el sistema arma la tabla de hallazgos, la fórmula, el
-- puntaje, el semáforo, la numeración, la calificación de cada vinculación y las notas.
-- Los códigos LAB-01..12 acumulan por vinculación (por_proceso); LAB-13 y LAB-14 cuentan una vez por estudio.
-- El Word hay que cargarlo de nuevo en Admin → Catálogo → Plantilla (esta migración borra el anterior).
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- Catálogo de hallazgos de la laboral (docs/Validacion Laboral/motor_lab.py).
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta)
SELECT 'GENERAL', v.codigo, v.descripcion, v.puntos, v.critico, v.por_proceso, FALSE, TRUE, (SELECT COALESCE(MAX(orden), 0) FROM codigos_calificacion) + v.n, v.alerta
FROM (VALUES
    ('LAB-01', 'Certificado laboral falso o adulterado', CAST(NULL AS INTEGER), TRUE, TRUE, FALSE, 1),
    ('LAB-02', 'La empresa no reconoce la vinculación declarada', 70, FALSE, TRUE, FALSE, 2),
    ('LAB-03', 'Datos que no coinciden de fondo (fechas, cargo o tipo de contrato)', 60, FALSE, TRUE, FALSE, 3),
    ('LAB-04', 'El motivo de retiro declarado no coincide con el confirmado', 45, FALSE, TRUE, FALSE, 4),
    ('LAB-05', 'Despido con justa causa', 70, FALSE, TRUE, FALSE, 5),
    ('LAB-06', 'Abandono del cargo', 60, FALSE, TRUE, FALSE, 6),
    ('LAB-07', 'Llamados de atención por faltas de cumplimiento', 50, FALSE, TRUE, FALSE, 7),
    ('LAB-08', 'Llamados o sanciones por faltas de conducta o confianza', 70, FALSE, TRUE, FALSE, 8),
    ('LAB-09', 'Persona natural o informal: concepto "malo" o no lo volvería a contratar', 50, FALSE, TRUE, FALSE, 9),
    ('LAB-10', 'Experiencia omitida en la hoja de vida (más de 30 días), sin explicación razonable', 40, FALSE, TRUE, FALSE, 10),
    ('LAB-11', 'No verificable: el evaluado no aportó el certificado solicitado en 4 días hábiles', 50, FALSE, TRUE, FALSE, 11),
    ('LAB-12', 'Persona natural o informal: concepto "regular" del jefe', 30, FALSE, TRUE, FALSE, 12),
    ('LAB-13', 'Periodo de inactividad no corroborado', 30, FALSE, FALSE, FALSE, 13),
    ('LAB-14', 'Periodo de inactividad sustentado solo con la declaración del evaluado', 10, FALSE, FALSE, FALSE, 14),
    ('ALR-LAB-01', 'Verificación pendiente por causa ajena al evaluado (la empresa no responde y no hay sábana ni aportes)', 0, FALSE, FALSE, TRUE, 15)
) AS v (codigo, descripcion, puntos, critico, por_proceso, alerta, n)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- Borrar lo que existe, en orden, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE).
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral');
DELETE FROM formulario_plantilla_word_historial WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral');
DELETE FROM formulario_plantilla_config WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral');
-- Contar desde la versión 1.
UPDATE tipos_progreso t SET version_plantilla = 1, grupo_calificacion = 'HALLAZGOS' WHERE t.nombre_progreso = 'Validacion Laboral';

-- 1. Datos del evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '1. Datos del evaluado', 'CAMPOS', 0, TRUE, 'datos_evaluado', NULL, NULL, t.version_plantilla, FALSE, 'Datos del evaluado' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
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
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'datos_evaluado';

-- Experiencia laboral declarada por el evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, 'Experiencia laboral declarada por el evaluado', 'TABLA', 1, TRUE, 'experiencia_laboral', 'experiencia_laboral', 'ciudad,telefono_empresa,motivo_retiro,nombre_jefe,cargo_jefe,tipo_contrato,salario,llamados_atencion,contratado_por_temporal,nombre_temporal,labora_actualmente', t.version_plantilla, FALSE, 'Datos del evaluado' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';

-- 2. Resultado del servicio (lo calcula el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '2. Resultado del servicio (lo calcula el sistema)', 'CAMPOS', 2, TRUE, 'resultado_servicio', NULL, NULL, t.version_plantilla, FALSE, 'Resultado de la calificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTAJE', NULL, NULL, NULL, NULL),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FORMULA', 'CON_CODIGO', NULL, NULL, NULL),
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_BAJO', NULL, NULL, NULL, NULL),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_MEDIO', NULL, NULL, NULL, NULL),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_ALTO', NULL, NULL, NULL, NULL),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 5, NULL, FALSE, NULL, 'Las alertas (ALR-LAB) no suman puntos; requieren revisión del analista antes del informe final.', FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'resultado_servicio';

-- 3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 3, TRUE, 'hallazgos', NULL, NULL, t.version_plantilla, TRUE, 'Resultado de la calificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
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
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'hallazgos';

-- 4. Hallazgos sin impacto en el nivel de riesgo
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo', 'TABLA', 4, TRUE, 'informativos', NULL, NULL, t.version_plantilla, FALSE, 'Resultado de la calificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'informativos';

-- 5. Alcance de la verificación
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '5. Alcance de la verificación', 'CAMPOS', 5, TRUE, 'alcance', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('lab_periodo_desde', 'Periodo validado desde (mes y año)', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('lab_periodo_hasta', 'Periodo validado hasta (mes y año)', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('vinc_nota', 'Nota de vinculaciones (la arma el sistema)', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'vinculaciones', 'El evaluado no registra vinculaciones laborales en el periodo validado.', NULL, NULL),
    ('inactividad_nota', 'Nota de inactividad (la arma el sistema)', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'inactividad', 'No se identificaron periodos de inactividad superiores a 30 días en el periodo validado.', NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'alcance';

-- 6. Vinculaciones laborales
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '6. Vinculaciones laborales', 'TABLA', 6, TRUE, 'vinculaciones', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('v_numero', 'Vinculación N°', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NUMERO_FILA', NULL, NULL, NULL, NULL),
    ('v_empresa', 'Empresa', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_ciudad', 'Ciudad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_fecha_inicio', 'Fecha de inicio (la confirmada)', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_fecha_retiro', 'Fecha de retiro ("Actualmente" si sigue vinculado)', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_periodos', 'Periodos de vinculación (uno por línea; "—" si fue uno solo)', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_tipo_contrato', 'Tipo de contrato', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_cargo', 'Cargo desempeñado', 'TEXTO', 7, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_salario', 'Salario devengado', 'TEXTO', 8, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_llamados', 'Llamados de atención', 'TEXTO', 9, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_motivo', 'Motivo del retiro (lo que informa la empresa)', 'TEXTO', 10, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_contacto', 'Nombre del contacto', 'TEXTO', 11, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_contacto_cargo', 'Cargo del contacto', 'TEXTO', 12, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_telefono', 'Teléfono empresa', 'TEXTO', 13, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_estado_empresa', 'Estado actual de la empresa (RUES)', 'TEXTO', 14, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_validado_por', 'Validado por', 'LISTA', 15, 'La empresa' || CHR(10) || 'Jefe inmediato (persona natural o informal)' || CHR(10) || 'Sábana pensional o bases de datos' || CHR(10) || 'Pendiente', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_calificacion', 'Calificación (la arma el sistema)', 'TEXTO', 16, NULL, TRUE, NULL, NULL, FALSE, NULL, 'CODIGOS_FILA', NULL, NULL, NULL, NULL),
    ('v_observaciones', 'Observaciones', 'TEXTO', 17, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_nota', 'Nota de alcance (la arma el sistema)', 'TEXTO', 18, NULL, TRUE, NULL, NULL, FALSE, NULL, 'TEXTO_SI', 'v_validado_por=Sábana pensional o bases de datos', 'Se confirma la vinculación por medio de bases de datos públicas y privadas. No fue posible validar motivo de retiro, llamados de atención ni la veracidad del certificado.', NULL, NULL),
    ('v_tipo', 'Tipo de vinculación (no se imprime)', 'LISTA', 19, 'Empresa' || CHR(10) || 'Persona natural o informal', FALSE, NULL, 'Empresa', FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_motivo_clase', 'Clasificación del motivo de retiro (no se imprime)', 'LISTA', 20, 'Renuncia voluntaria' || CHR(10) || 'Terminación o fin de contrato' || CHR(10) || 'Mutuo acuerdo' || CHR(10) || 'Despido sin justa causa' || CHR(10) || 'Despido con justa causa' || CHR(10) || 'Abandono del cargo' || CHR(10) || 'Vigente (aún vinculado)' || CHR(10) || 'No informado', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_ref1', 'Código 1 (califica; no se imprime)', 'LISTA', 21, '—' || CHR(10) || 'LAB-01' || CHR(10) || 'LAB-02' || CHR(10) || 'LAB-03' || CHR(10) || 'LAB-04' || CHR(10) || 'LAB-05' || CHR(10) || 'LAB-06' || CHR(10) || 'LAB-07' || CHR(10) || 'LAB-08' || CHR(10) || 'LAB-09' || CHR(10) || 'LAB-10' || CHR(10) || 'LAB-11' || CHR(10) || 'LAB-12' || CHR(10) || 'ALR-LAB-01', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, '{v_validado_por}', 'Vinculación N° {v_numero}: {v_empresa} ({v_cargo})'),
    ('v_ref2', 'Código 2 (califica; no se imprime)', 'LISTA', 22, '—' || CHR(10) || 'LAB-01' || CHR(10) || 'LAB-02' || CHR(10) || 'LAB-03' || CHR(10) || 'LAB-04' || CHR(10) || 'LAB-05' || CHR(10) || 'LAB-06' || CHR(10) || 'LAB-07' || CHR(10) || 'LAB-08' || CHR(10) || 'LAB-09' || CHR(10) || 'LAB-10' || CHR(10) || 'LAB-11' || CHR(10) || 'LAB-12' || CHR(10) || 'ALR-LAB-01', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, '{v_validado_por}', 'Vinculación N° {v_numero}: {v_empresa} ({v_cargo})'),
    ('v_lab01_base', 'Sustento del LAB-01 (no se imprime)', 'LISTA', 23, 'Confirmado por la empresa' || CHR(10) || 'Alteración evidente en inspección visual', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_vobo', 'Visto bueno 2.º analista (no se imprime)', 'TEXTO', 24, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_fecha_sol_eval', 'Fecha de solicitud del certificado al evaluado (no se imprime)', 'FECHA', 25, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_fecha_limite', 'Fecha límite, 4 días hábiles (no se imprime)', 'FECHA', 26, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_seguimiento', 'Nota de seguimiento (no se imprime)', 'TEXTO', 27, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_decl_empresa', 'Empresa declarada (no se imprime)', 'TEXTO', 28, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_decl_cargo', 'Cargo declarado (no se imprime)', 'TEXTO', 29, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_decl_inicio', 'Inicio declarado (no se imprime)', 'FECHA', 30, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_decl_fin', 'Fin declarado (no se imprime)', 'FECHA', 31, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('v_decl_motivo', 'Motivo de retiro declarado (no se imprime)', 'TEXTO', 32, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'vinculaciones';

-- 7. Periodos de inactividad laboral
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '7. Periodos de inactividad laboral', 'TABLA', 7, TRUE, 'inactividad', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('in_desde', 'Desde', 'FECHA', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('in_hasta', 'Hasta', 'FECHA', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('in_dias', 'Días', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('in_actividad', 'Actividad declarada', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('in_corroboracion', 'Cómo se corroboró', 'LISTA', 4, 'Referencia confirmó' || CHR(10) || 'Aportes como independiente' || CHR(10) || 'Solo declaración del evaluado' || CHR(10) || 'No se pudo corroborar', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Periodos de inactividad', 'Periodos de inactividad: {in_corroboracion} (cuenta una vez por estudio)')
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'inactividad';

-- Observaciones de la inactividad
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, 'Observaciones de la inactividad', 'CAMPOS', 8, TRUE, 'obs_inactividad', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('inactividad_obs', 'Observaciones de inactividad', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'obs_inactividad';

-- Gestiones realizadas (control interno, no se imprime)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, 'Gestiones realizadas (control interno, no se imprime)', 'TABLA', 9, TRUE, 'gestiones', NULL, NULL, t.version_plantilla, FALSE, 'Verificación' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('g_vinculacion', 'Vinculación N°', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_fecha', 'Fecha', 'FECHA', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_medio', 'Medio', 'LISTA', 2, 'Llamada' || CHR(10) || 'Correo electrónico' || CHR(10) || 'WhatsApp / mensaje' || CHR(10) || 'Página web' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_destino', 'Dirigida a', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('g_resultado', 'Gestión y respuesta', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'gestiones';

-- 8. Concepto del analista
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo) SELECT t.id_tipo_progreso, '8. Concepto del analista', 'CAMPOS', 10, TRUE, 'concepto_analista', NULL, NULL, t.version_plantilla, FALSE, 'Cierre' FROM tipos_progreso t WHERE t.nombre_progreso = 'Validacion Laboral';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('concepto', 'Concepto del analista', 'TEXTO', 0, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 1, NULL, FALSE, 'PROFESIONAL', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_fecha', 'Fecha', 'FECHA', 2, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'concepto_analista';

-- Hallazgo que genera cada opción de la corroboración de la inactividad (una vez por estudio cada uno).
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo
FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('in_corroboracion', 'Solo declaración del evaluado', 'LAB-14'),
    ('in_corroboracion', 'No se pudo corroborar', 'LAB-13')
) AS v (clave, opcion, codigo)
WHERE t.nombre_progreso = 'Validacion Laboral' AND c.clave = v.clave;
