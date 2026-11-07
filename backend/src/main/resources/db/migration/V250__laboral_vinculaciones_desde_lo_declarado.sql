-- LB-03 (2026-11-04): Verificación laboral. Las vinculaciones nacen de la experiencia que declaró el evaluado y cada celda se compara con lo declarado. Columnas nuevas
-- de la tabla «6. Vinculaciones laborales» (ninguna se imprime; las claves existentes no cambian):
--   v_id_exp            id de la experiencia de la hoja de vida de la que nació la vinculación (para saber si el evaluado cambió su declaración)
--   v_decl_ciudad / v_decl_contrato / v_decl_salario / v_decl_llamados / v_decl_telefono   copia de lo declarado al crear la vinculación
--   v_no_declarada      «Sí» = vinculación hallada por el analista (sábana pensional, etc.) que el evaluado no declaró
--   v_mas_30            ¿la vinculación no declarada dura más de 30 días? (base del LAB-10)
--   v_autorizacion      el evaluado autorizó contactar a la empresa actual
--   v_soporte           certificado, sábana pensional o constancia de la llamada
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, opciones, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, v.clave, v.nombre, v.tipo_dato, v.orden, v.opciones, FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
CROSS JOIN (VALUES
    ('v_id_exp',          'Experiencia de origen (no se imprime)',                 'TEXTO',   33, NULL),
    ('v_decl_ciudad',     'Ciudad declarada (no se imprime)',                      'TEXTO',   34, NULL),
    ('v_decl_contrato',   'Tipo de contrato declarado (no se imprime)',            'TEXTO',   35, NULL),
    ('v_decl_salario',    'Salario declarado (no se imprime)',                     'TEXTO',   36, NULL),
    ('v_decl_llamados',   'Llamados de atención declarados (no se imprime)',       'TEXTO',   37, NULL),
    ('v_decl_telefono',   'Teléfono declarado (no se imprime)',                    'TEXTO',   38, NULL),
    ('v_no_declarada',    'No declarada en la hoja de vida (no se imprime)',       'TEXTO',   39, NULL),
    ('v_mas_30',          '¿Dura más de 30 días? (no se imprime)',                 'LISTA',   40, 'Sí' || CHR(10) || 'No'),
    ('v_autorizacion',    'Autorización del evaluado para contactar (no se imprime)', 'LISTA', 41, 'Sí' || CHR(10) || 'No'),
    ('v_soporte',         'Soporte de la vinculación (no se imprime)',             'ARCHIVO', 42, NULL)
) AS v (clave, nombre, tipo_dato, orden, opciones)
WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'vinculaciones'
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas x WHERE x.id_seccion = s.id_seccion AND x.clave = v.clave);
