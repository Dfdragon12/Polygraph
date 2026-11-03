-- Plantilla de "Visita Domiciliaria  presencial" (2026-10-29, pedido del usuario): crea el formulario DESDE CERO sobre el subservicio que YA
-- existe (id 7; su nombre lleva DOS espacios, por eso las migraciones que solo crean «si no existe» no hacían nada) a partir de docs/Visita Domiciliaria
-- presencial (diccionario de marcadores, formato Word y motor_vis.py). NO toca el Word ya cargado (es el mismo 1_Formato_Visita_Domiciliaria.docx).
-- Los MÓDULOS (rutina, virtual, vehicular, OEA, teletrabajo y las fotos de parqueo y de teletrabajo) son tablas de una fila: el visitador
-- agrega UNA fila cuando el módulo aplica y el bloque sale en el Word; sin filas, el bloque desaparece (repeatDocPart sobre lista vacía).
-- Califica con el catálogo de hallazgos genérico: cada opción de lista que dispara un código (VIS-01..11 y ALR-VIS-01..09) queda mapeada.
-- PENDIENTE (segunda fase, motor propio como MotorFinanciero): VIS-04 por dos intentos fallidos, escalas en porcentaje (df, hab, eco, tq, perfil),
-- totales de economía, v_modulos, dc_*_res y ps_*_ref automáticos, avisos de validar() y mínimos de fotos. Hoy esos campos los escribe el visitador.
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- Catálogo de hallazgos de la visita (docs/Visita Domiciliaria presencial/motor_vis.py): todos cuentan una vez por estudio.
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta)
SELECT 'GENERAL', v.codigo, v.descripcion, v.puntos, FALSE, FALSE, FALSE, TRUE, (SELECT COALESCE(MAX(orden), 0) FROM codigos_calificacion) + v.n, v.alerta
FROM (VALUES
    ('VIS-01', 'No reside en la dirección declarada, o la ubicación de la visita virtual no corresponde, sin explicación', 70, FALSE, 1),
    ('VIS-02', 'Vínculo (relación, colaboración o negocios) con personas al margen de la ley o grupos armados ilegales', 70, FALSE, 2),
    ('VIS-03', 'Arma de fuego sin salvoconducto en el hogar', 50, FALSE, 3),
    ('VIS-04', 'Visita no realizada por causa del candidato: dos intentos fallidos o negativa a recibirla', 45, FALSE, 4),
    ('VIS-05', 'Consumo actual de sustancias psicoactivas declarado', 40, FALSE, 5),
    ('VIS-06', 'Economía desequilibrada: los egresos superan los ingresos sin explicación', 35, FALSE, 6),
    ('VIS-07', 'Frecuenta juegos de azar con impacto en su economía', 35, FALSE, 7),
    ('VIS-08', 'Falta de colaboración en la visita: se niega a responder, a mostrar espacios o a las fotos, o trata de forma irrespetuosa al visitador', 35, FALSE, 8),
    ('VIS-09', 'Recibe la visita bajo efectos aparentes de alcohol o sustancias', 50, FALSE, 9),
    ('VIS-10', 'Teletrabajo: no garantiza la seguridad de la información (red no privada, equipo compartido, sin privacidad de contraseñas o espacio expuesto a terceros)', 35, FALSE, 10),
    ('VIS-11', 'Vehicular: el parqueadero no ofrece seguridad para el vehículo (vía pública, sin vigilancia ni cerramiento, o arriendo sin soportes)', 35, FALSE, 11),
    ('ALR-VIS-01', 'Familiar privado de la libertad, detenido o con procesos judiciales', 0, TRUE, 12),
    ('ALR-VIS-02', 'Conocido o familiar que trabaja o trabajó en la empresa (cómo llegó la hoja de vida o pregunta 8)', 0, TRUE, 13),
    ('ALR-VIS-03', 'Problemáticas graves en el sector (expendio de drogas, presencia de grupos armados u otras)', 0, TRUE, 14),
    ('ALR-VIS-04', 'Arma de fuego con salvoconducto en el hogar', 0, TRUE, 15),
    ('ALR-VIS-05', 'Teletrabajo: no cumple, o cumple con observaciones, las condiciones físicas', 0, TRUE, 16),
    ('ALR-VIS-06', 'El candidato o su familia fueron contactados por grupos ilegales como víctimas (extorsión u otra)', 0, TRUE, 17),
    ('ALR-VIS-07', 'Visita no realizada por causa ajena al candidato: reprogramar', 0, TRUE, 18),
    ('ALR-VIS-08', 'Rutina: la vinculación laboral actual no coincide con lo reportado por la empresa', 0, TRUE, 19),
    ('ALR-VIS-09', 'Vehicular: la distancia o el tamaño de la zona de parqueo no son adecuados', 0, TRUE, 20)
) AS v (codigo, descripcion, puntos, alerta, n)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- Borrar lo que exista del formulario, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE). El Word y su historial se conservan.
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial');
UPDATE tipos_progreso t SET grupo_calificacion = 'HALLAZGOS' WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';

-- 1. Datos generales de la visita
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '1. Datos generales de la visita', 'CAMPOS', 0, TRUE, 'datos_generales', NULL, NULL, t.version_plantilla, FALSE, 'Datos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('estudio_numero', 'N° de estudio', 'TEXTO', 0, NULL::text, FALSE, 'NUMERO_ESTUDIO', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fecha_emision', 'Fecha de emisión', 'TEXTO', 1, NULL::text, FALSE, 'FECHA_EMISION', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_cliente', 'Cliente', 'TEXTO', 2, NULL::text, FALSE, 'CLIENTE', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_cargo', 'Cargo', 'TEXTO', 3, NULL::text, FALSE, 'CARGO', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'CIUDAD_EXPEDICION', NULL::integer),
    ('ev_criticidad', 'Criticidad del cargo (no se imprime)', 'LISTA', 5, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL::text, NULL::text, TRUE, 'CRITICIDAD', NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_fecha_orden', 'Fecha de recibo de la orden', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_ciudad_fecha', 'Ciudad y fecha de visita', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, TRUE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_tipo', 'Tipo de visita', 'LISTA', 8, 'Presencial' || CHR(10) || 'Virtual', FALSE, NULL::text, 'Presencial', TRUE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_clase', 'Clase de visita (no se imprime)', 'LISTA', 9, 'Pre-empleo' || CHR(10) || 'Rutina', FALSE, NULL::text, 'Pre-empleo', TRUE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_modulos', 'Clase de visita (impreso)', 'TEXTO', 10, NULL::text, FALSE, NULL::text, 'Pre-empleo', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_profesional', 'Profesional a cargo', 'TEXTO', 11, NULL::text, FALSE, 'PROFESIONAL', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_atiende', 'Quien atiende la visita (nombre y parentesco)', 'TEXTO', 12, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_hoja_vida', 'Cómo llega su hoja de vida a la empresa', 'TEXTO', 13, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_hv_conocido', '¿Llegó por un conocido o familiar que trabaja en la empresa? (no se imprime)', 'LISTA', 14, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_salario', 'Salario a devengar', 'TEXTO', 15, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_ciudad_reg', 'Ciudad de residencia – registrado por el candidato', 'TEXTO', 16, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'CIUDAD_RESIDENCIA', NULL::integer),
    ('dc_ciudad_vis', 'Ciudad de residencia – confirmado en la visita', 'TEXTO', 17, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_ciudad_res', 'Ciudad de residencia – resultado', 'LISTA', 18, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_barrio_reg', 'Barrio de residencia – registrado por el candidato', 'TEXTO', 19, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'BARRIO_RESIDENCIA', NULL::integer),
    ('dc_barrio_vis', 'Barrio de residencia – confirmado en la visita', 'TEXTO', 20, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_barrio_res', 'Barrio de residencia – resultado', 'LISTA', 21, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_direccion_reg', 'Dirección de residencia – registrado por el candidato', 'TEXTO', 22, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'DIRECCION_RESIDENCIA', NULL::integer),
    ('dc_direccion_vis', 'Dirección de residencia – confirmado en la visita', 'TEXTO', 23, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_direccion_res', 'Dirección de residencia – resultado', 'LISTA', 24, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_adicionales_reg', 'Adicionales (casa, torre, apartamento) – registrado por el candidato', 'TEXTO', 25, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_adicionales_vis', 'Adicionales (casa, torre, apartamento) – confirmado en la visita', 'TEXTO', 26, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_adicionales_res', 'Adicionales (casa, torre, apartamento) – resultado', 'LISTA', 27, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_estrato_reg', 'Estrato – registrado por el candidato', 'TEXTO', 28, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'ESTRATO', NULL::integer),
    ('dc_estrato_vis', 'Estrato – confirmado en la visita', 'TEXTO', 29, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_estrato_res', 'Estrato – resultado', 'LISTA', 30, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_estado_civil_reg', 'Estado civil – registrado por el candidato', 'TEXTO', 31, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 'ESTADO_CIVIL', NULL::integer),
    ('dc_estado_civil_vis', 'Estado civil – confirmado en la visita', 'TEXTO', 32, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_estado_civil_res', 'Estado civil – resultado', 'LISTA', 33, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('v_residencia', '¿Reside en la dirección declarada?', 'LISTA', 34, 'Sí' || CHR(10) || 'No – con explicación' || CHR(10) || 'No – sin explicación', FALSE, NULL::text, 'Sí', TRUE, NULL::text, NULL::text, NULL::text, 'Datos generales', '{v_residencia_det}', NULL::text, NULL::integer),
    ('v_residencia_det', 'Explicación de la residencia (no se imprime)', 'TEXTO', 35, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('dc_obs', 'Observaciones de los datos', 'TEXTO', 36, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'datos_generales';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('v_residencia', 'No – sin explicación', 'VIS-01'),
    ('v_hv_conocido', 'Sí', 'ALR-VIS-02')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'datos_generales' AND c.clave = v.clave;

-- Gestión de la visita (intentos)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Gestión de la visita (intentos)', 'TABLA', 1, TRUE, 'intentos', NULL, NULL, t.version_plantilla, FALSE, 'Datos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('it_fecha', 'Fecha y hora', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('it_modalidad', 'Modalidad', 'LISTA', 1, 'Presencial' || CHR(10) || 'Virtual', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('it_resultado', 'Resultado', 'LISTA', 2, 'Realizada' || CHR(10) || 'No atendió' || CHR(10) || 'Canceló el candidato' || CHR(10) || 'Reprogramó el candidato' || CHR(10) || 'Se negó a recibirla' || CHR(10) || 'No realizada por causa ajena', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, 'Gestión de la visita', '{it_obs}', NULL::text, NULL::integer),
    ('it_obs', 'Observación', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'intentos';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('it_resultado', 'Se negó a recibirla', 'VIS-04'),
    ('it_resultado', 'No realizada por causa ajena', 'ALR-VIS-07')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'intentos' AND c.clave = v.clave;

-- 2. Resultado del servicio (lo calcula el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '2. Resultado del servicio (lo calcula el sistema)', 'CAMPOS', 2, TRUE, 'resultado_servicio', NULL, NULL, t.version_plantilla, FALSE, 'Resultado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 0, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'PUNTAJE', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 1, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'FORMULA', 'CON_CODIGO', NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 2, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'SEMAFORO_BAJO', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 3, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'SEMAFORO_MEDIO', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 4, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'SEMAFORO_ALTO', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 5, NULL::text, FALSE, NULL::text, 'Las alertas (ALR-VIS) no suman puntos; requieren revisión del analista antes del informe final.', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'resultado_servicio';

-- 3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 3, TRUE, 'hallazgos', NULL, NULL, t.version_plantilla, FALSE, 'Resultado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('h_ref', 'Ref.', 'TEXTO', 0, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'REF', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('h_descripcion', 'Hallazgo', 'TEXTO', 1, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'DESCRIPCION', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('h_fuente', 'Fuente', 'TEXTO', 2, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'FUENTE', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('h_detalle', 'Detalle', 'TEXTO', 3, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'DETALLE', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('h_puntos', 'Puntos', 'TEXTO', 4, NULL::text, TRUE, NULL::text, NULL::text, FALSE, NULL::text, 'PUNTOS', NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'hallazgos';

-- 4. Hallazgos sin impacto en el nivel de riesgo
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo', 'TABLA', 4, TRUE, 'informativos', NULL, NULL, t.version_plantilla, FALSE, 'Resultado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'informativos';

-- Módulo rutina · cambios y vinculación laboral (agrega UNA fila solo si la visita es de rutina)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo rutina · cambios y vinculación laboral (agrega UNA fila solo si la visita es de rutina)', 'TABLA', 5, TRUE, 'rutina', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('rc_anterior', 'Visita anterior', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_viv_ant', 'Vivienda – anterior', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_viv_act', 'Vivienda – actual', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_viv_cambio', 'Vivienda – cambio', 'LISTA', 3, 'Sin cambio' || CHR(10) || 'Cambió' || CHR(10) || 'Primer registro', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_fam_ant', 'Familia – anterior', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_fam_act', 'Familia – actual', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_fam_cambio', 'Familia – cambio', 'LISTA', 6, 'Sin cambio' || CHR(10) || 'Cambió' || CHR(10) || 'Primer registro', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_edu_ant', 'Estudios – anterior', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_edu_act', 'Estudios – actual', 'TEXTO', 8, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_edu_cambio', 'Estudios – cambio', 'LISTA', 9, 'Sin cambio' || CHR(10) || 'Cambió' || CHR(10) || 'Primer registro', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_ing_ant', 'Ingresos – anterior', 'TEXTO', 10, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_ing_act', 'Ingresos – actual', 'TEXTO', 11, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_ing_cambio', 'Ingresos – cambio', 'LISTA', 12, 'Sin cambio' || CHR(10) || 'Cambió' || CHR(10) || 'Primer registro', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_deu_ant', 'Deudas – anterior', 'TEXTO', 13, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_deu_act', 'Deudas – actual', 'TEXTO', 14, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_deu_cambio', 'Deudas – cambio', 'LISTA', 15, 'Sin cambio' || CHR(10) || 'Cambió' || CHR(10) || 'Primer registro', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_pat_ant', 'Patrimonio – anterior', 'TEXTO', 16, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_pat_act', 'Patrimonio – actual', 'TEXTO', 17, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_pat_cambio', 'Patrimonio – cambio', 'LISTA', 18, 'Sin cambio' || CHR(10) || 'Cambió' || CHR(10) || 'Primer registro', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rc_obs', 'Observaciones', 'TEXTO', 19, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_ingreso_emp', 'Fecha de ingreso – empresa', 'TEXTO', 20, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_ingreso_vis', 'Fecha de ingreso – visita', 'TEXTO', 21, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_ingreso_res', 'Fecha de ingreso – resultado', 'LISTA', 22, '—' || CHR(10) || 'Coincide' || CHR(10) || 'No coincide', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_contrato_emp', 'Tipo de contrato – empresa', 'TEXTO', 23, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_contrato_vis', 'Tipo de contrato – visita', 'TEXTO', 24, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_contrato_res', 'Tipo de contrato – resultado', 'LISTA', 25, '—' || CHR(10) || 'Coincide' || CHR(10) || 'No coincide', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_cargo_emp', 'Cargo y funciones – empresa', 'TEXTO', 26, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_cargo_vis', 'Cargo y funciones – visita', 'TEXTO', 27, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_cargo_res', 'Cargo y funciones – resultado', 'LISTA', 28, '—' || CHR(10) || 'Coincide' || CHR(10) || 'No coincide', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_salario_emp', 'Salario – empresa', 'TEXTO', 29, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_salario_vis', 'Salario – visita', 'TEXTO', 30, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_salario_res', 'Salario – resultado', 'LISTA', 31, '—' || CHR(10) || 'Coincide' || CHR(10) || 'No coincide', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_jefe_emp', 'Jefe inmediato – empresa', 'TEXTO', 32, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_jefe_vis', 'Jefe inmediato – visita', 'TEXTO', 33, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_jefe_res', 'Jefe inmediato – resultado', 'LISTA', 34, '—' || CHR(10) || 'Coincide' || CHR(10) || 'No coincide', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_horario', 'Horario', 'TEXTO', 35, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_relacion_jefe', 'Relación con el jefe', 'TEXTO', 36, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_ambiente', 'Ambiente laboral', 'TEXTO', 37, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_llamados', 'Llamados de atención', 'TEXTO', 38, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_investigaciones', 'Investigaciones', 'TEXTO', 39, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('rv_obs', 'Observaciones', 'TEXTO', 40, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'rutina';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('rv_ingreso_res', 'No coincide', 'ALR-VIS-08'),
    ('rv_contrato_res', 'No coincide', 'ALR-VIS-08'),
    ('rv_cargo_res', 'No coincide', 'ALR-VIS-08'),
    ('rv_salario_res', 'No coincide', 'ALR-VIS-08'),
    ('rv_jefe_res', 'No coincide', 'ALR-VIS-08')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'rutina' AND c.clave = v.clave;

-- 5. Familia · padres y hermanos (si no tiene, una fila «No tiene»)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '5. Familia · padres y hermanos (si no tiene, una fila «No tiene»)', 'TABLA', 6, TRUE, 'familia_origen', NULL, NULL, t.version_plantilla, FALSE, 'Familia', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('fo_nombre', 'Nombre', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_parentesco', 'Parentesco', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_edad', 'Edad', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_estado_civil', 'Estado civil', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_ocupacion', 'Ocupación', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_entidad', 'Entidad', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_nivel', 'Nivel educativo', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fo_convive', '¿Vive con él?', 'LISTA', 7, 'Sí' || CHR(10) || 'No', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'familia_origen';

-- 5. Familia · pareja e hijos (si no tiene, una fila «No tiene»)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '5. Familia · pareja e hijos (si no tiene, una fila «No tiene»)', 'TABLA', 7, TRUE, 'familia_pareja', NULL, NULL, t.version_plantilla, FALSE, 'Familia', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('fp_nombre', 'Nombre', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_parentesco', 'Parentesco', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_edad', 'Edad', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_estado_civil', 'Estado civil', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_ocupacion', 'Ocupación', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_entidad', 'Entidad', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_nivel', 'Nivel educativo', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fp_convive', '¿Vive con él?', 'LISTA', 7, 'Sí' || CHR(10) || 'No', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'familia_pareja';

-- 5. Familia · otras personas con las que convive (si no hay, una fila «No tiene»)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '5. Familia · otras personas con las que convive (si no hay, una fila «No tiene»)', 'TABLA', 8, TRUE, 'familia_convive', NULL, NULL, t.version_plantilla, FALSE, 'Familia', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('fc_nombre', 'Nombre', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fc_parentesco', 'Parentesco', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fc_edad', 'Edad', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fc_estado_civil', 'Estado civil', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fc_ocupacion', 'Ocupación', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fc_entidad', 'Entidad', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fc_nivel', 'Nivel educativo', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'familia_convive';

-- 5. Dinámica familiar
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '5. Dinámica familiar', 'CAMPOS', 9, TRUE, 'dinamica_familiar', NULL, NULL, t.version_plantilla, FALSE, 'Familia', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('fam_obs_origen', 'Observaciones – cuadro origen', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fam_obs_pareja', 'Observaciones – cuadro pareja', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fam_obs_convive', 'Observaciones – cuadro convive', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fam_dinamica', 'Concepto dinámica familiar', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, 4000),
    ('fam_logro', 'Competencia orientada al logro', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fam_servicio', 'Competencia orientada al servicio', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('fam_afrontamiento', 'Afrontamiento de dificultades', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_1', 'Dinámica familiar – indicador 1', 'LISTA', 7, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_2', 'Dinámica familiar – indicador 2', 'LISTA', 8, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_3', 'Dinámica familiar – indicador 3', 'LISTA', 9, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_4', 'Dinámica familiar – indicador 4', 'LISTA', 10, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_5', 'Dinámica familiar – indicador 5', 'LISTA', 11, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_6', 'Dinámica familiar – indicador 6', 'LISTA', 12, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_7', 'Dinámica familiar – indicador 7', 'LISTA', 13, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_8', 'Dinámica familiar – indicador 8', 'LISTA', 14, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_9', 'Dinámica familiar – indicador 9', 'LISTA', 15, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_10', 'Dinámica familiar – indicador 10', 'LISTA', 16, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_11', 'Dinámica familiar – indicador 11', 'LISTA', 17, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_12', 'Dinámica familiar – indicador 12', 'LISTA', 18, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_pct', 'Dinámica familiar – % (suma ÷ 48 × 100)', 'TEXTO', 19, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('df_concepto', 'Dinámica familiar – concepto', 'TEXTO', 20, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'dinamica_familiar';

-- 6. Estudios (precargados de la hoja de vida; marca Coincide o Corregido)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '6. Estudios (precargados de la hoja de vida; marca Coincide o Corregido)', 'TABLA', 10, TRUE, 'estudios', NULL, NULL, t.version_plantilla, FALSE, 'Estudios y empleos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('e_nivel', 'Nivel', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('e_institucion', 'Institución', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('e_titulo', 'Título', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('e_estado', 'Estado', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('e_fecha', 'Finalización', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('e_resultado', 'Resultado', 'LISTA', 5, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'estudios';

-- 6. Observaciones académicas
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '6. Observaciones académicas', 'CAMPOS', 11, TRUE, 'estudios_obs', NULL, NULL, t.version_plantilla, FALSE, 'Estudios y empleos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('edu_obs', 'Observaciones académicas', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'estudios_obs';

-- 7. Experiencia laboral (una ficha por empresa; precargada de la hoja de vida)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '7. Experiencia laboral (una ficha por empresa; precargada de la hoja de vida)', 'TABLA', 12, TRUE, 'empleos', NULL, NULL, t.version_plantilla, FALSE, 'Estudios y empleos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('l_empresa', 'Empresa', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_cargo', 'Cargo', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_contrato', 'Tipo de contrato', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_ingreso', 'Fecha de ingreso', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_retiro', 'Fecha de retiro', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_salario', 'Salario', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_resultado', 'Resultado', 'LISTA', 6, 'Coincide' || CHR(10) || 'Corregido', FALSE, NULL::text, 'Coincide', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('l_funciones', 'Funciones', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'empleos';

-- 7. Observaciones laborales
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '7. Observaciones laborales', 'CAMPOS', 13, TRUE, 'empleos_obs', NULL, NULL, t.version_plantilla, FALSE, 'Estudios y empleos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('lab_obs', 'Observaciones laborales', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'empleos_obs';

-- 8. Sector y vivienda
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '8. Sector y vivienda', 'CAMPOS', 14, TRUE, 'sector_vivienda', NULL, NULL, t.version_plantilla, FALSE, 'Vivienda y sector', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('sec_tipo', 'Sector – Tipo (sector)', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_estrato', 'Sector – Estrato', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_ubicacion', 'Sector – Ubicación y puntos de referencia', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_calles', 'Sector – Estado de las calles', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_zonas', 'Sector – Zonas verdes', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_comercio', 'Sector – Locales comerciales y servicios', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_vias', 'Sector – Vías de acceso', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_problematicas', 'Sector – Problemáticas', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('sec_grave', '¿Problemática grave en el sector? (no se imprime)', 'LISTA', 8, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_tipo', 'Vivienda – Tipo', 'TEXTO', 9, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_tenencia', 'Vivienda – Tenencia', 'TEXTO', 10, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_dotacion', 'Vivienda – Dotación', 'TEXTO', 11, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_organizacion', 'Vivienda – Organización / limpieza', 'TEXTO', 12, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_tamano', 'Vivienda – Tamaño', 'TEXTO', 13, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_estado', 'Vivienda – Estado', 'TEXTO', 14, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_aspecto', 'Vivienda – Aspecto físico', 'TEXTO', 15, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_servicios', 'Vivienda – Servicios públicos', 'TEXTO', 16, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_tiempo', 'Vivienda – Tiempo de residencia', 'TEXTO', 17, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_distribucion', 'Vivienda – Distribución', 'TEXTO', 18, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_electro', 'Vivienda – Electrodomésticos', 'TEXTO', 19, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_infra', 'Vivienda – Estado de la infraestructura', 'TEXTO', 20, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('viv_muebles', 'Vivienda – Muebles y enseres', 'TEXTO', 21, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hab_1', 'Aspecto habitacional – indicador 1', 'LISTA', 22, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hab_2', 'Aspecto habitacional – indicador 2', 'LISTA', 23, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hab_3', 'Aspecto habitacional – indicador 3', 'LISTA', 24, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hab_pct', 'Aspecto habitacional – % (suma ÷ 12 × 100)', 'TEXTO', 25, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('hab_concepto', 'Aspecto habitacional – concepto', 'TEXTO', 26, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'sector_vivienda';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('sec_grave', 'Sí', 'ALR-VIS-03')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'sector_vivienda' AND c.clave = v.clave;

-- Módulo virtual · ubicación (agrega UNA fila solo si la visita es virtual)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo virtual · ubicación (agrega UNA fila solo si la visita es virtual)', 'TABLA', 15, TRUE, 'virtual', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('vv_georef', 'Georreferenciación', 'LISTA', 0, 'Corresponde a la dirección suministrada' || CHR(10) || 'No corresponde', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, 'Módulo virtual', 'La ubicación de la visita virtual no corresponde', NULL::text, NULL::integer),
    ('vv_nota', 'Nota de ubicación', 'TEXTO', 1, NULL::text, FALSE, NULL::text, 'La ubicación se tomó durante la videollamada y se compara con la dirección suministrada.', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'virtual';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('vv_georef', 'No corresponde', 'VIS-01')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'virtual' AND c.clave = v.clave;

-- Módulo vehicular · zona de parqueo (agrega UNA fila solo si aplica)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo vehicular · zona de parqueo (agrega UNA fila solo si aplica)', 'TABLA', 16, TRUE, 'vehicular', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('vh_distancia', 'Distancia y dirección', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_calidad', 'Condición del parqueadero', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_tenencia', 'Soportes, valor y con quién lo comparte', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_seguridad', 'Vigilancia, cerramiento y techo', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_tamano', 'Tamaño (Duster a Trafic)', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_seg_ok', '¿El parqueadero ofrece seguridad para el vehículo? (no se imprime)', 'LISTA', 5, 'Sí' || CHR(10) || 'No', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, 'Módulo vehicular', '{vh_seguridad}', NULL::text, NULL::integer),
    ('vh_concepto_seg', 'Seguridad para el vehículo', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_concepto', 'Distancia y tamaño', 'LISTA', 7, 'Adecuados' || CHR(10) || 'No adecuados', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('vh_obs', 'Observaciones', 'TEXTO', 8, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'vehicular';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('vh_seg_ok', 'No', 'VIS-11'),
    ('vh_concepto', 'No adecuados', 'ALR-VIS-09')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'vehicular' AND c.clave = v.clave;

-- 9. Economía del hogar
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Economía del hogar', 'CAMPOS', 17, TRUE, 'economia', NULL, NULL, t.version_plantilla, FALSE, 'Economía', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('eco_total_ing', 'Total ingresos', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_total_eg', 'Total egresos', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_aporte', 'Aporte del candidato', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_pct_candidato', '% que asume el candidato', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_mejor_ingreso', 'Quien tiene mejor ingreso', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_estado', 'Estado de la economía', 'LISTA', 5, 'Economía equilibrada' || CHR(10) || 'Equilibrada con observación' || CHR(10) || 'Economía desequilibrada', FALSE, NULL::text, 'Economía equilibrada', TRUE, NULL::text, NULL::text, NULL::text, 'Concepto económico', '{eco_desequilibrio_det}', NULL::text, NULL::integer),
    ('eco_desequilibrio_det', 'Detalle del desequilibrio (no se imprime)', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_conclusion', 'Conclusión económica', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pf_obs', 'Observaciones del patrimonio', 'TEXTO', 8, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_reportado_act', '¿Reportado actualmente en central de riesgo?', 'LISTA', 9, 'No' || CHR(10) || 'Sí' || CHR(10) || 'Desconoce', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_reportado_ant', '¿Ha estado reportado?', 'LISTA', 10, 'No' || CHR(10) || 'Sí' || CHR(10) || 'Desconoce', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_rep_obs', 'Observaciones de reportes', 'TEXTO', 11, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_1', 'Aspecto económico – indicador 1', 'LISTA', 12, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_2', 'Aspecto económico – indicador 2', 'LISTA', 13, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_3', 'Aspecto económico – indicador 3', 'LISTA', 14, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_pct', 'Aspecto económico – % (suma ÷ 12 × 100)', 'TEXTO', 15, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eco_concepto', 'Aspecto económico – concepto', 'TEXTO', 16, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'economia';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('eco_estado', 'Economía desequilibrada', 'VIS-06')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'economia' AND c.clave = v.clave;

-- 9. Ingresos del hogar (solo quienes conviven con el candidato)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Ingresos del hogar (solo quienes conviven con el candidato)', 'TABLA', 18, TRUE, 'ingresos_hogar', NULL, NULL, t.version_plantilla, FALSE, 'Economía', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('in_integrante', 'Integrante del hogar', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('in_concepto', 'Concepto', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('in_monto', 'Monto', 'NUMERO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'ingresos_hogar';

-- 9. Egresos del hogar (un gasto compartido se registra una sola vez)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Egresos del hogar (un gasto compartido se registra una sola vez)', 'TABLA', 19, TRUE, 'egresos_hogar', NULL, NULL, t.version_plantilla, FALSE, 'Economía', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('eg_concepto', 'Concepto', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eg_monto', 'Monto total', 'NUMERO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('eg_aportantes', 'Quién lo paga y cuánto aporta cada uno', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'egresos_hogar';

-- 9. Patrimonio familiar
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Patrimonio familiar', 'TABLA', 20, TRUE, 'patrimonio_fam', NULL, NULL, t.version_plantilla, FALSE, 'Economía', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pf_tipo', 'Tipo de inmueble – muebles', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pf_ubicacion', 'Ubicación / Marca', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('pf_valor', 'Valor', 'NUMERO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'patrimonio_fam';

-- 9. Deudas y obligaciones
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Deudas y obligaciones', 'TABLA', 21, TRUE, 'deudas', NULL, NULL, t.version_plantilla, FALSE, 'Economía', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('d_producto', 'Obligación / producto', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('d_entidad', 'Entidad', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('d_valor', 'Valor del crédito', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('d_cuotas', 'N° de cuotas', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('d_saldo', 'Saldo pendiente', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('d_cuota', 'Valor mensual de la cuota', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('d_mora', 'Mora / días', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'deudas';

-- Módulo OEA · paso 2: patrimonio en bases de datos (CONFIDENCIAL; lo registra Polygraph)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo OEA · paso 2: patrimonio en bases de datos (CONFIDENCIAL; lo registra Polygraph)', 'TABLA', 22, TRUE, 'oea_bd', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ob_tipo', 'Tipo de mueble o inmueble', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ob_ubicacion', 'Ubicación / Marca', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ob_matricula', 'Matrícula', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'oea_bd';

-- Módulo OEA · paso 3 y análisis de variación (agrega UNA fila solo si es OEA)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo OEA · paso 3 y análisis de variación (agrega UNA fila solo si es OEA)', 'TABLA', 23, TRUE, 'oea', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('oea_resultado', 'Paso 3 – resultado', 'LISTA', 0, 'El candidato informó su patrimonio sin novedad' || CHR(10) || 'No informó su patrimonio después del recordatorio', FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_obs', 'Paso 3 – observaciones', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_col_ant', 'Columna estudio anterior', 'TEXTO', 2, NULL::text, FALSE, NULL::text, 'Primer estudio', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_col_act', 'Columna estudio actual', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_inf_ant', 'Informado – anterior', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_inf_act', 'Informado – actual', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_bd_ant', 'Bases de datos – anterior', 'TEXTO', 6, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_bd_act', 'Bases de datos – actual', 'TEXTO', 7, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_intervalo', 'Tiempo entre estudios', 'TEXTO', 8, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('oea_cmp_obs', 'Observaciones del análisis', 'TEXTO', 9, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'oea';

-- 10. Protocolo de seguridad
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '10. Protocolo de seguridad', 'CAMPOS', 24, TRUE, 'protocolo', NULL, NULL, t.version_plantilla, FALSE, 'Seguridad', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ps_1', 'Pregunta 1 – respuesta', 'LISTA', 0, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_1_det', 'Pregunta 1 – detalle (obligatorio si es Sí)', 'TEXTO', 1, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_1_ref', 'Pregunta 1 – Ref.', 'TEXTO', 2, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_2', 'Pregunta 2 – respuesta', 'LISTA', 3, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_2_det', 'Pregunta 2 – detalle (obligatorio si es Sí)', 'TEXTO', 4, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_2_ref', 'Pregunta 2 – Ref.', 'TEXTO', 5, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_3', 'Pregunta 3 – respuesta', 'LISTA', 6, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_3_det', 'Pregunta 3 – detalle (obligatorio si es Sí)', 'TEXTO', 7, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_3_ref', 'Pregunta 3 – Ref.', 'TEXTO', 8, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_4', 'Pregunta 4 – respuesta', 'LISTA', 9, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_4_det', 'Pregunta 4 – detalle (obligatorio si es Sí)', 'TEXTO', 10, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_4_ref', 'Pregunta 4 – Ref.', 'TEXTO', 11, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_5', 'Pregunta 5 – respuesta', 'LISTA', 12, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_5_det', 'Pregunta 5 – detalle (obligatorio si es Sí)', 'TEXTO', 13, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_5_ref', 'Pregunta 5 – Ref.', 'TEXTO', 14, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_6', 'Pregunta 6 – respuesta', 'LISTA', 15, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_6_det', 'Pregunta 6 – detalle (obligatorio si es Sí)', 'TEXTO', 16, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_6_ref', 'Pregunta 6 – Ref.', 'TEXTO', 17, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_7', 'Pregunta 7 – respuesta', 'LISTA', 18, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_7_det', 'Pregunta 7 – detalle (obligatorio si es Sí)', 'TEXTO', 19, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_7_ref', 'Pregunta 7 – Ref.', 'TEXTO', 20, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_8', 'Pregunta 8 – respuesta', 'LISTA', 21, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_8_det', 'Pregunta 8 – detalle (obligatorio si es Sí)', 'TEXTO', 22, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_8_ref', 'Pregunta 8 – Ref.', 'TEXTO', 23, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_9', 'Pregunta 9 – respuesta', 'LISTA', 24, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_9_det', 'Pregunta 9 – detalle (obligatorio si es Sí)', 'TEXTO', 25, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_9_ref', 'Pregunta 9 – Ref.', 'TEXTO', 26, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_11', 'Pregunta 11 – respuesta', 'LISTA', 27, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_11_det', 'Pregunta 11 – detalle (obligatorio si es Sí)', 'TEXTO', 28, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_11_ref', 'Pregunta 11 – Ref.', 'TEXTO', 29, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_12', 'Pregunta 12 – respuesta', 'LISTA', 30, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_12_det', 'Pregunta 12 – detalle (obligatorio si es Sí)', 'TEXTO', 31, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_12_ref', 'Pregunta 12 – Ref.', 'TEXTO', 32, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_13', 'Pregunta 13 – respuesta', 'LISTA', 33, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_13_det', 'Pregunta 13 – detalle (obligatorio si es Sí)', 'TEXTO', 34, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_13_ref', 'Pregunta 13 – Ref.', 'TEXTO', 35, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_14', 'Pregunta 14 – respuesta', 'LISTA', 36, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_14_det', 'Pregunta 14 – detalle (obligatorio si es Sí)', 'TEXTO', 37, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_14_ref', 'Pregunta 14 – Ref.', 'TEXTO', 38, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_15', 'Pregunta 15 – respuesta', 'LISTA', 39, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_15_det', 'Pregunta 15 – detalle (obligatorio si es Sí)', 'TEXTO', 40, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_15_ref', 'Pregunta 15 – Ref.', 'TEXTO', 41, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_16', 'Pregunta 16 – respuesta', 'LISTA', 42, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_16_det', 'Pregunta 16 – detalle (obligatorio si es Sí)', 'TEXTO', 43, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_16_ref', 'Pregunta 16 – Ref.', 'TEXTO', 44, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_17', 'Pregunta 17 – respuesta', 'LISTA', 45, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_17_det', 'Pregunta 17 – detalle (obligatorio si es Sí)', 'TEXTO', 46, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_17_ref', 'Pregunta 17 – Ref.', 'TEXTO', 47, NULL::text, FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_3_quien', 'Pregunta 3 – ¿quién? (no se imprime)', 'LISTA', 48, '—' || CHR(10) || 'Candidato' || CHR(10) || 'Familiar' || CHR(10) || 'Candidato y familiar', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_4_quien', 'Pregunta 4 – ¿quién? (no se imprime)', 'LISTA', 49, '—' || CHR(10) || 'Candidato' || CHR(10) || 'Familiar' || CHR(10) || 'Candidato y familiar', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_5_quien', 'Pregunta 5 – ¿quién? (no se imprime)', 'LISTA', 50, '—' || CHR(10) || 'Candidato' || CHR(10) || 'Familiar' || CHR(10) || 'Candidato y familiar', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ps_6_salvoconducto', 'Pregunta 6 – ¿tiene salvoconducto? (no se imprime)', 'LISTA', 51, '—' || CHR(10) || 'Sí' || CHR(10) || 'No', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Protocolo de seguridad (pregunta 6)', '{ps_6_det}', NULL::text, NULL::integer),
    ('ps_7_impacto', 'Pregunta 7 – ¿afecta su economía? (no se imprime)', 'LISTA', 52, '—' || CHR(10) || 'Sí' || CHR(10) || 'No', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Protocolo de seguridad (pregunta 7)', '{ps_7_det}', NULL::text, NULL::integer),
    ('ps_9_tipo', 'Pregunta 9 – vínculo o víctima (no se imprime)', 'LISTA', 53, '—' || CHR(10) || 'Vínculo (relación, colaboración o negocios)' || CHR(10) || 'Víctima (extorsión u otra)', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Protocolo de seguridad (pregunta 9)', '{ps_9_det}', NULL::text, NULL::integer),
    ('ps_15_tipo', 'Pregunta 15 – vínculo o víctima (no se imprime)', 'LISTA', 54, '—' || CHR(10) || 'Vínculo (relación, colaboración o negocios)' || CHR(10) || 'Víctima (extorsión u otra)', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Protocolo de seguridad (pregunta 15)', '{ps_15_det}', NULL::text, NULL::integer),
    ('ps_17_actual', 'Pregunta 17 – consumo (no se imprime)', 'LISTA', 55, '—' || CHR(10) || 'Consumo actual' || CHR(10) || 'Consumo en el pasado', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, 'Protocolo de seguridad (pregunta 17)', '{ps_17_det}', NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'protocolo';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('ps_3_quien', 'Familiar', 'ALR-VIS-01'),
    ('ps_3_quien', 'Candidato y familiar', 'ALR-VIS-01'),
    ('ps_4_quien', 'Familiar', 'ALR-VIS-01'),
    ('ps_4_quien', 'Candidato y familiar', 'ALR-VIS-01'),
    ('ps_5_quien', 'Familiar', 'ALR-VIS-01'),
    ('ps_5_quien', 'Candidato y familiar', 'ALR-VIS-01'),
    ('ps_6_salvoconducto', 'No', 'VIS-03'),
    ('ps_6_salvoconducto', 'Sí', 'ALR-VIS-04'),
    ('ps_7_impacto', 'Sí', 'VIS-07'),
    ('ps_8', 'Sí', 'ALR-VIS-02'),
    ('ps_9_tipo', 'Vínculo (relación, colaboración o negocios)', 'VIS-02'),
    ('ps_9_tipo', 'Víctima (extorsión u otra)', 'ALR-VIS-06'),
    ('ps_15_tipo', 'Vínculo (relación, colaboración o negocios)', 'VIS-02'),
    ('ps_15_tipo', 'Víctima (extorsión u otra)', 'ALR-VIS-06'),
    ('ps_17_actual', 'Consumo actual', 'VIS-05')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'protocolo' AND c.clave = v.clave;

-- Módulo teletrabajo (agrega UNA fila solo si aplica)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo teletrabajo (agrega UNA fila solo si aplica)', 'TABLA', 25, TRUE, 'teletrabajo', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('tt_1', 'Pregunta 1', 'LISTA', 0, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_2', 'Pregunta 2', 'LISTA', 1, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_3', 'Pregunta 3', 'LISTA', 2, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_4', 'Pregunta 4', 'LISTA', 3, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_5', 'Pregunta 5', 'LISTA', 4, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_6', 'Pregunta 6', 'LISTA', 5, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_7', 'Pregunta 7', 'LISTA', 6, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_8', 'Pregunta 8', 'LISTA', 7, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_9', 'Pregunta 9', 'LISTA', 8, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_10', 'Pregunta 10', 'LISTA', 9, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_11', 'Pregunta 11', 'LISTA', 10, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_12', 'Pregunta 12', 'LISTA', 11, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_13', 'Pregunta 13', 'LISTA', 12, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_14', 'Pregunta 14', 'LISTA', 13, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_15', 'Pregunta 15', 'LISTA', 14, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_16', 'Pregunta 16', 'LISTA', 15, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_17', 'Pregunta 17', 'LISTA', 16, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_18', 'Pregunta 18', 'LISTA', 17, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_19', 'Pregunta 19', 'LISTA', 18, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_20', 'Pregunta 20', 'LISTA', 19, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_21', 'Pregunta 21', 'LISTA', 20, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_22', 'Pregunta 22', 'LISTA', 21, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_23', 'Pregunta 23', 'LISTA', 22, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_24', 'Pregunta 24', 'LISTA', 23, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_25', 'Pregunta 25', 'LISTA', 24, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_26', 'Pregunta 26', 'LISTA', 25, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_27', 'Pregunta 27', 'LISTA', 26, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_28', 'Pregunta 28', 'LISTA', 27, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_29', 'Pregunta 29', 'LISTA', 28, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_30', 'Pregunta 30', 'LISTA', 29, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_31', 'Pregunta 31', 'LISTA', 30, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_32', 'Pregunta 32', 'LISTA', 31, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_33', 'Pregunta 33', 'LISTA', 32, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_34', 'Pregunta 34', 'LISTA', 33, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_35', 'Pregunta 35', 'LISTA', 34, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, 'Módulo teletrabajo', 'Pregunta 35 de seguridad de la información sin cumplir', NULL::text, NULL::integer),
    ('tt_36', 'Pregunta 36', 'LISTA', 35, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, 'Módulo teletrabajo', 'Pregunta 36 de seguridad de la información sin cumplir', NULL::text, NULL::integer),
    ('tt_37', 'Pregunta 37', 'LISTA', 36, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, 'Módulo teletrabajo', 'Pregunta 37 de seguridad de la información sin cumplir', NULL::text, NULL::integer),
    ('tt_38', 'Pregunta 38', 'LISTA', 37, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'Sí', FALSE, NULL::text, NULL::text, NULL::text, 'Módulo teletrabajo', 'Pregunta 38 de seguridad de la información sin cumplir', NULL::text, NULL::integer),
    ('tt_obs', 'Observaciones', 'TEXTO', 38, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_cond', 'Concepto de las condiciones físicas', 'LISTA', 39, 'Cumple' || CHR(10) || 'Cumple con observaciones' || CHR(10) || 'No cumple', FALSE, NULL::text, 'Cumple', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tt_seg', 'Seguridad de la información', 'TEXTO', 40, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tp_pos', 'Aspectos positivos', 'TEXTO', 41, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tp_neg', 'Aspectos negativos', 'TEXTO', 42, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tp_dif', 'Dificultades', 'TEXTO', 43, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_1', 'Indicador teletrabajo 1', 'LISTA', 44, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_2', 'Indicador teletrabajo 2', 'LISTA', 45, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_3', 'Indicador teletrabajo 3', 'LISTA', 46, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_4', 'Indicador teletrabajo 4', 'LISTA', 47, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_5', 'Indicador teletrabajo 5', 'LISTA', 48, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_6', 'Indicador teletrabajo 6', 'LISTA', 49, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_7', 'Indicador teletrabajo 7', 'LISTA', 50, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_8', 'Indicador teletrabajo 8', 'LISTA', 51, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_9', 'Indicador teletrabajo 9', 'LISTA', 52, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_10', 'Indicador teletrabajo 10', 'LISTA', 53, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_11', 'Indicador teletrabajo 11', 'LISTA', 54, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_12', 'Indicador teletrabajo 12', 'LISTA', 55, '—' || CHR(10) || '1' || CHR(10) || '2' || CHR(10) || '3' || CHR(10) || '4', FALSE, NULL::text, '—', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_pct', '% teletrabajo (suma ÷ 48 × 100)', 'TEXTO', 56, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('tq_concepto', 'Concepto teletrabajo', 'TEXTO', 57, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'teletrabajo';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('tt_35', 'No', 'VIS-10'),
    ('tt_36', 'No', 'VIS-10'),
    ('tt_37', 'No', 'VIS-10'),
    ('tt_38', 'No', 'VIS-10'),
    ('tt_cond', 'Cumple con observaciones', 'ALR-VIS-05'),
    ('tt_cond', 'No cumple', 'ALR-VIS-05')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'teletrabajo' AND c.clave = v.clave;

-- 11. Concepto final
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '11. Concepto final', 'CAMPOS', 26, TRUE, 'concepto_final', NULL, NULL, t.version_plantilla, FALSE, 'Cierre', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('cf_expectativas', 'Expectativas frente al cargo – metas y proyectos', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('cf_dificultades', 'Dificultades que percibe la familia', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('cf_actitud', 'Actitud del candidato y su familia frente a la visita', 'TEXTO', 2, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('cf_dinamica', 'Conclusión – dinámica familiar', 'TEXTO', 3, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('cf_habitacional', 'Conclusión – aspecto habitacional', 'TEXTO', 4, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('cf_economico', 'Conclusión – aspecto económico', 'TEXTO', 5, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('cf_conducta_neg', 'Conducta negativa durante la visita (no se imprime)', 'LISTA', 6, 'Ninguna' || CHR(10) || 'Se negó a responder preguntas' || CHR(10) || 'Se negó a mostrar espacios de la vivienda' || CHR(10) || 'Se negó a las fotos' || CHR(10) || 'Trato irrespetuoso con el visitador', FALSE, NULL::text, 'Ninguna', FALSE, NULL::text, NULL::text, NULL::text, 'Concepto final (conducta durante la visita)', '{cf_conducta_neg}', NULL::text, NULL::integer),
    ('cf_bajo_efectos', 'Bajo efectos aparentes de alcohol o sustancias (no se imprime)', 'LISTA', 7, 'No' || CHR(10) || 'Sí', FALSE, NULL::text, 'No', FALSE, NULL::text, NULL::text, NULL::text, 'Concepto final (conducta durante la visita)', '{cf_actitud}', NULL::text, NULL::integer),
    ('cf_conducta', 'Conducta durante la visita (impreso)', 'TEXTO', 8, NULL::text, FALSE, NULL::text, 'Colaboró durante toda la visita', FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('perfil_pct', 'Perfil de la visita – promedio de los tres %', 'TEXTO', 9, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('perfil_concepto', 'Perfil – concepto', 'TEXTO', 10, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 11, NULL::text, FALSE, 'PROFESIONAL', NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('analista_fecha', 'Fecha', 'FECHA', 12, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'concepto_final';
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso CROSS JOIN (VALUES
    ('cf_conducta_neg', 'Se negó a responder preguntas', 'VIS-08'),
    ('cf_conducta_neg', 'Se negó a mostrar espacios de la vivienda', 'VIS-08'),
    ('cf_conducta_neg', 'Se negó a las fotos', 'VIS-08'),
    ('cf_conducta_neg', 'Trato irrespetuoso con el visitador', 'VIS-08'),
    ('cf_bajo_efectos', 'Sí', 'VIS-09')
) AS v (clave, opcion, codigo) WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'concepto_final' AND c.clave = v.clave;

-- 12. Fotos de la vivienda (mínimo 3: fachada, habitación, sala/comedor u otra área social)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '12. Fotos de la vivienda (mínimo 3: fachada, habitación, sala/comedor u otra área social)', 'TABLA', 27, TRUE, 'fotos', NULL, NULL, t.version_plantilla, FALSE, 'Fotos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ft_imagen', 'Foto', 'ARCHIVO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ft_descripcion', 'Descripción', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'fotos';

-- Módulo vehicular · fotos de la zona de parqueo (mínimo 2)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo vehicular · fotos de la zona de parqueo (mínimo 2)', 'TABLA', 28, TRUE, 'fotos_vehicular', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ft_imagen', 'Foto', 'ARCHIVO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ft_descripcion', 'Descripción', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'fotos_vehicular';

-- Módulo teletrabajo · fotos del lugar (20: 4 con el candidato, 13 del lugar, 3 de la calle)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Módulo teletrabajo · fotos del lugar (20: 4 con el candidato, 13 del lugar, 3 de la calle)', 'TABLA', 29, TRUE, 'fotos_teletrabajo', NULL, NULL, t.version_plantilla, FALSE, 'Módulos', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ft_imagen', 'Foto', 'ARCHIVO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer),
    ('ft_descripcion', 'Descripción', 'TEXTO', 1, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'fotos_teletrabajo';

-- Anexo 2 · enlaces
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Anexo 2 · enlaces', 'CAMPOS', 30, TRUE, 'anexos', NULL, NULL, t.version_plantilla, FALSE, 'Cierre', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, campo_candidato, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, NULL, v.fuente, v.detalle, v.cand, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('anexos_enlace', 'Decadactilar, grafológico y habeas data', 'TEXTO', 0, NULL::text, FALSE, NULL::text, NULL::text, FALSE, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::integer)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, fuente, detalle, cand, maximo)
WHERE t.nombre_progreso = 'Visita Domiciliaria  presencial' AND s.clave = 'anexos';
