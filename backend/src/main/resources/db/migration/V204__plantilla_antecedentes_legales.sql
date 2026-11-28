-- Formulario del subservicio "Antecedentes legales" (2026-10-05, Prompt 2 de docs/antecedentes/).
-- Generado desde docs/antecedentes/Diccionario_marcadores_Antecedentes_FINAL.xlsx: las claves son
-- EXACTAMENTE las de los marcadores de VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx (lo comprueba
-- PlantillaAntecedentesWordTest). Aplica al subservicio con tipos_progreso.grupo_calificacion =
-- 'ANTECEDENTES' (V202); si en esta base no hay ninguno, no hace nada.
--
-- · Los datos de la ficha (NOMBRE_COMPLETO, CEDULA, RUT...) no son columnas: salen de los
--   marcadores candidato[...] del Word, que siempre traen todos los campos del candidato.
--   (Ojo: aquí no se puede escribir el marcador completo con signo de pesos y llave: Flyway lo
--   toma como una variable suya y la migración no arranca.)
-- · Datos del estudio (N° de estudio, fecha de emisión, cliente, cargo): columnas campo_servicio.
-- · Calculados (fórmula, puntaje, semáforo, notas, p_numero, pp_numero) y la tabla hallazgos: los
--   llena el cálculo propio de Antecedentes (no usa "Califica riesgo" del constructor).
-- · ev_criticidad: obligatoria, sin marcador en el Word. a_ref: sin marcador en el Word.
-- · Columnas Ref./Calificación: "—" por defecto.
-- · Lo que la plantilla tenía antes se ARCHIVA (no se borra): los formularios ya diligenciados
--   conservan sus datos. Las secciones que se reutilizan por clave (listas_nacionales,
--   listas_internacionales) quedan con sus columnas viejas archivadas y las nuevas activas.

-- Archivar lo anterior.
UPDATE formulario_columnas SET activo = FALSE WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES'));
UPDATE formulario_secciones SET activo = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND (clave IS NULL OR clave NOT IN ('datos_evaluado', 'resultado_servicio', 'hallazgos', 'informativos', 'validacion_evaluado', 'declaracion_evaluado', 'listas_nacionales', 'listas_internacionales', 'transito_simit', 'simur', 'runt', 'alertas_id', 'identidad_contacto', 'direcciones', 'telefonos', 'correos', 'procesos', 'procesos_privados', 'notas_procesos', 'concepto_analista'));

-- Nueva versión de la plantilla: los subprocesos ya completados se siguen viendo con la anterior.
UPDATE tipos_progreso SET version_plantilla = version_plantilla + 1 WHERE grupo_calificacion = 'ANTECEDENTES';

-- 1. Datos del evaluado
UPDATE formulario_secciones SET nombre = '1. Datos del evaluado', tipo = 'CAMPOS', orden = 0, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'datos_evaluado';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '1. Datos del evaluado', 'CAMPOS', 0, TRUE, 'datos_evaluado', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'datos_evaluado');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('estudio_numero', 'N° de estudio', 'TEXTO', 0, NULL, FALSE, 'NUMERO_ESTUDIO', NULL, FALSE),
    ('fecha_emision', 'Fecha de emisión', 'TEXTO', 1, NULL, FALSE, 'FECHA_EMISION', NULL, FALSE),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('ev_cliente', 'Cliente', 'TEXTO', 3, NULL, FALSE, 'CLIENTE', NULL, FALSE),
    ('ev_cargo', 'Cargo', 'TEXTO', 4, NULL, FALSE, 'CARGO', NULL, FALSE),
    ('ev_criticidad', 'Criticidad del cargo', 'LISTA', 5, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL, NULL, TRUE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'datos_evaluado' AND s.activo = TRUE;

-- 2. Resultado del servicio
UPDATE formulario_secciones SET nombre = '2. Resultado del servicio', tipo = 'CAMPOS', orden = 1, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'resultado_servicio';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '2. Resultado del servicio', 'CAMPOS', 1, TRUE, 'resultado_servicio', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'resultado_servicio');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE),
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'resultado_servicio' AND s.activo = TRUE;

-- 3. Hallazgos con impacto en el nivel de riesgo
UPDATE formulario_secciones SET nombre = '3. Hallazgos con impacto en el nivel de riesgo', tipo = 'TABLA', orden = 2, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = TRUE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'hallazgos';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo', 'TABLA', 2, TRUE, 'hallazgos', t.version_plantilla, TRUE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'hallazgos');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('h_ref', 'Ref.', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE),
    ('h_descripcion', 'Hallazgo', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE),
    ('h_fuente', 'Fuente', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE),
    ('h_detalle', 'Detalle', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE),
    ('h_puntos', 'Puntos', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'hallazgos' AND s.activo = TRUE;

-- 4. Hallazgos sin impacto en el nivel de riesgo
UPDATE formulario_secciones SET nombre = '4. Hallazgos sin impacto en el nivel de riesgo', tipo = 'TABLA', orden = 3, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'informativos';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo', 'TABLA', 3, TRUE, 'informativos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'informativos');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'informativos' AND s.activo = TRUE;

-- 5. Validación del evaluado
UPDATE formulario_secciones SET nombre = '5. Validación del evaluado', tipo = 'CAMPOS', orden = 4, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'validacion_evaluado';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '5. Validación del evaluado', 'CAMPOS', 4, TRUE, 'validacion_evaluado', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'validacion_evaluado');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ev_tipo_cargo', 'Tipo de cargo (Anexo 2)', 'LISTA', 0, 'Manejo de dinero, valores o inventarios' || CHR(10) || 'Cargos documentales, jurídicos o administrativos' || CHR(10) || 'Logística, transporte y conducción' || CHR(10) || 'Seguridad y vigilancia' || CHR(10) || 'Escoltas y transporte de valores' || CHR(10) || 'Acceso a información confidencial o sistemas' || CHR(10) || 'Trabajo con niños, adultos mayores o población vulnerable' || CHR(10) || 'Cargos directivos o con relación con entidades públicas' || CHR(10) || 'Comercial, ventas y atención al cliente' || CHR(10) || 'Cobranza y recuperación de cartera' || CHR(10) || 'Compras, abastecimiento y relación con proveedores' || CHR(10) || 'Comercio exterior, aduanas y zonas francas' || CHR(10) || 'Hidrocarburos, minería y combustibles' || CHR(10) || 'Salud, farmacéutico y medicamentos' || CHR(10) || 'Construcción, mantenimiento y operación de maquinaria' || CHR(10) || 'Cargos operativos y de apoyo' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE),
    ('ev_conduccion', '¿El cargo implica conducción?', 'LISTA', 1, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('ev_estado_doc', 'Estado del documento', 'LISTA', 2, 'Vigente' || CHR(10) || 'En trámite' || CHR(10) || 'Cancelada por muerte' || CHR(10) || 'Cancelada por doble cedulación' || CHR(10) || 'No encontrada', FALSE, NULL, NULL, FALSE),
    ('ev_rut_actividad', 'Actividad económica (RUT)', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'validacion_evaluado' AND s.activo = TRUE;

-- 6. Declaración del evaluado frente a lo verificado
UPDATE formulario_secciones SET nombre = '6. Declaración del evaluado frente a lo verificado', tipo = 'CAMPOS', orden = 5, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'declaracion_evaluado';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '6. Declaración del evaluado frente a lo verificado', 'CAMPOS', 5, TRUE, 'declaracion_evaluado', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'declaracion_evaluado');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('dec1_respuesta', 'Pregunta 1 – Respuesta', 'LISTA', 0, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('dec1_detalle', 'Pregunta 1 – Detalle declarado', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('dec1_verificacion', 'Pregunta 1 – Resultado de la verificación', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('dec1_coincide', 'Pregunta 1 – Coincide', 'LISTA', 3, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('dec2_respuesta', 'Pregunta 2 – Respuesta', 'LISTA', 4, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('dec2_detalle', 'Pregunta 2 – Detalle declarado', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE),
    ('dec2_verificacion', 'Pregunta 2 – Resultado de la verificación', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE),
    ('dec2_coincide', 'Pregunta 2 – Coincide', 'LISTA', 7, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('dec3_respuesta', 'Pregunta 3 – Respuesta', 'LISTA', 8, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('dec3_detalle', 'Pregunta 3 – Detalle declarado', 'TEXTO', 9, NULL, FALSE, NULL, NULL, FALSE),
    ('dec3_verificacion', 'Pregunta 3 – Resultado de la verificación', 'TEXTO', 10, NULL, FALSE, NULL, NULL, FALSE),
    ('dec3_coincide', 'Pregunta 3 – Coincide', 'LISTA', 11, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'declaracion_evaluado' AND s.activo = TRUE;

-- 7. Consultas en listas nacionales
UPDATE formulario_secciones SET nombre = '7. Consultas en listas nacionales', tipo = 'TABLA', orden = 6, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'listas_nacionales';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '7. Consultas en listas nacionales', 'TABLA', 6, TRUE, 'listas_nacionales', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'listas_nacionales');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ln_fuente', 'Fuente', 'LISTA', 0, 'Registraduría' || CHR(10) || 'Procuraduría' || CHR(10) || 'Policía Nacional' || CHR(10) || 'INPEC' || CHR(10) || 'Medidas correctivas (RNMC)' || CHR(10) || 'Personería de Bogotá' || CHR(10) || 'Rama Judicial' || CHR(10) || 'Contaduría General (BDME)' || CHR(10) || 'Contraloría' || CHR(10) || 'DIAN – Proveedores ficticios' || CHR(10) || 'RUES' || CHR(10) || 'Boletines' || CHR(10) || 'RND – Registro Nacional de Desaparecidos' || CHR(10) || 'RUNCOM' || CHR(10) || 'PJP' || CHR(10) || 'Validación en Google', FALSE, NULL, NULL, FALSE),
    ('ln_resultado', 'Resultado', 'LISTA', 1, 'Sin novedad' || CHR(10) || 'Con hallazgo' || CHR(10) || 'No disponible', FALSE, NULL, NULL, FALSE),
    ('ln_ref', 'Ref.', 'LISTA', 2, '—' || CHR(10) || 'LEG-03' || CHR(10) || 'LEG-06' || CHR(10) || 'LEG-08' || CHR(10) || 'LEG-09' || CHR(10) || 'LEG-13' || CHR(10) || 'LEG-16' || CHR(10) || 'LEG-18' || CHR(10) || 'ALR-04', FALSE, NULL, '—', FALSE),
    ('ln_estado', 'Estado', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('ln_fecha', 'Fecha', 'FECHA', 4, NULL, FALSE, NULL, NULL, FALSE),
    ('ln_soporte', 'Soporte', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE),
    ('ln_observacion', 'Observación', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'listas_nacionales' AND s.activo = TRUE;

-- 8. Listas restrictivas, vinculantes, internacionales y PEP
UPDATE formulario_secciones SET nombre = '8. Listas restrictivas, vinculantes, internacionales y PEP', tipo = 'TABLA', orden = 7, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'listas_internacionales';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '8. Listas restrictivas, vinculantes, internacionales y PEP', 'TABLA', 7, TRUE, 'listas_internacionales', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'listas_internacionales');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('li_lista', 'Lista', 'LISTA', 0, 'Consejo de Seguridad ONU (vinculante)' || CHR(10) || 'OFAC – Lista Clinton' || CHR(10) || 'HM Treasury – Reino Unido' || CHR(10) || 'Otras listas restrictivas' || CHR(10) || 'Interpol' || CHR(10) || 'Europol' || CHR(10) || 'Banco Mundial' || CHR(10) || 'BID' || CHR(10) || 'Noticias criminales' || CHR(10) || 'PEP nacionales' || CHR(10) || 'PEP internacionales' || CHR(10) || 'SECOP', FALSE, NULL, NULL, FALSE),
    ('li_resultado', 'Resultado', 'LISTA', 1, 'Sin novedad' || CHR(10) || 'Con hallazgo' || CHR(10) || 'No disponible', FALSE, NULL, NULL, FALSE),
    ('li_coincidencia', 'Coincidencia', 'LISTA', 2, '—' || CHR(10) || 'Documento' || CHR(10) || 'Solo nombre', FALSE, NULL, NULL, FALSE),
    ('li_ref', 'Ref.', 'LISTA', 3, '—' || CHR(10) || 'LEG-02' || CHR(10) || 'LEG-04' || CHR(10) || 'LEG-07' || CHR(10) || 'LEG-13' || CHR(10) || 'LEG-15' || CHR(10) || 'ALR-01' || CHR(10) || 'ALR-02', FALSE, NULL, '—', FALSE),
    ('li_fecha', 'Fecha', 'FECHA', 4, NULL, FALSE, NULL, NULL, FALSE),
    ('li_soporte', 'Soporte', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE),
    ('li_observacion', 'Observación', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'listas_internacionales' AND s.activo = TRUE;

-- 9. Tránsito – SIMIT
UPDATE formulario_secciones SET nombre = '9. Tránsito – SIMIT', tipo = 'CAMPOS', orden = 8, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'transito_simit';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '9. Tránsito – SIMIT', 'CAMPOS', 8, TRUE, 'transito_simit', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'transito_simit');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('simit_multas', 'SIMIT – Multas', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('simit_comparendos', 'SIMIT – Comparendos', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('simit_acuerdos', 'SIMIT – Acuerdos de pago', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('simit_inmov', 'SIMIT – Inmovilizaciones', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('simit_ref', 'SIMIT – Ref.', 'LISTA', 4, '—' || CHR(10) || 'LEG-17', FALSE, NULL, '—', FALSE),
    ('transito_nota', 'Nota de tránsito', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'transito_simit' AND s.activo = TRUE;

-- 9. Tránsito – SIMUR (complementaria Bogotá)
UPDATE formulario_secciones SET nombre = '9. Tránsito – SIMUR (complementaria Bogotá)', tipo = 'TABLA', orden = 9, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'simur';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '9. Tránsito – SIMUR (complementaria Bogotá)', 'TABLA', 9, TRUE, 'simur', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'simur');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('simur_comparendo', 'Comparendo', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('simur_placa', 'Placa', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('simur_fecha', 'Fecha de infracción', 'FECHA', 2, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'simur' AND s.activo = TRUE;

-- 9. Tránsito – RUNT
UPDATE formulario_secciones SET nombre = '9. Tránsito – RUNT', tipo = 'TABLA', orden = 10, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'runt';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '9. Tránsito – RUNT', 'TABLA', 10, TRUE, 'runt', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'runt');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('runt_categoria', 'Categoría', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('runt_estado', 'Estado de la licencia', 'LISTA', 1, 'Vigente' || CHR(10) || 'Vencida' || CHR(10) || 'Suspendida' || CHR(10) || 'Cancelada' || CHR(10) || '—', FALSE, NULL, NULL, FALSE),
    ('runt_vencimiento', 'Vencimiento', 'FECHA', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('runt_categoria_ok', '¿Categoría corresponde al cargo?', 'LISTA', 3, 'Sí' || CHR(10) || 'No' || CHR(10) || '—', FALSE, NULL, NULL, FALSE),
    ('runt_ref', 'Ref.', 'LISTA', 4, '—' || CHR(10) || 'LEG-10' || CHR(10) || 'LEG-14', FALSE, NULL, '—', FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'runt' AND s.activo = TRUE;

-- 10. Identidad – Alertas (Reconocer / Datacrédito)
UPDATE formulario_secciones SET nombre = '10. Identidad – Alertas (Reconocer / Datacrédito)', tipo = 'TABLA', orden = 11, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'alertas_id';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Identidad – Alertas (Reconocer / Datacrédito)', 'TABLA', 11, TRUE, 'alertas_id', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'alertas_id');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('a_fuente', 'Fuente', 'LISTA', 0, 'Reconocer' || CHR(10) || 'Datacrédito', FALSE, NULL, NULL, FALSE),
    ('a_fecha', 'Fecha', 'FECHA', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('a_novedad', 'Novedad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('a_descripcion', 'Descripción', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('a_tratamiento', 'Tratamiento', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE),
    ('a_ref', 'Ref.', 'LISTA', 5, '—' || CHR(10) || 'ALR-03' || CHR(10) || 'LEG-24', FALSE, NULL, '—', FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'alertas_id' AND s.activo = TRUE;

-- 10. Identidad y contacto
UPDATE formulario_secciones SET nombre = '10. Identidad y contacto', tipo = 'CAMPOS', orden = 12, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'identidad_contacto';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Identidad y contacto', 'CAMPOS', 12, TRUE, 'identidad_contacto', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'identidad_contacto');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('alertas_id_nota', 'Nota de alertas', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE),
    ('rec_dir_coincide', '¿La dirección declarada aparece en Reconocer?', 'LISTA', 1, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('rec_residencial', 'Información residencial', 'LISTA', 2, 'Estable' || CHR(10) || 'No estable' || CHR(10) || 'Sin información', FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'identidad_contacto' AND s.activo = TRUE;

-- 10. Direcciones
UPDATE formulario_secciones SET nombre = '10. Direcciones', tipo = 'TABLA', orden = 13, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'direcciones';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Direcciones', 'TABLA', 13, TRUE, 'direcciones', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'direcciones');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('d_direccion', 'Dirección', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('d_tipo', 'Tipo', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('d_ciudad', 'Ciudad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('d_desde', 'Reportada desde', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('d_ultimo', 'Último reporte', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE),
    ('d_coincide', '¿Coincide con la declarada?', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'direcciones' AND s.activo = TRUE;

-- 10. Teléfonos
UPDATE formulario_secciones SET nombre = '10. Teléfonos', tipo = 'TABLA', orden = 14, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'telefonos';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Teléfonos', 'TABLA', 14, TRUE, 'telefonos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'telefonos');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('t_numero', 'Teléfono', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('t_tipo', 'Tipo (RES – CORR – LAB)', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('t_ciudad', 'Ciudad', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('t_ultimo', 'Último reporte', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('t_coincide', '¿Coincide con el declarado?', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'telefonos' AND s.activo = TRUE;

-- 10. Correos electrónicos
UPDATE formulario_secciones SET nombre = '10. Correos electrónicos', tipo = 'TABLA', orden = 15, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'correos';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '10. Correos electrónicos', 'TABLA', 15, TRUE, 'correos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'correos');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('e_correo', 'Correo', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('e_ultimo', 'Último reporte', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('e_coincide', '¿Coincide con el declarado?', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'correos' AND s.activo = TRUE;

-- 11. Procesos judiciales – Rama Judicial
UPDATE formulario_secciones SET nombre = '11. Procesos judiciales – Rama Judicial', tipo = 'TABLA', orden = 16, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'procesos';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '11. Procesos judiciales – Rama Judicial', 'TABLA', 16, TRUE, 'procesos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'procesos');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('p_numero', 'Proceso N°', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE),
    ('p_radicado', 'Radicado', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('p_tipo', 'Tipo', 'LISTA', 2, 'Penal' || CHR(10) || 'Civil' || CHR(10) || 'Laboral' || CHR(10) || 'Familia' || CHR(10) || 'Administrativo' || CHR(10) || 'Ejecutivo' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE),
    ('p_calidad', 'Calidad del evaluado', 'LISTA', 3, 'Investigado / indiciado' || CHR(10) || 'Imputado / acusado' || CHR(10) || 'Condenado' || CHR(10) || 'Demandado' || CHR(10) || 'Demandante' || CHR(10) || 'Denunciante' || CHR(10) || 'Víctima' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE),
    ('p_delito', 'Delito o asunto', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE),
    ('p_despacho', 'Despacho', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE),
    ('p_fecha', 'Fecha del proceso', 'FECHA', 6, NULL, FALSE, NULL, NULL, FALSE),
    ('p_etapa', 'Etapa', 'TEXTO', 7, NULL, FALSE, NULL, NULL, FALSE),
    ('p_estado', 'Estado', 'LISTA', 8, 'Activo' || CHR(10) || 'Pena vigente' || CHR(10) || 'Terminado' || CHR(10) || 'Archivado' || CHR(10) || 'Inactivo', FALSE, NULL, NULL, FALSE),
    ('p_motivo', 'Motivo de terminación', 'LISTA', 9, '—' || CHR(10) || 'Archivo' || CHR(10) || 'Preclusión' || CHR(10) || 'Prescripción' || CHR(10) || 'Desistimiento' || CHR(10) || 'Conciliación' || CHR(10) || 'Principio de oportunidad' || CHR(10) || 'Sentencia absolutoria' || CHR(10) || 'Sentencia condenatoria' || CHR(10) || 'Fallo a favor' || CHR(10) || 'Fallo en contra' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE),
    ('p_justificacion', 'Justificación', 'TEXTO', 10, NULL, FALSE, NULL, NULL, FALSE),
    ('p_relacion', 'Relación con el cargo', 'LISTA', 11, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('p_calificacion', 'Calificación', 'LISTA', 12, '—' || CHR(10) || 'LEG-05' || CHR(10) || 'LEG-11' || CHR(10) || 'LEG-19' || CHR(10) || 'LEG-20' || CHR(10) || 'LEG-21' || CHR(10) || 'LEG-22' || CHR(10) || 'LEG-23', FALSE, NULL, '—', FALSE),
    ('p_obs', 'Observaciones', 'TEXTO', 13, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'procesos' AND s.activo = TRUE;

-- 12. Procesos judiciales – Bases privadas
UPDATE formulario_secciones SET nombre = '12. Procesos judiciales – Bases privadas', tipo = 'TABLA', orden = 17, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'procesos_privados';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '12. Procesos judiciales – Bases privadas', 'TABLA', 17, TRUE, 'procesos_privados', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'procesos_privados');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pp_numero', 'Proceso N°', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE),
    ('pp_calidad', 'Calidad del evaluado', 'LISTA', 1, 'Investigado / indiciado' || CHR(10) || 'Imputado / acusado' || CHR(10) || 'Condenado' || CHR(10) || 'Demandado' || CHR(10) || 'Demandante' || CHR(10) || 'Denunciante' || CHR(10) || 'Víctima' || CHR(10) || 'Otro', FALSE, NULL, NULL, FALSE),
    ('pp_delito', 'Delito o asunto', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE),
    ('pp_despacho', 'Despacho', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE),
    ('pp_fecha', 'Fecha del proceso', 'FECHA', 4, NULL, FALSE, NULL, NULL, FALSE),
    ('pp_etapa', 'Etapa', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE),
    ('pp_estado', 'Estado', 'LISTA', 6, 'Activo' || CHR(10) || 'Pena vigente' || CHR(10) || 'Terminado' || CHR(10) || 'Archivado' || CHR(10) || 'Inactivo', FALSE, NULL, NULL, FALSE),
    ('pp_relacion', 'Relación con el cargo', 'LISTA', 7, 'Sí' || CHR(10) || 'No', FALSE, NULL, NULL, FALSE),
    ('pp_calificacion', 'Calificación', 'LISTA', 8, '—' || CHR(10) || 'LEG-05' || CHR(10) || 'LEG-11' || CHR(10) || 'LEG-19' || CHR(10) || 'LEG-20' || CHR(10) || 'LEG-21' || CHR(10) || 'LEG-22' || CHR(10) || 'LEG-23', FALSE, NULL, '—', FALSE),
    ('pp_obs', 'Observaciones', 'TEXTO', 9, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'procesos_privados' AND s.activo = TRUE;

-- 11 y 12. Notas de procesos
UPDATE formulario_secciones SET nombre = '11 y 12. Notas de procesos', tipo = 'CAMPOS', orden = 18, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'notas_procesos';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '11 y 12. Notas de procesos', 'CAMPOS', 18, TRUE, 'notas_procesos', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'notas_procesos');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('procesos_nota', 'Nota de procesos (Rama Judicial)', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE),
    ('procesos_priv_nota', 'Nota de procesos (bases privadas)', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'notas_procesos' AND s.activo = TRUE;

-- 13. Concepto del analista
UPDATE formulario_secciones SET nombre = '13. Concepto del analista', tipo = 'CAMPOS', orden = 19, activo = TRUE, origen_fijo = NULL, columnas_ocultas = NULL, calculada = FALSE WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES') AND clave = 'concepto_analista';
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, version_creacion, calculada) SELECT t.id_tipo_progreso, '13. Concepto del analista', 'CAMPOS', 19, TRUE, 'concepto_analista', t.version_plantilla, FALSE FROM tipos_progreso t WHERE t.grupo_calificacion = 'ANTECEDENTES' AND NOT EXISTS (SELECT 1 FROM formulario_secciones s WHERE s.id_tipo_progreso = t.id_tipo_progreso AND s.clave = 'concepto_analista');
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('concepto', 'Concepto del analista', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE),
    ('analista_fecha', 'Fecha', 'FECHA', 2, NULL, FALSE, NULL, NULL, FALSE)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio)
WHERE t.grupo_calificacion = 'ANTECEDENTES' AND s.clave = 'concepto_analista' AND s.activo = TRUE;
