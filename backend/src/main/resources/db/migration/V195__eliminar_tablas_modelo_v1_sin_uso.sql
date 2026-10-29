-- Elimina las tablas del modelo viejo de V1 que ya no usa el proyecto (2026-09-28).
--
-- Eran el formato "fijo" de cada informe (poligrafía, visita domiciliaria, verificaciones de
-- referencias/financieras...), reemplazado por el módulo de formularios universal (V156:
-- formularios, formulario_secciones, formulario_columnas, formulario_filas, formulario_datos).
-- Verificado antes de escribir esto: ninguna entidad JPA las mapea, ningún SQL nativo las usa
-- (salvo visitas_domiciliarias en CiudadRepository, que se quitó en el mismo cambio), ninguna
-- tabla en uso tiene FK hacia ellas y en la base de desarrollo todas tienen 0 filas.
--
-- referencias_academicas y verificaciones_academicas (también de V1) ya no existían en
-- desarrollo (se borraron a mano); van con IF EXISTS para que una instalación limpia quede igual.
--
-- Protección: si en ALGUNA base (ej. producción) una de estas tablas tiene filas, la migración
-- se detiene sin borrar nada — mejor revisar esos datos a mano que perderlos en un despliegue.

DO $$
DECLARE
    tablas TEXT[] := ARRAY[
        -- Poligrafía
        'informes_poligrafia', 'antecedentes_poligrafia', 'autorizacion_poligrafia',
        'consumo_alcohol_poligrafia', 'consumo_drogas_poligrafia', 'educacion_poligrafia',
        'experiencia_laboral_poligrafia', 'historia_familiar_poligrafia', 'historia_financiera_poligrafia',
        'historia_medica_poligrafia', 'preguntas_relevantes_poligrafia', 'vinculos_poligrafia',
        'tecnica_poligrafica', 'planes_infiltracion', 'preguntas_antecedentes_laborales',
        -- Visita domiciliaria
        'visitas_domiciliarias', 'academica_visita', 'familia_visita', 'vivienda_visita',
        'decadactilar_visita', 'deudas_candidato_visita', 'economia_candidato_visita',
        'egresos_familia_visita', 'ingresos_familia_visita', 'patrimonio_familiar_visita',
        'protocolo_seguridad_visita', 'registro_fotografico_visita', 'concepto_dinamica_familiar',
        'concepto_economico_visita', 'concepto_habitacional', 'estado_salud',
        -- Verificaciones
        'verificaciones_financieras', 'obligaciones_financieras', 'verificaciones_referencias',
        'referencias_laborales', 'referencias_personales', 'periodos_inactividad_laboral',
        'verificaciones_academicas', 'referencias_academicas',
        -- Otros
        'documentos', 'informes_analisis_final'
    ];
    t TEXT;
    filas BIGINT;
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        IF to_regclass(t) IS NOT NULL THEN
            EXECUTE format('SELECT count(*) FROM %I', t) INTO filas;
            IF filas > 0 THEN
                RAISE EXCEPTION 'V195: la tabla % tiene % fila(s); no se borra nada. Revisar esos datos antes de desplegar.', t, filas;
            END IF;
        END IF;
    END LOOP;

    -- CASCADE solo arrastra las FK entre estas mismas tablas (ninguna tabla en uso apunta a ellas).
    FOREACH t IN ARRAY tablas LOOP
        EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', t);
    END LOOP;
END $$;
