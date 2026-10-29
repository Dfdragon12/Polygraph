-- Plantilla de "Análisis financiero" (antecedentes financieros) DESDE CERO (2026-10-22, pedido del usuario): crea el
-- subservicio si no existe, borra todo lo que tuviera (secciones, campos, filas diligenciadas, hallazgos por opción y el
-- Word cargado con su historial) y la crea de nuevo contando desde la versión 1, a partir de docs/Analisis Financiero
-- (diccionario de marcadores, formato Word y motor_fin.py). UN solo formato para síntesis, verificación y OEA.
-- Se califica con su propio motor (MotorFinanciero), no con el genérico: depende del tipo de estudio (OEA lo dice el nombre
-- del proceso, campo es_oea), de rangos de meses de mora y de la banda del score. Los hallazgos por opción de fin_ejecutivo
-- (FIN-05), c_*_res (FIN-08) y oea_inconsistencia (FIN-07) solo documentan la regla y mantienen la calificación activa al
-- guardar desde el constructor. FIN-01, FIN-02 y FIN-06 acumulan por obligación (OEA); los demás cuentan una vez.
-- Las secciones solo OEA son opcionales: en un estudio que no es OEA el analista las quita. El Word hay que cargarlo de
-- nuevo en Admin → Catálogo → Plantilla (esta migración borra el anterior).
-- (No escribir aquí marcadores con signo de pesos y llave: Flyway los toma como variables.)

-- El subservicio (si el admin ya lo creó con este nombre, no se toca).
INSERT INTO tipos_progreso (nombre_progreso, descripcion, orden, activo, rol_responsable, requiere_documento, version_plantilla)
SELECT 'Analisis Financiero', 'Verificación de antecedentes financieros del evaluado', (SELECT COALESCE(MAX(orden), 0) + 1 FROM tipos_progreso), TRUE, 'ANALISTA_INTERNO', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero');
UPDATE tipos_progreso SET requiere_documento = TRUE WHERE nombre_progreso = 'Analisis Financiero';

-- Catálogo de hallazgos financieros (docs/Analisis Financiero/motor_fin.py).
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta)
SELECT 'GENERAL', v.codigo, v.descripcion, v.puntos, v.critico, v.por_proceso, FALSE, TRUE, (SELECT COALESCE(MAX(orden), 0) FROM codigos_calificacion) + v.n, v.alerta
FROM (VALUES
    ('FIN-01', 'Obligación en cartera castigada sin pagar, o con mora vigente superior a 180 días', 60, FALSE, TRUE, FALSE, 1),
    ('FIN-02', 'Obligación con mora vigente de 90 a 180 días', 45, FALSE, TRUE, FALSE, 2),
    ('FIN-03', 'Moras de 30 a 89 días repetidas: 3 o más meses con mora 30 o mora 60 en el último año', 25, FALSE, FALSE, FALSE, 3),
    ('FIN-04', 'Endeudamiento alto: la utilización del ingreso estimado frente a la cuota mensual supera el 70 %', 40, FALSE, FALSE, FALSE, 4),
    ('FIN-05', 'Proceso ejecutivo o embargo vigente', 50, FALSE, FALSE, FALSE, 5),
    ('FIN-06', 'Codeudor de una obligación en mora (estudios OEA, con el reporte detallado)', 20, FALSE, TRUE, FALSE, 6),
    ('FIN-07', 'Inconsistencia patrimonial: bienes declarados que no aparecen en los registros, bienes registrados que no declaró, o cambio patrimonial frente al estudio anterior que no se explica con sus ingresos, sin explicación razonable (estudios OEA)', 30, FALSE, FALSE, FALSE, 7),
    ('FIN-08', 'Ocultamiento financiero: negó u omitió deudas, moras, reportes o embargos que aparecen en centrales de riesgo (poligrafía, visita domiciliaria o formulario del evaluado)', 40, FALSE, FALSE, FALSE, 8),
    ('ALR-FIN-01', 'Score en la banda más baja de la central consultada sin ningún código FIN marcado: revisar el reporte antes de cerrar', 0, FALSE, FALSE, TRUE, 9)
) AS v (codigo, descripcion, puntos, critico, por_proceso, alerta, n)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- Borrar lo que existe, en orden, de lo que depende hacia la sección (no se confía en el ON DELETE CASCADE).
DELETE FROM formulario_datos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero')));
DELETE FROM formulario_filas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero'));
DELETE FROM formulario_columna_puntajes WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero')));
DELETE FROM formulario_columna_hallazgos WHERE id_columna IN (SELECT c.id_columna FROM formulario_columnas c WHERE c.id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero')));
DELETE FROM formulario_columnas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero'));
DELETE FROM formulario_filas_sugeridas WHERE id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s WHERE s.id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero'));
DELETE FROM formulario_secciones WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero');
DELETE FROM formulario_plantilla_word_historial WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero');
DELETE FROM formulario_plantilla_config WHERE id_tipo_progreso IN (SELECT t.id_tipo_progreso FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero');
-- Contar desde la versión 1.
UPDATE tipos_progreso t SET version_plantilla = 1, grupo_calificacion = 'HALLAZGOS' WHERE t.nombre_progreso = 'Analisis Financiero';

-- 1. Datos del evaluado
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '1. Datos del evaluado', 'CAMPOS', 0, TRUE, 'datos_evaluado', NULL, NULL, t.version_plantilla, FALSE, 'Datos del evaluado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('estudio_numero', 'N° de estudio', 'TEXTO', 0, NULL, FALSE, 'NUMERO_ESTUDIO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fecha_emision', 'Fecha de emisión', 'TEXTO', 1, NULL, FALSE, 'FECHA_EMISION', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('ev_cliente', 'Cliente', 'TEXTO', 2, NULL, FALSE, 'CLIENTE', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('ev_cargo', 'Cargo', 'TEXTO', 3, NULL, FALSE, 'CARGO', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('ev_lugar_exp', 'Lugar de expedición', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('ev_criticidad', 'Criticidad del cargo', 'LISTA', 5, 'Estándar' || CHR(10) || 'Sensible' || CHR(10) || 'Crítico', FALSE, NULL, NULL, TRUE, 'CRITICIDAD', NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('es_oea', '¿Estudio OEA? (lo dice el tipo de estudio; no se imprime)', 'TEXTO', 6, NULL, FALSE, 'ES_OEA', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'datos_evaluado';

-- 2. Resultado del servicio (lo calcula el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '2. Resultado del servicio (lo calcula el sistema)', 'CAMPOS', 1, TRUE, 'resultado_servicio', NULL, NULL, t.version_plantilla, FALSE, 'Resultado de la calificación', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('srv_puntaje', 'Puntaje del servicio', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTAJE', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('srv_formula', 'Fórmula del puntaje', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FORMULA', 'CON_CODIGO', NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('sem_bajo', 'Semáforo – BAJO', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_BAJO', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('sem_medio', 'Semáforo – MEDIO', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_MEDIO', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('sem_alto', 'Semáforo – ALTO', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'SEMAFORO_ALTO', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('srv_alertas_nota', 'Instructivo', 'TEXTO', 5, NULL, FALSE, NULL, 'Las alertas (ALR-FIN) no suman puntos; requieren revisión del analista antes del informe final.', FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'resultado_servicio';

-- 3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '3. Hallazgos con impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 2, TRUE, 'hallazgos', NULL, NULL, t.version_plantilla, TRUE, 'Resultado de la calificación', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('h_ref', 'Ref.', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, 'REF', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('h_descripcion', 'Hallazgo', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, 'DESCRIPCION', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('h_fuente', 'Fuente', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, 'FUENTE', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('h_detalle', 'Detalle', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, 'DETALLE', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('h_puntos', 'Puntos', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, 'PUNTOS', NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'hallazgos';

-- 4. Hallazgos sin impacto en el nivel de riesgo (lo arma el sistema)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto en el nivel de riesgo (lo arma el sistema)', 'TABLA', 3, TRUE, 'informativos', NULL, NULL, t.version_plantilla, TRUE, 'Resultado de la calificación', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('i_situacion', 'Situación', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('i_detalle', 'Detalle', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('i_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'informativos';

-- 4. Hallazgos sin impacto: los del analista (el sistema agrega solo la fila del score)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '4. Hallazgos sin impacto: los del analista (el sistema agrega solo la fila del score)', 'TABLA', 4, TRUE, 'informativos_analista', NULL, NULL, t.version_plantilla, FALSE, 'Resultado de la calificación', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('ia_situacion', 'Situación', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('ia_detalle', 'Detalle', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('ia_tratamiento', 'Tratamiento', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'informativos_analista';

-- 5. Concepto endeudamiento y comportamiento de pago (consulta de síntesis; valores en miles)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '5. Concepto endeudamiento y comportamiento de pago (consulta de síntesis; valores en miles)', 'CAMPOS', 5, TRUE, 'consulta_sintesis', NULL, NULL, t.version_plantilla, FALSE, 'Consulta de síntesis', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('cr_fecha', 'Fecha de consulta', 'FECHA', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_mayor_mora', 'Mayor mora del último año', 'LISTA', 1, 'Normal' || CHR(10) || 'Mora 30' || CHR(10) || 'Mora 60' || CHR(10) || 'Mora 90 o más' || CHR(10) || 'No se recibió / no existe', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_fin_vig', 'Sector financiero – Créditos vigentes', 'NUMERO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_fin_cer', 'Sector financiero – Créditos cerrados', 'NUMERO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_fin_pri', 'Sector financiero – Total como principal', 'NUMERO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_fin_cod', 'Sector financiero – Total como codeudor y otros', 'NUMERO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_fin_cuota', 'Sector financiero – Valor cuota (miles)', 'NUMERO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_fin_mora', 'Sector financiero – Saldo en mora (miles)', 'NUMERO', 7, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_real_vig', 'Sector real – Créditos vigentes', 'NUMERO', 8, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_real_cer', 'Sector real – Créditos cerrados', 'NUMERO', 9, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_real_pri', 'Sector real – Total como principal', 'NUMERO', 10, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_real_cod', 'Sector real – Total como codeudor y otros', 'NUMERO', 11, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_real_cuota', 'Sector real – Valor cuota (miles)', 'NUMERO', 12, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_real_mora', 'Sector real – Saldo en mora (miles)', 'NUMERO', 13, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_coop_vig', 'Sector cooperativo – Créditos vigentes', 'NUMERO', 14, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_coop_cer', 'Sector cooperativo – Créditos cerrados', 'NUMERO', 15, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_coop_pri', 'Sector cooperativo – Total como principal', 'NUMERO', 16, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_coop_cod', 'Sector cooperativo – Total como codeudor y otros', 'NUMERO', 17, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_coop_cuota', 'Sector cooperativo – Valor cuota (miles)', 'NUMERO', 18, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_coop_mora', 'Sector cooperativo – Saldo en mora (miles)', 'NUMERO', 19, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tel_vig', 'Sector telcos – Créditos vigentes', 'NUMERO', 20, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tel_cer', 'Sector telcos – Créditos cerrados', 'NUMERO', 21, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tel_pri', 'Sector telcos – Total como principal', 'NUMERO', 22, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tel_cod', 'Sector telcos – Total como codeudor y otros', 'NUMERO', 23, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tel_cuota', 'Sector telcos – Valor cuota (miles)', 'NUMERO', 24, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tel_mora', 'Sector telcos – Saldo en mora (miles)', 'NUMERO', 25, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tot_vig', 'TOTAL – Créditos vigentes', 'TEXTO', 26, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tot_cer', 'TOTAL – Créditos cerrados', 'TEXTO', 27, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tot_pri', 'TOTAL – Total como principal', 'TEXTO', 28, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tot_cod', 'TOTAL – Total como codeudor y otros', 'TEXTO', 29, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tot_cuota', 'TOTAL – Valor cuota (miles)', 'TEXTO', 30, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('s_tot_mora', 'TOTAL – Saldo en mora (miles)', 'TEXTO', 31, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fin_meses_mora_90', 'Meses seguidos con mora 90 o más (cuente en la barra de comportamiento de los últimos 12 meses, no se imprime)', 'NUMERO', 32, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fin_meses_mora_30_60', 'Meses con mora 30 o 60 en el último año (cuente en la barra de comportamiento, no se imprime)', 'NUMERO', 33, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fin_comportamiento', 'Endeudamiento y comportamiento de pago (último año)', 'TEXTO', 34, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cp_ref', 'Ref. del comportamiento (la arma el sistema)', 'TEXTO', 35, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_salvedad', 'Salvedad de alcance (la arma el sistema)', 'TEXTO', 36, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'consulta_sintesis';

-- 6. Análisis de ingresos
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '6. Análisis de ingresos', 'CAMPOS', 6, TRUE, 'analisis_ingresos', NULL, NULL, t.version_plantilla, FALSE, 'Consulta de síntesis', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('fin_ingreso', 'Ingreso estimado (en miles, o "No reportado")', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_cuotas', 'Valor cuota mensual (la calcula el sistema)', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fin_endeudamiento', '% de utilización del ingreso estimado vs. cuota mensual', 'NUMERO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fin_endeudamiento_ref', 'Ref. del análisis de ingresos (la arma el sistema)', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'analisis_ingresos';

-- 7. Consolidado financiero
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '7. Consolidado financiero', 'CAMPOS', 7, TRUE, 'consolidado', NULL, NULL, t.version_plantilla, FALSE, 'Consolidado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('c_ing_cr', 'Ingresos – Centrales de riesgo', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_ing_poli', 'Ingresos – Poligrafía', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_ing_visita', 'Ingresos – Visita domiciliaria', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_ing_res', 'Ingresos – Resultado', 'LISTA', 3, 'Coincide' || CHR(10) || 'No coincide – lo explicó' || CHR(10) || 'No coincide – omitió u ocultó (FIN-08)' || CHR(10) || 'No incluido en el estudio', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Consolidado financiero', 'Omitió u ocultó ingresos', CAST(NULL AS INTEGER)),
    ('c_deu_cr', 'Deudas y cuotas – Centrales de riesgo', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_deu_poli', 'Deudas y cuotas – Poligrafía', 'TEXTO', 5, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_deu_visita', 'Deudas y cuotas – Visita domiciliaria', 'TEXTO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_deu_res', 'Deudas y cuotas – Resultado', 'LISTA', 7, 'Coincide' || CHR(10) || 'No coincide – lo explicó' || CHR(10) || 'No coincide – omitió u ocultó (FIN-08)' || CHR(10) || 'No incluido en el estudio', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Consolidado financiero', 'Omitió u ocultó deudas y cuotas', CAST(NULL AS INTEGER)),
    ('c_mor_cr', 'Moras y reportes – Centrales de riesgo', 'TEXTO', 8, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_mor_poli', 'Moras y reportes – Poligrafía', 'TEXTO', 9, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_mor_visita', 'Moras y reportes – Visita domiciliaria', 'TEXTO', 10, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_mor_res', 'Moras y reportes – Resultado', 'LISTA', 11, 'Coincide' || CHR(10) || 'No coincide – lo explicó' || CHR(10) || 'No coincide – omitió u ocultó (FIN-08)' || CHR(10) || 'No incluido en el estudio', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Consolidado financiero', 'Omitió u ocultó moras y reportes', CAST(NULL AS INTEGER)),
    ('c_bie_cr', 'Bienes – Centrales de riesgo', 'TEXTO', 12, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_bie_poli', 'Bienes – Poligrafía', 'TEXTO', 13, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_bie_visita', 'Bienes – Visita domiciliaria', 'TEXTO', 14, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 120),
    ('c_bie_res', 'Bienes – Resultado', 'LISTA', 15, 'Coincide' || CHR(10) || 'No coincide – lo explicó' || CHR(10) || 'No coincide – omitió u ocultó (FIN-08)' || CHR(10) || 'No incluido en el estudio', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 'Consolidado financiero', 'Omitió u ocultó bienes', CAST(NULL AS INTEGER)),
    ('c_observaciones', 'Observaciones del consolidado', 'TEXTO', 16, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 300)
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'consolidado';

-- Proceso ejecutivo o embargo y ocultamiento (no se imprime)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Proceso ejecutivo o embargo y ocultamiento (no se imprime)', 'CAMPOS', 8, TRUE, 'alertas_internas', NULL, NULL, t.version_plantilla, FALSE, 'Consolidado', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('fin_ejecutivo', 'Proceso ejecutivo o embargo vigente', 'LISTA', 0, 'Sí' || CHR(10) || 'No', FALSE, NULL, 'No', FALSE, NULL, NULL, NULL, NULL, 'Antecedentes judiciales', '{fin_ejecutivo_detalle}', CAST(NULL AS INTEGER)),
    ('fin_ejecutivo_detalle', 'Detalle del ejecutivo o embargo', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('fin_ocultamiento_detalle', 'Detalle del ocultamiento (FIN-08)', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'alertas_internas';

-- 8. Obligaciones con reporte negativo (reporte detallado; solo OEA)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '8. Obligaciones con reporte negativo (reporte detallado; solo OEA)', 'TABLA', 9, TRUE, 'obligaciones_negativas', NULL, NULL, t.version_plantilla, FALSE, 'Solo estudios OEA', TRUE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('o_entidad', 'Entidad', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_tipo', 'Tipo de cuenta (sigla)', 'LISTA', 1, 'CAB' || CHR(10) || 'CAC' || CHR(10) || 'CAU' || CHR(10) || 'CDC' || CHR(10) || 'CTC' || CHR(10) || 'CON' || CHR(10) || 'CVE' || CHR(10) || 'LBZ' || CHR(10) || 'TDC' || CHR(10) || 'MCR' || CHR(10) || 'CSP' || CHR(10) || 'CVV' || CHR(10) || 'CVN', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_calidad', 'Calidad', 'LISTA', 2, 'Principal' || CHR(10) || 'Codeudor y otros', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_estado', 'Estado', 'LISTA', 3, 'En mora' || CHR(10) || 'Castigada' || CHR(10) || 'Cartera de difícil cobro' || CHR(10) || 'Saldada castigada', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_dias', 'Días de mora (no se imprime)', 'NUMERO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_dias_mora', 'Mora máxima (la calcula el sistema)', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_valor', 'Valor en mora, en miles (no se imprime)', 'NUMERO', 6, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_valor_mora', 'Valor en mora* (la calcula el sistema)', 'TEXTO', 7, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('o_ref', 'Ref. (la arma el sistema)', 'TEXTO', 8, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'obligaciones_negativas';

-- 9. Declaración de renta y patrimonio: bienes (solo OEA)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Declaración de renta y patrimonio: bienes (solo OEA)', 'TABLA', 10, TRUE, 'patrimonios', NULL, NULL, t.version_plantilla, FALSE, 'Solo estudios OEA', TRUE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('pt_tipo', 'Tipos de mueble o inmueble', 'TEXTO', 0, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('pt_ubicacion', 'Ubicación / Marca', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('pt_matricula', 'Matrícula o placa', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('pt_estado', 'Frente al estudio anterior (no se imprime)', 'LISTA', 3, 'Primer estudio' || CHR(10) || 'Sin cambio' || CHR(10) || 'Nuevo' || CHR(10) || 'Ya no aparece' || CHR(10) || 'No registra', FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('pt_explicacion', 'Explicación (obligatoria si es Nuevo o Ya no aparece; no se imprime)', 'TEXTO', 4, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('pt_novedad', 'Frente al estudio anterior (la arma el sistema)', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'patrimonios';

-- 9. Declaración de renta y patrimonio: datos generales (solo OEA)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, '9. Declaración de renta y patrimonio: datos generales (solo OEA)', 'CAMPOS', 11, TRUE, 'oea_datos', NULL, NULL, t.version_plantilla, FALSE, 'Solo estudios OEA', TRUE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('oea_anterior', 'Estudio OEA anterior (N° y fecha)', 'TEXTO', 0, NULL, FALSE, NULL, 'Primer estudio', FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('oea_obs', 'Observaciones del patrimonio (fuentes consultadas y corroboración en la visita)', 'TEXTO', 1, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('oea_inconsistencia', 'Inconsistencia patrimonial (no se imprime)', 'LISTA', 2, 'Sí' || CHR(10) || 'No', FALSE, NULL, 'No', FALSE, NULL, NULL, NULL, NULL, 'Declaración de renta y patrimonio', '{oea_inconsistencia_detalle}', CAST(NULL AS INTEGER)),
    ('oea_inconsistencia_detalle', 'Detalle de la inconsistencia (no se imprime)', 'TEXTO', 3, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'oea_datos';

-- Secciones 8 y 9 (lo arma el sistema; solo OEA)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Secciones 8 y 9 (lo arma el sistema; solo OEA)', 'TABLA', 12, TRUE, 'oea', NULL, NULL, t.version_plantilla, TRUE, 'Solo estudios OEA', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('oea_negativas_nota', 'Nota de la sección 8', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('oea_estudio_anterior', 'Estudio anterior', 'TEXTO', 1, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('oea_observaciones', 'Observaciones del patrimonio', 'TEXTO', 2, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'oea';

-- Concepto final del estudio financiero
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Concepto final del estudio financiero', 'CAMPOS', 13, TRUE, 'concepto_analista', NULL, NULL, t.version_plantilla, FALSE, 'Cierre', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('sec_concepto', 'Número de la sección del concepto (10 en OEA, 8 en los demás)', 'TEXTO', 0, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('concepto', 'Concepto final del estudio financiero', 'TEXTO', 1, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_score', 'Indicador de pago (score)', 'TEXTO', 2, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_banda_1', 'Banda 1 del indicador de pago', 'TEXTO', 3, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_banda_2', 'Banda 2 del indicador de pago', 'TEXTO', 4, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_banda_3', 'Banda 3 del indicador de pago', 'TEXTO', 5, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_banda_4', 'Banda 4 del indicador de pago', 'TEXTO', 6, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_m1', 'Marca de la banda 1', 'TEXTO', 7, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_m2', 'Marca de la banda 2', 'TEXTO', 8, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_m3', 'Marca de la banda 3', 'TEXTO', 9, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_m4', 'Marca de la banda 4', 'TEXTO', 10, NULL, TRUE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('cr_probabilidad', 'Probabilidad de cumplimiento de pago', 'TEXTO', 11, NULL, FALSE, NULL, NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('analista_nombre', 'Profesional encargado', 'TEXTO', 12, NULL, FALSE, 'PROFESIONAL', NULL, FALSE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER)),
    ('analista_fecha', 'Fecha', 'FECHA', 13, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'concepto_analista';

-- Anexo 2. Archivos de centrales de riesgo (síntesis; en OEA, también el reporte detallado)
INSERT INTO formulario_secciones (id_tipo_progreso, nombre, tipo, orden, activo, clave, origen_fijo, columnas_ocultas, version_creacion, calculada, grupo, opcional) SELECT t.id_tipo_progreso, 'Anexo 2. Archivos de centrales de riesgo (síntesis; en OEA, también el reporte detallado)', 'CAMPOS', 14, TRUE, 'adjuntos', NULL, NULL, t.version_plantilla, FALSE, 'Cierre', FALSE FROM tipos_progreso t WHERE t.nombre_progreso = 'Analisis Financiero';
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso_calificacion, calculo, referencia, texto_nota, fuente_hallazgo, detalle_hallazgo, longitud_maxima, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, v.calculado, v.campo_servicio, v.valor_defecto, v.obligatorio, v.uso, v.calculo, v.referencia, v.nota, v.fuente, v.detalle, v.maximo, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('cr_reporte_enlace', 'Archivos de centrales de riesgo', 'ARCHIVO', 0, NULL, FALSE, NULL, NULL, TRUE, NULL, NULL, NULL, NULL, NULL, NULL, CAST(NULL AS INTEGER))
) AS v (clave, nombre, tipo_dato, orden, opciones, calculado, campo_servicio, valor_defecto, obligatorio, uso, calculo, referencia, nota, fuente, detalle, maximo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND s.clave = 'adjuntos';

-- Hallazgo que genera una opción (la regla la aplica MotorFinanciero; aquí queda documentada y mantiene la calificación activa).
INSERT INTO formulario_columna_hallazgos (id_columna, opcion, codigo)
SELECT c.id_columna, v.opcion, v.codigo
FROM formulario_columnas c JOIN formulario_secciones s ON s.id_seccion = c.id_seccion
JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('c_ing_res', 'No coincide – omitió u ocultó (FIN-08)', 'FIN-08'),
    ('c_deu_res', 'No coincide – omitió u ocultó (FIN-08)', 'FIN-08'),
    ('c_mor_res', 'No coincide – omitió u ocultó (FIN-08)', 'FIN-08'),
    ('c_bie_res', 'No coincide – omitió u ocultó (FIN-08)', 'FIN-08'),
    ('fin_ejecutivo', 'Sí', 'FIN-05'),
    ('oea_inconsistencia', 'Sí', 'FIN-07')
) AS v (clave, opcion, codigo)
WHERE t.nombre_progreso = 'Analisis Financiero' AND c.clave = v.clave;
