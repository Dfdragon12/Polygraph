-- Reemplaza el catálogo de prueba (V99) por la taxonomía real del negocio:
-- Clasificaciones -> Procesos -> Subprocesos (tipos_progreso), tal como la definió el usuario.
--
-- IMPORTANTE sobre seguridad de datos: los procesos id 1,2,3 ("Polígrafo Pre-empleo/Rutina/
-- Específico") tienen servicios reales asociados (servicios 1-5, candidato de prueba incluido)
-- y tipos_progreso id 1,2,3,5 ("Poligrafia","Antecedentes","Visita","Referencia") tienen filas
-- reales en servicio_subprocesos. Por eso esas filas se RENOMBRAN/RECLASIFICAN en vez de
-- borrarse. Todo lo demás (procesos 4-17, tipo_progreso 4, y sus procesos_tipos_progreso) no
-- tiene ninguna fila real asociada — se verificó contra la base de datos antes de escribir esta
-- migración — así que se borra y se reconstruye limpio.

-- ── 1. Clasificaciones ──────────────────────────────────────────────────────
UPDATE clasificaciones_proceso SET nombre = 'SERVICIOS',
    descripcion = 'Validación laboral, académica, personal, financiera y visitas domiciliarias'
    WHERE codigo = 'A';
UPDATE clasificaciones_proceso SET nombre = 'PRUEBAS DE CONFIABILIDAD',
    descripcion = 'Pruebas de confiabilidad remotas' WHERE codigo = 'B';
UPDATE clasificaciones_proceso SET nombre = 'ESTUDIOS DE SEGURIDAD',
    descripcion = 'Estudios integrales que combinan varios servicios' WHERE codigo = 'C';

-- Los 2 procesos que colgaban de 'Otros' (D) no tienen servicios reales — se eliminan junto
-- con sus procesos_tipos_progreso antes de poder borrar la clasificación.
DELETE FROM procesos_tipos_progreso WHERE id_proceso NOT IN (1, 2, 3);
DELETE FROM procesos WHERE id_proceso NOT IN (1, 2, 3);
DELETE FROM clasificaciones_proceso WHERE codigo = 'D';

INSERT INTO clasificaciones_proceso (codigo, nombre, descripcion) VALUES
    ('E', 'POLIGRAFIA', 'Pruebas de polígrafo — Bogotá/Nacional'),
    ('F', 'VERIFEYE',   'Servicio Verifeye');

-- El único tipo_progreso sin ninguna fila real en servicio_subprocesos era el id 4
-- ("Poligrafia Oea"); se borra junto con los demás no usados.
DELETE FROM tipos_progreso WHERE id_tipo_progreso NOT IN (1, 2, 3, 5);

-- ── 2. Procesos 1-3: se conservan (FK real desde servicios), solo se reclasifican ──
-- nombre_proceso tiene UNIQUE constraint, y más abajo se crean "Pre-empleo" (bajo Pruebas de
-- Confiabilidad) y "Rutina" (bajo Verifeye) — por eso estos 3 NO se renombran (se quedarían sin
-- nombre único), solo cambian de clasificación. Quedan como "Polígrafo Pre-empleo/Rutina/
-- Específico", ahora bajo POLIGRAFIA.
UPDATE procesos SET
    descripcion = 'Bogotá/Nacional',
    id_clasificacion = (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'E')
    WHERE id_proceso IN (1, 2, 3);
-- Sus procesos_tipos_progreso (Poligrafia/Antecedentes/Visita/Referencia) quedan tal cual,
-- no se tocan.

-- ── 3. Subprocesos nuevos (tipos_progreso) ──────────────────────────────────
-- "orden" es GLOBAL (no por proceso — ver nota al final), se asigna aproximando el orden
-- típico: antecedentes/validaciones primero, campo (visitas/polígrafo) después, cierre
-- financiero al final.
INSERT INTO tipos_progreso (nombre_progreso, descripcion, orden, activo) VALUES
    ('Validación Laboral',              'Validación de experiencia laboral (cobertura 4 años)', 10, true),
    ('Validación Académica',            'Validación del último título académico',               11, true),
    ('Validación Personal',             'Validación de referencias personales',                 12, true),
    ('Análisis Financiero',             NULL,                                                    40, true),
    ('Pruebas Psicotécnicas',           NULL,                                                    15, true),
    ('Visita Presencial',               NULL,                                                    30, true),
    ('Visita OEA',                      NULL,                                                    31, true),
    ('Visita Rutina',                   NULL,                                                    32, true),
    ('Visita Virtual',                  NULL,                                                    33, true),
    ('Visita Domiciliaria',             NULL,                                                    34, true),
    ('Visita Domiciliaria Virtual',     NULL,                                                    35, true),
    ('Visita Domiciliaria Financiera',  NULL,                                                    36, true),
    ('Validación Documental',           'Incluye validación laboral, académica y personal',     20, true),
    ('Validación Documental (1)',       'Cobertura reducida — variante usada en Estudio Quick', 20, true),
    ('Antecedentes (2 años)',           'Variante de cobertura reducida — Estudio Quick',        10, true),
    ('Revisión Patrimonial',            NULL,                                                    41, true),
    ('Remotas',                         NULL,                                                    1,  true),
    ('Persona Jurídica',                NULL,                                                    1,  true),
    ('Rutina',                          NULL,                                                    1,  true);

-- ── 4. Procesos nuevos ───────────────────────────────────────────────────────
INSERT INTO procesos (nombre_proceso, descripcion, id_clasificacion, activo) VALUES
    ('Antecedentes',           NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'A'), true),
    ('Validación documental',  NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'A'), true),
    ('Análisis Financiero',    NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'A'), true),
    ('Pruebas Psicotécnicas',  NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'A'), true),
    ('Visitas domiciliarias',  'Ciudades / municipios principales / municipios secundarios',
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'A'), true),
    ('Confiabilidad Pre-empleo', NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'B'), true),
    ('Verifeye Rutina',        NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'F'), true),
    ('Básico',                 NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('Básico Virtual',         NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('Avanzado',               'Bogotá/Nacional',
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('Quick',                  NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('OEA',                    NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('Poligrafía + Visita Domiciliaria', NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('Poligrafía + Antecedentes', NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true),
    ('Persona Jurídica',       NULL,
        (SELECT id_clasificacion FROM clasificaciones_proceso WHERE codigo = 'C'), true);

-- ── 5. Vincular cada proceso con sus subprocesos ────────────────────────────
-- Procesos "simples" (sin desglose en la lista original) reciben un subproceso del mismo
-- nombre: sin al menos un tipo_progreso vinculado, crear un servicio de ese proceso no genera
-- NINGUNA fila en servicio_subprocesos y no hay nada que asignar a un analista/operador.
INSERT INTO procesos_tipos_progreso (id_proceso, id_tipo_progreso, orden_en_proceso)
SELECT p.id_proceso, t.id_tipo_progreso, x.orden FROM (VALUES
    ('Antecedentes',                       'Antecedentes',                      1),

    ('Validación documental',              'Validación Laboral',                1),
    ('Validación documental',              'Validación Académica',              2),
    ('Validación documental',              'Validación Personal',               3),

    ('Análisis Financiero',                'Análisis Financiero',               1),
    ('Pruebas Psicotécnicas',              'Pruebas Psicotécnicas',             1),

    ('Visitas domiciliarias',              'Visita Presencial',                 1),
    ('Visitas domiciliarias',              'Visita OEA',                        2),
    ('Visitas domiciliarias',              'Visita Rutina',                     3),
    ('Visitas domiciliarias',              'Visita Virtual',                    4),

    ('Confiabilidad Pre-empleo',           'Remotas',                           1),

    ('Verifeye Rutina',                    'Rutina',                            1),

    ('Básico',                             'Antecedentes',                      1),
    ('Básico',                             'Visita Domiciliaria',               2),
    ('Básico',                             'Validación Documental',             3),
    ('Básico',                             'Análisis Financiero',               4),

    ('Básico Virtual',                     'Antecedentes',                      1),
    ('Básico Virtual',                     'Visita Domiciliaria Virtual',       2),
    ('Básico Virtual',                     'Validación Documental',             3),
    ('Básico Virtual',                     'Análisis Financiero',               4),

    ('Avanzado',                           'Antecedentes',                      1),
    ('Avanzado',                           'Poligrafia',                        2),
    ('Avanzado',                           'Visita Domiciliaria',               3),
    ('Avanzado',                           'Validación Documental',             4),
    ('Avanzado',                           'Análisis Financiero',               5),

    ('Quick',                              'Antecedentes (2 años)',             1),
    ('Quick',                              'Validación Documental (1)',         2),

    ('OEA',                                'Antecedentes',                      1),
    ('OEA',                                'Visita Domiciliaria Financiera',    2),
    ('OEA',                                'Revisión Patrimonial',              3),
    ('OEA',                                'Análisis Financiero',               4),

    ('Poligrafía + Visita Domiciliaria',   'Poligrafia',                        1),
    ('Poligrafía + Visita Domiciliaria',   'Visita Domiciliaria',               2),

    ('Poligrafía + Antecedentes',          'Poligrafia',                        1),
    ('Poligrafía + Antecedentes',          'Antecedentes',                      2),

    ('Persona Jurídica',                   'Persona Jurídica',                  1)
) AS x(proceso, subproceso, orden)
JOIN procesos p       ON p.nombre_proceso = x.proceso AND p.id_proceso NOT IN (1, 2, 3)
JOIN tipos_progreso t ON t.nombre_progreso = x.subproceso;

-- NOTA para el equipo: el orden de despliegue de los subprocesos dentro de un proceso hoy lo
-- decide tipos_progreso.orden, que es GLOBAL (compartido por todos los procesos que reutilizan
-- ese subproceso) — la columna procesos_tipos_progreso.orden_en_proceso se guarda pero no se usa
-- para ordenar en ninguna consulta actual. Por eso subprocesos reutilizados (p.ej. "Antecedentes"
-- o "Análisis Financiero") van a aparecer siempre en la misma posición relativa en todos los
-- procesos que los usan, aunque en la lista original el orden varíe un poco entre combos. Si se
-- necesita orden independiente por proceso, es un cambio de código aparte (usar
-- orden_en_proceso en vez de tipos_progreso.orden al listar pasos).
