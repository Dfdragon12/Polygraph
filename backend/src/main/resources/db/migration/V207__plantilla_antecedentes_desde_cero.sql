-- Plantilla de "Antecedentes legales" DESDE CERO (2026-10-06, pedido del usuario), con el modelo de
-- hallazgos de V206: cada Ref./Calificación es un campo HALLAZGO (el analista elige del catálogo de
-- hallazgos), las listas que generan hallazgo lo dicen por opción (Estado del documento → LEG-01,
-- Coincide = No → LEG-12), ev_criticidad es la CRITICIDAD y ev_conduccion la CONDICION; puntaje,
-- fórmula, semáforo, notas, numeración y la tabla de hallazgos los calcula el sistema.
-- Claves = marcadores de docs/antecedentes/VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx.
--
-- BORRA todo lo que tenía la plantilla de Antecedentes (secciones, campos, archivados) y lo que se
-- había diligenciado en ella (formulario_datos y formulario_filas caen en cascada). El Word subido
-- (formulario_plantilla_config) se conserva. Generado con un script desde el diccionario original.
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- En orden, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE).
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES');
UPDATE tipos_progreso SET version_plantilla = version_plantilla + 1 WHERE grupo_calificacion = 'ANTECEDENTES';

-- 1. Datos del evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '1. Datos del evaluado', 'CAMPOS', 0, TRUE, 'datos_evaluado', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('estudio_numero', 'N° de estudio', 'TEXTO', 0, NULL, FALSE, 'NUMERO_ESTUDIO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('fecha_emision', 'Fecha de emisión', 'TEXTO', 1, NULL, FALSE, 'FECHA_EMISION', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_cliente', 'Cliente', 'TEXTO', 3, NULL, FALSE, 'CLIENTE', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_cargo', 'Cargo', 'TEXTO', 4, NULL, FALSE, 'CARGO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_criticidad', 'Criticidad del cargo', 'LISTA', 5, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL, NULL, TRUE, 'CRITICIDAD', NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'datos_evaluado';

-- 2. Resultado del servicio
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '2. Resultado del servicio', 'CAMPOS', 1, TRUE, 'resultado_servicio', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_BAJO', NULL, NULL, NULL, NULL),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_MEDIO', NULL, NULL, NULL, NULL),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_ALTO', NULL, NULL, NULL, NULL),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FORMULA', NULL, NULL, NULL, NULL),
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTAJE', NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'resultado_servicio';

-- 3. Hallazgos con impacto en el nivel de riesgo
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo', 'TABLA', 2, TRUE, 'hallazgos', t.version_plantilla, TRUE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
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
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'hallazgos';

-- 4. Hallazgos sin impacto en el nivel de riesgo
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo', 'TABLA', 3, TRUE, 'informativos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'informativos';

-- 5. Validación del evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '5. Validación del evaluado', 'CAMPOS', 4, TRUE, 'validacion_evaluado', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ev_tipo_cargo', 'Tipo de cargo (Anexo 2)', 'LISTA', 0, 'Manejo de dinero, valores o inventarios' || CHR(10) || 'Cargos documentales, jurídicos o administrativos' || CHR(10) || 'Logística, transporte y conducción' || CHR(10) || 'Seguridad y vigilancia' || CHR(10) || 'Escoltas y transporte de valores' || CHR(10) || 'Acceso a información confidencial o sistemas' || CHR(10) || 'Trabajo con niños, adultos mayores o población vulnerable' || CHR(10) || 'Cargos directivos o con relación con entidades públicas' || CHR(10) || 'Comercial, ventas y atención al cliente' || CHR(10) || 'Cobranza y recuperación de cartera' || CHR(10) || 'Compras, abastecimiento y relación con proveedores' || CHR(10) || 'Comercio exterior, aduanas y zonas francas' || CHR(10) || 'Hidrocarburos, minería y combustibles' || CHR(10) || 'Salud, farmacéutico y medicamentos' || CHR(10) || 'Construcción, mantenimiento y operación de maquinaria' || CHR(10) || 'Cargos operativos y de apoyo' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ev_conduccion', '¿El cargo implica conducción?', 'LISTA', 1, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, 'CONDICION', NULL, NULL, NULL, NULL, NULL),
    ('ev_estado_doc', 'Estado del documento', 'LISTA', 2, 'Vigente' || CHR(10) || 'En trámite' || CHR(10) || 'Cancelada por muerte' || CHR(10) || 'Cancelada por doble cedulación' || CHR(10) || 'No encontrada', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Registraduría', 'Estado del documento: {ev_estado_doc}'),
    ('ev_rut_actividad', 'Actividad económica (RUT)', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'validacion_evaluado';

-- 6. Declaración del evaluado frente a lo verificado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '6. Declaración del evaluado frente a lo verificado', 'CAMPOS', 5, TRUE, 'declaracion_evaluado', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('dec1_respuesta', 'Pregunta 1 – Respuesta', 'LISTA', 0, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec1_detalle', 'Pregunta 1 – Detalle declarado', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec1_verificacion', 'Pregunta 1 – Resultado de la verificación', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec1_coincide', 'Pregunta 1 – Coincide', 'LISTA', 3, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Declaración del evaluado', 'La respuesta a la pregunta 1 no coincide con lo verificado'),
    ('dec2_respuesta', 'Pregunta 2 – Respuesta', 'LISTA', 4, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec2_detalle', 'Pregunta 2 – Detalle declarado', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec2_verificacion', 'Pregunta 2 – Resultado de la verificación', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec2_coincide', 'Pregunta 2 – Coincide', 'LISTA', 7, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Declaración del evaluado', 'La respuesta a la pregunta 2 no coincide con lo verificado'),
    ('dec3_respuesta', 'Pregunta 3 – Respuesta', 'LISTA', 8, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec3_detalle', 'Pregunta 3 – Detalle declarado', 'TEXTO', 9, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec3_verificacion', 'Pregunta 3 – Resultado de la verificación', 'TEXTO', 10, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('dec3_coincide', 'Pregunta 3 – Coincide', 'LISTA', 11, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Declaración del evaluado', 'La respuesta a la pregunta 3 no coincide con lo verificado')
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'declaracion_evaluado';

-- 7. Consultas en listas nacionales
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '7. Consultas en listas nacionales', 'TABLA', 6, TRUE, 'listas_nacionales', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ln_fuente', 'Fuente', 'LISTA', 0, 'Registraduría' || CHR(10) || 'Procuraduría' || CHR(10) || 'Policía Nacional' || CHR(10) || 'INPEC' || CHR(10) || 'Medidas correctivas (RNMC)' || CHR(10) || 'Personería de Bogotá' || CHR(10) || 'Rama Judicial' || CHR(10) || 'Contaduría General (BDME)' || CHR(10) || 'Contraloría' || CHR(10) || 'DIAN – Proveedores ficticios' || CHR(10) || 'RUES' || CHR(10) || 'Boletines' || CHR(10) || 'RND – Registro Nacional de Desaparecidos' || CHR(10) || 'RUNCOM' || CHR(10) || 'PJP' || CHR(10) || 'Validación en Google', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ln_resultado', 'Resultado', 'LISTA', 1, 'Sin novedad' || CHR(10) || 'Con hallazgo' || CHR(10) || 'No disponible', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ln_ref', 'Ref.', 'LISTA', 2, '—' || CHR(10) || 'LEG-03' || CHR(10) || 'LEG-06' || CHR(10) || 'LEG-08' || CHR(10) || 'LEG-09' || CHR(10) || 'LEG-13' || CHR(10) || 'LEG-16' || CHR(10) || 'LEG-18' || CHR(10) || 'ALR-04', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, '{ln_fuente}', '{ln_observacion}'),
    ('ln_estado', 'Estado', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ln_fecha', 'Fecha', 'FECHA', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ln_soporte', 'Soporte', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('ln_observacion', 'Observación', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'listas_nacionales';

-- 8. Listas restrictivas, vinculantes, internacionales y PEP
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '8. Listas restrictivas, vinculantes, internacionales y PEP', 'TABLA', 7, TRUE, 'listas_internacionales', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('li_lista', 'Lista', 'LISTA', 0, 'Consejo de Seguridad ONU (vinculante)' || CHR(10) || 'OFAC – Lista Clinton' || CHR(10) || 'HM Treasury – Reino Unido' || CHR(10) || 'Otras listas restrictivas' || CHR(10) || 'Interpol' || CHR(10) || 'Europol' || CHR(10) || 'Banco Mundial' || CHR(10) || 'BID' || CHR(10) || 'Noticias criminales' || CHR(10) || 'PEP nacionales' || CHR(10) || 'PEP internacionales' || CHR(10) || 'SECOP', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('li_resultado', 'Resultado', 'LISTA', 1, 'Sin novedad' || CHR(10) || 'Con hallazgo' || CHR(10) || 'No disponible', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('li_coincidencia', 'Coincidencia', 'LISTA', 2, '—' || CHR(10) || 'Documento' || CHR(10) || 'Solo nombre', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('li_ref', 'Ref.', 'LISTA', 3, '—' || CHR(10) || 'LEG-02' || CHR(10) || 'LEG-04' || CHR(10) || 'LEG-07' || CHR(10) || 'LEG-13' || CHR(10) || 'LEG-15' || CHR(10) || 'ALR-01' || CHR(10) || 'ALR-02', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, '{li_lista}', '{li_observacion}'),
    ('li_fecha', 'Fecha', 'FECHA', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('li_soporte', 'Soporte', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('li_observacion', 'Observación', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'listas_internacionales';

-- 9. Tránsito – SIMIT
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '9. Tránsito – SIMIT', 'CAMPOS', 8, TRUE, 'transito_simit', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('simit_multas', 'SIMIT – Multas', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('simit_comparendos', 'SIMIT – Comparendos', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('simit_acuerdos', 'SIMIT – Acuerdos de pago', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('simit_inmov', 'SIMIT – Inmovilizaciones', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('simit_ref', 'SIMIT – Ref.', 'LISTA', 4, '—' || CHR(10) || 'LEG-17', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, 'SIMIT', 'Multas: {simit_multas}'),
    ('transito_nota', 'Nota de tránsito', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'ev_conduccion', 'Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos.', NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'transito_simit';

-- 9. Tránsito – SIMUR (complementaria Bogotá)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '9. Tránsito – SIMUR (complementaria Bogotá)', 'TABLA', 9, TRUE, 'simur', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('simur_comparendo', 'Comparendo', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('simur_placa', 'Placa', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('simur_fecha', 'Fecha de infracción', 'FECHA', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'simur';

-- 9. Tránsito – RUNT
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '9. Tránsito – RUNT', 'TABLA', 10, TRUE, 'runt', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('runt_categoria', 'Categoría', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('runt_estado', 'Estado de la licencia', 'LISTA', 1, 'Vigente' || CHR(10) || 'Vencida' || CHR(10) || 'Suspendida' || CHR(10) || 'Cancelada' || CHR(10) || '—', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('runt_vencimiento', 'Vencimiento', 'FECHA', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('runt_categoria_ok', '¿Categoría corresponde al cargo?', 'LISTA', 3, 'Sí' || CHR(10) || 'No' || CHR(10) || '—', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('runt_ref', 'Ref.', 'LISTA', 4, '—' || CHR(10) || 'LEG-10' || CHR(10) || 'LEG-14', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, 'RUNT', 'Categoría {runt_categoria}: {runt_estado}')
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'runt';

-- 10. Identidad – Alertas (Reconocer / Datacrédito)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Identidad – Alertas (Reconocer / Datacrédito)', 'TABLA', 11, TRUE, 'alertas_id', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('a_fuente', 'Fuente', 'LISTA', 0, 'Reconocer' || CHR(10) || 'Datacrédito', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('a_fecha', 'Fecha', 'FECHA', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('a_novedad', 'Novedad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('a_descripcion', 'Descripción', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('a_tratamiento', 'Tratamiento', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('a_ref', 'Ref.', 'LISTA', 5, '—' || CHR(10) || 'ALR-03' || CHR(10) || 'LEG-24', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, '{a_fuente}', '{a_descripcion}')
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'alertas_id';

-- 10. Identidad y contacto
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Identidad y contacto', 'CAMPOS', 12, TRUE, 'identidad_contacto', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('alertas_id_nota', 'Nota de alertas', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'alertas_id', 'Sin alertas reportadas.', NULL, NULL),
    ('rec_dir_coincide', '¿La dirección declarada aparece en Reconocer?', 'LISTA', 1, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('rec_residencial', 'Información residencial', 'LISTA', 2, 'Estable' || CHR(10) || 'No estable' || CHR(10) || 'Sin información', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'identidad_contacto';

-- 10. Direcciones
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Direcciones', 'TABLA', 13, TRUE, 'direcciones', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('d_direccion', 'Dirección', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('d_tipo', 'Tipo', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('d_ciudad', 'Ciudad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('d_desde', 'Reportada desde', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('d_ultimo', 'Último reporte', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('d_coincide', '¿Coincide con la declarada?', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'direcciones';

-- 10. Teléfonos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Teléfonos', 'TABLA', 14, TRUE, 'telefonos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('t_numero', 'Teléfono', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('t_tipo', 'Tipo (RES – CORR – LAB)', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('t_ciudad', 'Ciudad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('t_ultimo', 'Último reporte', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('t_coincide', '¿Coincide con el declarado?', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'telefonos';

-- 10. Correos electrónicos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Correos electrónicos', 'TABLA', 15, TRUE, 'correos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('e_correo', 'Correo', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('e_ultimo', 'Último reporte', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('e_coincide', '¿Coincide con el declarado?', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'correos';

-- 11. Procesos judiciales – Rama Judicial
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '11. Procesos judiciales – Rama Judicial', 'TABLA', 16, TRUE, 'procesos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('p_numero', 'Proceso N°', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NUMERO_FILA', NULL, NULL, NULL, NULL),
    ('p_radicado', 'Radicado', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_tipo', 'Tipo', 'LISTA', 2, 'Penal' || CHR(10) || 'Civil' || CHR(10) || 'Laboral' || CHR(10) || 'Familia' || CHR(10) || 'Administrativo' || CHR(10) || 'Ejecutivo' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_calidad', 'Calidad del evaluado', 'LISTA', 3, 'Investigado / indiciado' || CHR(10) || 'Imputado / acusado' || CHR(10) || 'Condenado' || CHR(10) || 'Demandado' || CHR(10) || 'Demandante' || CHR(10) || 'Denunciante' || CHR(10) || 'Víctima' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_delito', 'Delito o asunto', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_despacho', 'Despacho', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_fecha', 'Fecha del proceso', 'FECHA', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_etapa', 'Etapa', 'TEXTO', 7, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_estado', 'Estado', 'LISTA', 8, 'Activo' || CHR(10) || 'Pena vigente' || CHR(10) || 'Terminado' || CHR(10) || 'Archivado' || CHR(10) || 'Inactivo', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_motivo', 'Motivo de terminación', 'LISTA', 9, '—' || CHR(10) || 'Archivo' || CHR(10) || 'Preclusión' || CHR(10) || 'Prescripción' || CHR(10) || 'Desistimiento' || CHR(10) || 'Conciliación' || CHR(10) || 'Principio de oportunidad' || CHR(10) || 'Sentencia absolutoria' || CHR(10) || 'Sentencia condenatoria' || CHR(10) || 'Fallo a favor' || CHR(10) || 'Fallo en contra' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_justificacion', 'Justificación', 'TEXTO', 10, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_relacion', 'Relación con el cargo', 'LISTA', 11, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('p_calificacion', 'Calificación', 'LISTA', 12, '—' || CHR(10) || 'LEG-05' || CHR(10) || 'LEG-11' || CHR(10) || 'LEG-19' || CHR(10) || 'LEG-20' || CHR(10) || 'LEG-21' || CHR(10) || 'LEG-22' || CHR(10) || 'LEG-23', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, 'Rama Judicial', 'Proceso N° {p_numero}: {p_delito} ({p_estado})'),
    ('p_obs', 'Observaciones', 'TEXTO', 13, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'procesos';

-- 12. Procesos judiciales – Bases privadas
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '12. Procesos judiciales – Bases privadas', 'TABLA', 17, TRUE, 'procesos_privados', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pp_numero', 'Proceso N°', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NUMERO_FILA', NULL, NULL, NULL, NULL),
    ('pp_calidad', 'Calidad del evaluado', 'LISTA', 1, 'Investigado / indiciado' || CHR(10) || 'Imputado / acusado' || CHR(10) || 'Condenado' || CHR(10) || 'Demandado' || CHR(10) || 'Demandante' || CHR(10) || 'Denunciante' || CHR(10) || 'Víctima' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_delito', 'Delito o asunto', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_despacho', 'Despacho', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_fecha', 'Fecha del proceso', 'FECHA', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_etapa', 'Etapa', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_estado', 'Estado', 'LISTA', 6, 'Activo' || CHR(10) || 'Pena vigente' || CHR(10) || 'Terminado' || CHR(10) || 'Archivado' || CHR(10) || 'Inactivo', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_relacion', 'Relación con el cargo', 'LISTA', 7, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('pp_calificacion', 'Calificación', 'LISTA', 8, '—' || CHR(10) || 'LEG-05' || CHR(10) || 'LEG-11' || CHR(10) || 'LEG-19' || CHR(10) || 'LEG-20' || CHR(10) || 'LEG-21' || CHR(10) || 'LEG-22' || CHR(10) || 'LEG-23', FALSE, NULL, '—', FALSE, 'HALLAZGO', NULL, NULL, NULL, 'Bases privadas', 'Proceso N° {pp_numero}: {pp_delito} ({pp_estado})'),
    ('pp_obs', 'Observaciones', 'TEXTO', 9, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'procesos_privados';

-- 11 y 12. Notas de procesos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '11 y 12. Notas de procesos', 'CAMPOS', 18, TRUE, 'notas_procesos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('procesos_nota', 'Nota de procesos (Rama Judicial)', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'procesos', 'No se encontraron procesos judiciales en Rama Judicial.', NULL, NULL),
    ('procesos_priv_nota', 'Nota de procesos (bases privadas)', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'NOTA', 'procesos_privados', 'No se encontraron procesos en bases privadas.', NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'notas_procesos';

-- 13. Concepto del analista
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '13. Concepto del analista', 'CAMPOS', 19, TRUE, 'concepto_analista', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('concepto', 'Concepto del analista', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 1, NULL, FALSE, 'PROFESIONAL', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL),
    ('analista_fecha', 'Fecha', 'FECHA', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'concepto_analista';

-- Hallazgo que genera cada opción.
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo
FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ev_estado_doc', 'Cancelada por muerte', 'LEG-01'),
    ('ev_estado_doc', 'Cancelada por doble cedulación', 'LEG-01'),
    ('ev_estado_doc', 'No encontrada', 'LEG-01'),
    ('dec1_coincide', 'No', 'LEG-12'),
    ('dec2_coincide', 'No', 'LEG-12'),
    ('dec3_coincide', 'No', 'LEG-12')
) AS v (clave, opcion, codigo)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND c.clave = v.clave;

-- Desde ahora el servicio califica por hallazgos (lo mismo que pone el constructor al guardar).
UPDATE tipos_progreso SET grupo_calificacion = 'HALLAZGOS' WHERE grupo_calificacion = 'ANTECEDENTES';
