-- Plantilla de "Poligrafia Pre Empleo" (2026-10-29, pedido del usuario): crea el formulario DESDE CERO sobre el subservicio que YA existe (id 11) a partir
-- de docs/Poligrafia Pre Empleo (diccionario de marcadores, formato Word y motor_pol.py). Borra las 3 secciones que tenía (RESULTADO, FOTO y DATOS CANDIDATO)
-- y sus datos. El Word NO se cambia aquí (la ruta no es portable entre ambientes): hay que subir 1_Formato_Poligrafia_PreEmpleo.docx en Admin -> Catálogo -> Plantilla.
-- Califica con el catálogo de hallazgos genérico: cada pregunta Sí/No o lista que dispara un código POL (o ALR-POL) queda mapeada por opción. El poligrafista no escribe códigos.
-- PENDIENTE (segunda fase, motor propio como MotorFinanciero, motor_pol.py manda): resultado general calculado a partir de las preguntas, POL-29 por antigüedad o menor
-- de edad, POL-27 en cargos de conducción, POL-21/22 según cuándo, regla del servicio no contratado (LEG, LAB-15 y FIN declarados), rotación, FIN-08, avisos a otros
-- servicios, copia automática de las respuestas a Admisiones, precarga de las preguntas relevantes del cliente y registro de cambios. Hoy esos pasos los hace el poligrafista a mano.
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- Catálogo de hallazgos de la poligrafía (docs/Poligrafia Pre Empleo/motor_pol.py). Críticos: sin puntos y marcados críticos (el servicio queda en 100). Cuentan una vez por estudio.
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta)
SELECT 'GENERAL', v.codigo, v.descripcion, v.puntos, v.critico, FALSE, FALSE, TRUE, (SELECT COALESCE(MAX(orden), 0) FROM codigos_calificacion) + v.n, v.alerta
FROM (VALUES
    ('POL-01', 'Resultado de la prueba: No veraz', NULL::integer, FALSE, TRUE, 1),
    ('POL-02', 'Contramedidas o intento deliberado de manipular la prueba', 60, FALSE, FALSE, 2),
    ('POL-03', 'Dificultad para seguir las instrucciones durante el examen', 15, FALSE, FALSE, 3),
    ('POL-04', 'Robo en empleos (dinero, mercancía, materiales o información) por un valor total igual o mayor a un salario mínimo, o más de dos veces', NULL::integer, FALSE, TRUE, 4),
    ('POL-05', 'Robo en empleos de baja cuantía: una o dos veces y por un valor total menor a un salario mínimo', 50, FALSE, FALSE, 5),
    ('POL-06', 'Recibir sobornos o comisiones por trabajar de forma irregular', NULL::integer, FALSE, TRUE, 6),
    ('POL-07', 'Complicidad en ilícitos contra un empleador', NULL::integer, FALSE, TRUE, 7),
    ('POL-08', 'Alteración u ocultamiento intencional de documentos, registros o información', NULL::integer, FALSE, TRUE, 8),
    ('POL-09', 'Despido por mala conducta o faltas graves', 35, FALSE, FALSE, 9),
    ('POL-10', 'Abandono del cargo o retiros sin aviso (dejó de presentarse o se fue sin entregar el cargo)', 35, FALSE, FALSE, 10),
    ('POL-11', 'Conflicto de interés no declarado: negocios, sociedad o trabajo con proveedores, clientes o competencia del empleador', 35, FALSE, FALSE, 11),
    ('POL-12', 'Conductas agresivas o violentas sin relación con consumo: riñas frecuentes, agresiones a compañeros o jefes', 40, FALSE, FALSE, 12),
    ('POL-13', 'Uso de títulos, certificados o documentos falsos o comprados', NULL::integer, FALSE, TRUE, 13),
    ('POL-14', 'Mintió en la hoja de vida o en el proceso de selección', 45, FALSE, FALSE, 14),
    ('POL-15', 'Beneficio económico de actividades ilícitas', NULL::integer, FALSE, TRUE, 15),
    ('POL-16', 'Participación en venta, transporte o tráfico de drogas', NULL::integer, FALSE, TRUE, 16),
    ('POL-17', 'Vínculo activo con grupos ilegales o delincuencia organizada', NULL::integer, FALSE, TRUE, 17),
    ('POL-18', 'Otro delito admitido (por ejemplo, porte ilegal de armas)', NULL::integer, FALSE, TRUE, 18),
    ('POL-19', 'Relación social actual con personas dedicadas a actividades ilícitas, sin participar', 45, FALSE, FALSE, 19),
    ('POL-20', 'Vínculo pasado con personas dedicadas a actividades ilícitas, ya terminado', 15, FALSE, FALSE, 20),
    ('POL-21', 'Consumo frecuente de drogas que puede afectar su trabajo o sus funciones (último año)', 60, FALSE, FALSE, 21),
    ('POL-22', 'Consumo ocasional de drogas en los últimos 5 años', 45, FALSE, FALSE, 22),
    ('POL-23', 'Trabajar o presentarse a trabajar bajo efectos de alcohol o drogas', 45, FALSE, FALSE, 23),
    ('POL-24', 'Problemas por consumo de alcohol: riñas, problemas familiares, sociales o laborales', 40, FALSE, FALSE, 24),
    ('POL-25', 'Problemas por consumo de drogas: riñas, problemas familiares, sociales o laborales', 40, FALSE, FALSE, 25),
    ('POL-26', 'Conducir bajo efectos de alcohol o drogas', 45, FALSE, FALSE, 26),
    ('POL-27', 'Conducir bajo efectos de alcohol o drogas, en cargos de conducción', NULL::integer, FALSE, TRUE, 27),
    ('POL-28', 'Apuestas o juegos de azar que afectan su economía o generan deudas', 35, FALSE, FALSE, 28),
    ('POL-29', 'Admisiones no críticas de hace más de 5 años, o hechos cometidos siendo menor de edad', 10, FALSE, FALSE, 29),
    ('ALR-POL-01', 'Prueba no concluyente (No opinión) sin posibilidad de repetirla', NULL::integer, TRUE, FALSE, 30),
    ('ALR-POL-02', 'No opinión: se debe repetir la prueba', NULL::integer, TRUE, FALSE, 31),
    ('ALR-POL-03', 'El evaluado desistió del examen después de la entrevista', NULL::integer, TRUE, FALSE, 32),
    ('ALR-POL-04', 'Admite deudas, moras o préstamos informales (pasa al análisis financiero)', NULL::integer, TRUE, FALSE, 33),
    ('ALR-POL-05', 'Admite préstamos "gota a gota"', NULL::integer, TRUE, FALSE, 34)
) AS v (codigo, descripcion, puntos, alerta, critico, n)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- Borrar lo que exista del formulario, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE). El Word y su historial se conservan.
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo');
UPDATE tipos_progreso t SET grupo_calificacion = 'HALLAZGOS' WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';

-- 1. Datos de la prueba
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '1. Datos de la prueba', 'CAMPOS', 0, TRUE, 'datos_prueba', NULL, NULL, t.version_plantilla, FALSE, 'Datos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pol_resultado', 'Resultado (cuadro superior y resultado general)', 'LISTA', 0, 'VERAZ' || CHR(10) || 'VERAZ CON INFORMACIÓN' || CHR(10) || 'NO VERAZ' || CHR(10) || 'NO OPINIÓN' || CHR(10) || 'SIN EXAMEN', FALSE, NULL::text, NULL::text, TRUE, NULL::text, NULL::text, NULL::text, 'Resultado de la prueba', 'Resultado general: NO VERAZ', NULL::text, NULL::integer),
    ('estudio_numero', 'N° de estudio', 'TEXTO', 1, NULL::text, FALSE, 'NUMERO_ESTUDIO', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_fecha', 'Fecha del examen', 'FECHA', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_edad', 'Edad', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'CIUDAD_EXPEDICION', NULL::integer),
    ('pol_localizacion', 'Localización (ciudad de residencia)', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'CIUDAD_RESIDENCIA', NULL::integer),
    ('ev_cliente', 'Compañía', 'TEXTO', 6, NULL::text, FALSE, 'CLIENTE', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_cargo', 'Cargo', 'TEXTO', 7, NULL::text, FALSE, 'CARGO', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_anterior', 'Polígrafo anterior', 'TEXTO', 8, NULL::text, FALSE, NULL::text, 'Ninguno', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_criticidad', 'Criticidad del cargo (no se imprime)', 'LISTA', 9, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL::text, NULL::text, TRUE, 'CRITICIDAD', NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_tipo_cargo', 'Tipo de cargo (no se imprime)', 'LISTA', 10, 'Otro' || CHR(10) || 'Logística, transporte y conducción' || CHR(10) || 'Escoltas y transporte de valores', FALSE, NULL::text, 'Otro', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('examen_estado', 'Estado del examen (no se imprime)', 'LISTA', 11, 'Realizado' || CHR(10) || 'Sin examen: desistió después de la entrevista' || CHR(10) || 'Reprogramado', FALSE, NULL::text, 'Realizado', TRUE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_contramedidas', '¿Contramedidas o intento deliberado de manipular la prueba? (no se imprime)', 'LISTA', 12, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, 'Prueba', '{pol_contramedidas_det}', NULL::text, NULL::integer),
    ('pol_contramedidas_det', 'Detalle de las contramedidas (no se imprime)', 'TEXTO', 13, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_dificultad_instr', '¿Dificultad para seguir las instrucciones? (no se imprime)', 'LISTA', 14, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, 'Prueba', '{pol_dificultad_instr_det}', NULL::text, NULL::integer),
    ('pol_dificultad_instr_det', 'Detalle de la dificultad (no se imprime)', 'TEXTO', 15, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_puede_repetir', 'Si es NO OPINIÓN: ¿se puede repetir la prueba? (no se imprime)', 'LISTA', 16, '—' || CHR(10) || 'Sí' || CHR(10) || 'No', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'datos_prueba';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('pol_resultado', 'NO VERAZ', 'POL-01'),
    ('examen_estado', 'Sin examen: desistió después de la entrevista', 'ALR-POL-03'),
    ('pol_contramedidas', 'Sí', 'POL-02'),
    ('pol_dificultad_instr', 'Sí', 'POL-03'),
    ('pol_puede_repetir', 'Sí', 'ALR-POL-02'),
    ('pol_puede_repetir', 'No', 'ALR-POL-01')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'datos_prueba' AND c.clave = v.clave;

-- Preguntas relevantes (primera hoja)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Preguntas relevantes (primera hoja)', 'TABLA', 1, TRUE, 'preguntas', NULL, NULL, t.version_plantilla, FALSE, 'Datos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pr_num', 'R1, R2…', 'TEXTO', 0, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'NUMERO_FILA', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pr_pregunta', 'Pregunta relevante', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pr_resultado', 'Resultado', 'LISTA', 2, 'Veraz' || CHR(10) || 'No veraz' || CHR(10) || 'Inconclusa' || CHR(10) || 'No aplicada', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, 'Preguntas relevantes', 'Una o más preguntas relevantes con resultado No veraz', NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'preguntas';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('pr_resultado', 'No veraz', 'POL-01')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'preguntas' AND c.clave = v.clave;

-- 2. Resultado del servicio (lo calcula el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '2. Resultado del servicio (lo calcula el sistema)', 'CAMPOS', 2, TRUE, 'resultado_servicio', NULL, NULL, t.version_plantilla, FALSE, 'Resultado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 0, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'PUNTAJE', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('srv_formula', 'Cálculo (fila final de hallazgos)', 'TEXTO', 1, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'FORMULA', 'CON_CODIGO', NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 2, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'SEMAFORO_BAJO', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 3, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'SEMAFORO_MEDIO', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 4, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'SEMAFORO_ALTO', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hallazgos_nota', 'Nota sin hallazgos (escríbela solo si no hay hallazgos)', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('alertas_nota', 'Nota sin alertas (escríbela solo si no hay alertas)', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('admisiones_nota', 'Nota sin admisiones (escríbela solo si no hay admisiones)', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'resultado_servicio';

-- Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 3, TRUE, 'hallazgos', NULL, NULL, t.version_plantilla, FALSE, 'Resultado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('h_ref', 'Ref.', 'TEXTO', 0, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'REF', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('h_descripcion', 'Hallazgo', 'TEXTO', 1, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'DESCRIPCION', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('h_puntos', 'Puntos', 'TEXTO', 2, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'PUNTOS', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'hallazgos';

-- Alertas (no suman puntos; todas visibles al cliente)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Alertas (no suman puntos; todas visibles al cliente)', 'TABLA', 4, TRUE, 'alertas', NULL, NULL, t.version_plantilla, FALSE, 'Resultado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('a_alerta', 'Alerta', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('a_detalle', 'Detalle', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('a_codigo', 'Código', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'alertas';

-- 3. Admisiones (cada una con su categoría; el código lo calcula la plataforma)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '3. Admisiones (cada una con su categoría; el código lo calcula la plataforma)', 'TABLA', 5, TRUE, 'admisiones', NULL, NULL, t.version_plantilla, FALSE, 'Admisiones', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ad_texto', 'Texto de la admisión', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 4000),
    ('ad_categoria', 'Categoría (no se imprime)', 'LISTA', 1, 'Robo en empleos: un salario mínimo o más, o más de dos veces' || CHR(10) || 'Robo en empleos: menos de un salario mínimo, una o dos veces' || CHR(10) || 'Sobornos o comisiones irregulares' || CHR(10) || 'Complicidad en ilícitos contra un empleador' || CHR(10) || 'Alteración u ocultamiento de documentos, registros o información' || CHR(10) || 'Abandono del cargo o retiros sin aviso' || CHR(10) || 'Despido por mala conducta o faltas graves' || CHR(10) || 'Conflicto de interés no declarado' || CHR(10) || 'Conductas agresivas o violentas' || CHR(10) || 'Documentos falsos o comprados (títulos, certificados)' || CHR(10) || 'Mintió en la hoja de vida o en el proceso' || CHR(10) || 'Beneficio de actividades ilícitas' || CHR(10) || 'Venta, transporte o tráfico de drogas' || CHR(10) || 'Vínculo activo con grupos ilegales' || CHR(10) || 'Otro delito (incluye porte ilegal de armas)' || CHR(10) || 'Relación social actual con personas en actividades ilícitas' || CHR(10) || 'Vínculo pasado con personas en actividades ilícitas (terminado)' || CHR(10) || 'Consumo frecuente de drogas (puede afectar su trabajo)' || CHR(10) || 'Consumo ocasional de drogas' || CHR(10) || 'Trabajar bajo efectos de alcohol o drogas' || CHR(10) || 'Problemas por consumo de alcohol' || CHR(10) || 'Problemas por consumo de drogas' || CHR(10) || 'Conducir bajo efectos de alcohol o drogas' || CHR(10) || 'Apuestas o juegos de azar' || CHR(10) || 'Deudas, moras o préstamos' || CHR(10) || 'Préstamos gota a gota' || CHR(10) || 'Experiencia laboral, llamados de atención o descargos' || CHR(10) || 'Estudios' || CHR(10) || 'Antecedentes, detenciones o medidas correctivas' || CHR(10) || 'Ayudó en algo irregular sin saberlo' || CHR(10) || 'Otra información relevante', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, 'Admisiones', '{ad_texto}', NULL::text, NULL::integer),
    ('ad_cuando', 'Cuándo ocurrió (no se imprime)', 'LISTA', 2, 'En el último año' || CHR(10) || 'Hace 1 a 5 años' || CHR(10) || 'Hace más de 5 años' || CHR(10) || 'Siendo menor de edad', FALSE, NULL::text, 'En el último año', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ad_tipo', 'Tipo de antecedente declarado (no se imprime)', 'LISTA', 3, '—' || CHR(10) || 'Orden de captura o requerimiento vigente' || CHR(10) || 'Proceso penal activo, relacionado con el cargo' || CHR(10) || 'Proceso penal activo, no relacionado con el cargo' || CHR(10) || 'Sanción o inhabilidad disciplinaria vigente' || CHR(10) || 'Responsabilidad fiscal vigente' || CHR(10) || 'Medida correctiva' || CHR(10) || 'Proceso penal terminado sin condena' || CHR(10) || 'Condena cumplida, relacionada con el cargo' || CHR(10) || 'Condena cumplida, no relacionada con el cargo' || CHR(10) || 'Demanda no penal activa en contra' || CHR(10) || 'Demanda no penal terminada en contra', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ad_cantidad', 'Cuántos (no se imprime)', 'NUMERO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ad_mora', 'Tiempo de mora (no se imprime)', 'LISTA', 5, '—' || CHR(10) || 'Al día' || CHR(10) || 'Menos de 30 días' || CHR(10) || '30 a 89 días, repetidas' || CHR(10) || '90 a 180 días' || CHR(10) || 'Más de 180 días o castigada', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ad_embargo', '¿Embargo o cobro jurídico? (no se imprime)', 'LISTA', 6, '—' || CHR(10) || 'Sí' || CHR(10) || 'No', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'admisiones';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('ad_categoria', 'Robo en empleos: un salario mínimo o más, o más de dos veces', 'POL-04'),
    ('ad_categoria', 'Robo en empleos: menos de un salario mínimo, una o dos veces', 'POL-05'),
    ('ad_categoria', 'Sobornos o comisiones irregulares', 'POL-06'),
    ('ad_categoria', 'Complicidad en ilícitos contra un empleador', 'POL-07'),
    ('ad_categoria', 'Alteración u ocultamiento de documentos, registros o información', 'POL-08'),
    ('ad_categoria', 'Abandono del cargo o retiros sin aviso', 'POL-10'),
    ('ad_categoria', 'Despido por mala conducta o faltas graves', 'POL-09'),
    ('ad_categoria', 'Conflicto de interés no declarado', 'POL-11'),
    ('ad_categoria', 'Conductas agresivas o violentas', 'POL-12'),
    ('ad_categoria', 'Documentos falsos o comprados (títulos, certificados)', 'POL-13'),
    ('ad_categoria', 'Mintió en la hoja de vida o en el proceso', 'POL-14'),
    ('ad_categoria', 'Beneficio de actividades ilícitas', 'POL-15'),
    ('ad_categoria', 'Venta, transporte o tráfico de drogas', 'POL-16'),
    ('ad_categoria', 'Vínculo activo con grupos ilegales', 'POL-17'),
    ('ad_categoria', 'Otro delito (incluye porte ilegal de armas)', 'POL-18'),
    ('ad_categoria', 'Relación social actual con personas en actividades ilícitas', 'POL-19'),
    ('ad_categoria', 'Vínculo pasado con personas en actividades ilícitas (terminado)', 'POL-20'),
    ('ad_categoria', 'Consumo frecuente de drogas (puede afectar su trabajo)', 'POL-21'),
    ('ad_categoria', 'Consumo ocasional de drogas', 'POL-22'),
    ('ad_categoria', 'Trabajar bajo efectos de alcohol o drogas', 'POL-23'),
    ('ad_categoria', 'Problemas por consumo de alcohol', 'POL-24'),
    ('ad_categoria', 'Problemas por consumo de drogas', 'POL-25'),
    ('ad_categoria', 'Conducir bajo efectos de alcohol o drogas', 'POL-26'),
    ('ad_categoria', 'Apuestas o juegos de azar', 'POL-28'),
    ('ad_categoria', 'Deudas, moras o préstamos', 'ALR-POL-04'),
    ('ad_categoria', 'Préstamos gota a gota', 'ALR-POL-05')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'admisiones' AND c.clave = v.clave;

-- 3. Admisión personal o de salud (se guarda, NO se imprime ni suma)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '3. Admisión personal o de salud (se guarda, NO se imprime ni suma)', 'TABLA', 6, TRUE, 'admisiones_sensibles', NULL, NULL, t.version_plantilla, FALSE, 'Admisiones', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ad_texto', 'Texto de la admisión', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 4000)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'admisiones_sensibles';

-- 4. Idoneidad
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '4. Idoneidad', 'CAMPOS', 7, TRUE, 'idoneidad', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('id_concepto', 'Concepto de idoneidad (sin detalle médico)', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('id_estado', 'Estado general', 'LISTA', 1, 'Apto(a) para la prueba' || CHR(10) || 'No apto(a) para la prueba', FALSE, NULL::text, 'Apto(a) para la prueba', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'idoneidad';

-- 5. Entorno familiar
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '5. Entorno familiar', 'CAMPOS', 8, TRUE, 'entorno_familiar', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ef_hogar', 'Hogar y convivencia (parentesco, edad y ocupación)', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 4000),
    ('ef_familia', 'Familiares con quienes no convive y tiempo libre', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 4000)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'entorno_familiar';

-- 6. Educación (precargada de la hoja de vida)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '6. Educación (precargada de la hoja de vida)', 'TABLA', 9, TRUE, 'estudios', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ed_formacion', 'Formación', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ed_anio', 'Año', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ed_institucion', 'Institución', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'estudios';

-- 6. Educación · observaciones
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '6. Educación · observaciones', 'CAMPOS', 10, TRUE, 'estudios_obs', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ed_obs', 'Observaciones', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'estudios_obs';

-- 7. Historia financiera
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '7. Historia financiera', 'CAMPOS', 11, TRUE, 'historia_financiera', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('hf_al_dia', 'Obligaciones al día', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hf_mora', 'Obligaciones en mora', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hf_bienes', 'Bienes patrimoniales', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fd1_modulo', 'Obligaciones en mora o reportes negativos – respondió en el link', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fd1_prueba', 'Obligaciones en mora o reportes negativos – manifestó en la prueba', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fd2_modulo', 'Embargos o cobro jurídico en curso – respondió en el link', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fd2_prueba', 'Embargos o cobro jurídico en curso – manifestó en la prueba', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'historia_financiera';

-- 8. Vehículos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '8. Vehículos', 'CAMPOS', 12, TRUE, 'vehiculos', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ve_tiene', 'Vehículos – Sí/No', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ve_tipo', 'Moto/Automóvil', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ve_marca', 'Marca y modelo', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ve_a_nombre', 'A su nombre', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ve_licencia', 'Licencia', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ve_comparendos', 'Comparendos', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ve_obs', 'Observaciones', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'vehiculos';

-- 9. Experiencia laboral (precargada de la hoja de vida)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Experiencia laboral (precargada de la hoja de vida)', 'TABLA', 13, TRUE, 'empleos', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ex_num', 'N°', 'TEXTO', 0, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'NUMERO_FILA', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_tiempo', 'Tiempo (fecha inicio – fecha retiro)', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_empresa', 'Empresa', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_cargo', 'Cargo', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_contrato', 'Contrato', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_retiro', 'Motivo de retiro', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_llamados_mod', 'Llamados de atención declarados', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'empleos';

-- 9. Experiencia laboral · llamados de atención y descargos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Experiencia laboral · llamados de atención y descargos', 'CAMPOS', 14, TRUE, 'empleos_obs', NULL, NULL, t.version_plantilla, FALSE, 'Entrevista', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ex_llamados', '¿Tuvo llamados de atención?', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_descargos_empresa', 'Descargos – empresa', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ex_descargos_motivo', 'Descargos – motivo', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'empleos_obs';

-- 10. Antecedentes laborales (admisiones)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '10. Antecedentes laborales (admisiones)', 'CAMPOS', 15, TRUE, 'antecedentes_laborales', NULL, NULL, t.version_plantilla, FALSE, 'Admisiones', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('al_robos', 'Robos en empleos previos', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('al_robos_alcance', 'Robos: cuánto y cuántas veces (no se imprime)', 'LISTA', 1, '—' || CHR(10) || 'Menos de un salario mínimo y una o dos veces' || CHR(10) || 'Un salario mínimo o más, o más de dos veces', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Antecedentes laborales (robos)', '{al_robos}', NULL::text, NULL::integer),
    ('al_complicidad', 'Complicidad en ilícitos', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('al_complicidad_si', 'Complicidad: ¿admite? (no se imprime)', 'LISTA', 3, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Antecedentes laborales', '{al_complicidad}', NULL::text, NULL::integer),
    ('al_alteracion', 'Alteración de documentos o sistemas', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('al_alteracion_si', 'Alteración: ¿admite? (no se imprime)', 'LISTA', 5, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Antecedentes laborales', '{al_alteracion}', NULL::text, NULL::integer),
    ('al_despido', 'Despido por mala conducta', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('al_despido_si', 'Despido: ¿admite? (no se imprime)', 'LISTA', 7, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Antecedentes laborales', '{al_despido}', NULL::text, NULL::integer),
    ('al_sobornos', 'Sobornos o comisiones', 'TEXTO', 8, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('al_sobornos_si', 'Sobornos: ¿admite? (no se imprime)', 'LISTA', 9, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Antecedentes laborales', '{al_sobornos}', NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'antecedentes_laborales';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('al_robos_alcance', 'Menos de un salario mínimo y una o dos veces', 'POL-05'),
    ('al_robos_alcance', 'Un salario mínimo o más, o más de dos veces', 'POL-04'),
    ('al_complicidad_si', 'Sí', 'POL-07'),
    ('al_alteracion_si', 'Sí', 'POL-08'),
    ('al_despido_si', 'Sí', 'POL-09'),
    ('al_sobornos_si', 'Sí', 'POL-06')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'antecedentes_laborales' AND c.clave = v.clave;

-- 11. Áreas: alcohol, drogas, antecedentes y vínculos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '11. Áreas: alcohol, drogas, antecedentes y vínculos', 'CAMPOS', 16, TRUE, 'areas', NULL, NULL, t.version_plantilla, FALSE, 'Admisiones', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ac_alcohol_frec', 'Consumo de alcohol', 'LISTA', 0, 'Nulo' || CHR(10) || 'Ocasional' || CHR(10) || 'Frecuente', FALSE, NULL::text, 'Nulo', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ac_alcohol', 'Consumo de alcohol – texto', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('ac_alcohol_problemas', 'Alcohol: ¿problemas? (no se imprime)', 'LISTA', 2, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: alcohol', '{ac_alcohol}', NULL::text, NULL::integer),
    ('ac_alcohol_trabajo', 'Alcohol: ¿trabajó bajo efectos? (no se imprime)', 'LISTA', 3, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: alcohol', '{ac_alcohol}', NULL::text, NULL::integer),
    ('ac_alcohol_conduccion', 'Alcohol: ¿condujo bajo efectos? (no se imprime)', 'LISTA', 4, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: alcohol', '{ac_alcohol}', NULL::text, NULL::integer),
    ('ac_drogas_frec', 'Consumo de drogas', 'LISTA', 5, 'Nulo' || CHR(10) || 'Ocasional' || CHR(10) || 'Frecuente', FALSE, NULL::text, 'Nulo', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ac_drogas', 'Consumo de drogas – texto', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('ac_drogas_cuando', 'Drogas: consumo y cuándo (no se imprime)', 'LISTA', 7, '—' || CHR(10) || 'Frecuente, en el último año' || CHR(10) || 'Ocasional o frecuente, hace 1 a 5 años', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: drogas', '{ac_drogas}', NULL::text, NULL::integer),
    ('ac_drogas_problemas', 'Drogas: ¿problemas? (no se imprime)', 'LISTA', 8, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: drogas', '{ac_drogas}', NULL::text, NULL::integer),
    ('ac_drogas_trabajo', 'Drogas: ¿trabajó bajo efectos? (no se imprime)', 'LISTA', 9, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: drogas', '{ac_drogas}', NULL::text, NULL::integer),
    ('ac_drogas_conduccion', 'Drogas: ¿condujo bajo efectos? (no se imprime)', 'LISTA', 10, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: drogas', '{ac_drogas}', NULL::text, NULL::integer),
    ('ac_falsificacion', 'Falsificación de documentación', 'TEXTO', 11, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('ac_falsificacion_si', 'Falsificación: ¿admite? (no se imprime)', 'LISTA', 12, '—' || CHR(10) || 'Sí', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: falsificación', '{ac_falsificacion}', NULL::text, NULL::integer),
    ('ac_antecedentes', 'Antecedentes', 'TEXTO', 13, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('ac_vinculos', 'Vínculos', 'TEXTO', 14, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('ac_vinculos_tipo', 'Vínculos: tipo (no se imprime)', 'LISTA', 15, '—' || CHR(10) || 'Activo con grupos ilegales' || CHR(10) || 'Venta, transporte o tráfico de drogas' || CHR(10) || 'Beneficio de actividades ilícitas' || CHR(10) || 'Relación social actual con personas en actividades ilícitas' || CHR(10) || 'Vínculo pasado terminado', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Áreas: vínculos', '{ac_vinculos}', NULL::text, NULL::integer),
    ('pd1_modulo', 'Procesos judiciales y calidad – respondió en el link', 'TEXTO', 16, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pd1_prueba', 'Procesos judiciales y calidad – manifestó en la prueba', 'TEXTO', 17, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pd2_modulo', 'Órdenes de captura o asuntos pendientes – respondió en el link', 'TEXTO', 18, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pd2_prueba', 'Órdenes de captura o asuntos pendientes – manifestó en la prueba', 'TEXTO', 19, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pd3_modulo', 'Sanciones disciplinarias o fiscales – respondió en el link', 'TEXTO', 20, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pd3_prueba', 'Sanciones disciplinarias o fiscales – manifestó en la prueba', 'TEXTO', 21, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'areas';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('ac_alcohol_problemas', 'Sí', 'POL-24'),
    ('ac_alcohol_trabajo', 'Sí', 'POL-23'),
    ('ac_alcohol_conduccion', 'Sí', 'POL-26'),
    ('ac_drogas_cuando', 'Frecuente, en el último año', 'POL-21'),
    ('ac_drogas_cuando', 'Ocasional o frecuente, hace 1 a 5 años', 'POL-22'),
    ('ac_drogas_problemas', 'Sí', 'POL-25'),
    ('ac_drogas_trabajo', 'Sí', 'POL-23'),
    ('ac_drogas_conduccion', 'Sí', 'POL-26'),
    ('ac_falsificacion_si', 'Sí', 'POL-13'),
    ('ac_vinculos_tipo', 'Activo con grupos ilegales', 'POL-17'),
    ('ac_vinculos_tipo', 'Venta, transporte o tráfico de drogas', 'POL-16'),
    ('ac_vinculos_tipo', 'Beneficio de actividades ilícitas', 'POL-15'),
    ('ac_vinculos_tipo', 'Relación social actual con personas en actividades ilícitas', 'POL-19'),
    ('ac_vinculos_tipo', 'Vínculo pasado terminado', 'POL-20')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'areas' AND c.clave = v.clave;

-- 12. Firma y conclusión
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '12. Firma y conclusión', 'CAMPOS', 17, TRUE, 'firma', NULL, NULL, t.version_plantilla, FALSE, 'Cierre', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pol_poligrafista', 'Poligrafista', 'TEXTO', 0, NULL::text, FALSE, 'PROFESIONAL', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_poligrafista_cred', 'Credencial', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_tecnica', 'Técnica', 'LISTA', 2, 'AFMGQT' || CHR(10) || 'Utah', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_equipo', 'Equipo', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_num_relevantes', 'N° de preguntas relevantes (en letras)', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pol_conclusion', 'Conclusión', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 3000),
    ('pol_fecha_informe', 'Fecha del informe', 'FECHA', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'firma';

-- Anexo 2 · autorización firmada (un cuadro por página)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Anexo 2 · autorización firmada (un cuadro por página)', 'TABLA', 18, TRUE, 'autorizacion', NULL, NULL, t.version_plantilla, FALSE, 'Cierre', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Poligrafia Pre Empleo';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('au_pagina', 'Página', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('au_imagen', 'Imagen', 'ARCHIVO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'autorizacion';
