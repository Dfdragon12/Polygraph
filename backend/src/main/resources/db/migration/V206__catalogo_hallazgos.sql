-- Catálogo de hallazgos (2026-10-06, decisión del usuario): UN catálogo en la plataforma (Admin →
-- Calificación de riesgo → Catálogo de hallazgos) con puntos, tipo (puntos / crítico / alerta),
-- si acumula por fila, si es solo con condición (conducción) y a qué área aplica. El analista, el
-- poligrafista y el visitador eligen hallazgos de ESTE catálogo, filtrado por su área. En el
-- constructor de plantillas se marca qué campo aplica hallazgos y cuáles.
--
-- Reemplaza el módulo Diccionarios de V205 (el usuario no quiere manejar el diccionario en Excel):
-- se borran sus tablas. El catálogo (codigos_calificacion) se conserva con todo lo editado.

DROP TABLE diccionario_reglas;
DROP TABLE diccionario_listas;
DROP TABLE diccionario_campos;
DROP TABLE diccionario_secciones;
DROP TABLE diccionarios;
ALTER TABLE codigos_calificacion DROP COLUMN donde;

-- ── Catálogo: el código es único en todo el catálogo (antes era por grupo).
ALTER TABLE codigos_calificacion DROP CONSTRAINT ux_codigos_calificacion_grupo_codigo;
UPDATE codigos_calificacion SET grupo = 'GENERAL';
ALTER TABLE codigos_calificacion ADD CONSTRAINT ux_codigos_calificacion_codigo UNIQUE (codigo);
-- POLIGRAFIA | VISITA | VALIDACIONES | TODOS (las tres).
ALTER TABLE codigos_calificacion ADD COLUMN area VARCHAR(20) NOT NULL DEFAULT 'TODOS';
ALTER TABLE codigos_calificacion ADD CONSTRAINT ck_codigos_calificacion_area
    CHECK (area IN ('POLIGRAFIA', 'VISITA', 'VALIDACIONES', 'TODOS'));
ALTER TABLE codigos_calificacion DROP CONSTRAINT ck_codigos_calificacion_puntos;
ALTER TABLE codigos_calificacion ADD CONSTRAINT ck_codigos_calificacion_puntos CHECK (
    (critico AND puntos IS NULL AND NOT es_alerta)
    OR (es_alerta AND NOT critico AND puntos = 0)
    OR (NOT critico AND NOT es_alerta AND puntos IS NOT NULL AND puntos BETWEEN 0 AND 100));

-- Los LEG y ALR de Antecedentes legales son de validaciones (los hace el analista).
UPDATE codigos_calificacion SET area = 'VALIDACIONES' WHERE codigo LIKE 'LEG-%' OR codigo LIKE 'ALR-%';

-- Alertas de Antecedentes (informativas: no suman ni salen en la tabla de hallazgos; el analista
-- las puede marcar en las columnas Ref.). Si la base ya las tiene (V205 las sembró), no se repiten.
INSERT INTO codigos_calificacion (grupo, codigo, descripcion, puntos, critico, por_proceso, solo_conduccion, activo, orden, es_alerta, area)
SELECT 'GENERAL', v.codigo, v.descripcion, 0, FALSE, FALSE, FALSE, TRUE, v.orden, TRUE, 'VALIDACIONES'
FROM (VALUES
    ('ALR-01', 'Persona expuesta políticamente (PEP)', 25),
    ('ALR-02', 'Coincidencia en listas solo por nombre (posible homónimo)', 26),
    ('ALR-03', 'Alerta de fraude o suplantación en Reconocer/Datacrédito (si se confirma → LEG-24)', 27),
    ('ALR-04', 'Posible conflicto de interés: participación en empresas (RUES) relacionadas con el cliente', 28),
    ('ALR-06', 'Dirección, teléfono o correo declarados no coinciden con Reconocer', 29)
) AS v (codigo, descripcion, orden)
WHERE NOT EXISTS (SELECT 1 FROM codigos_calificacion c WHERE c.codigo = v.codigo);

-- ── Constructor: para qué sirve cada campo en la calificación.
--   uso_calificacion: NULL (normal) | HALLAZGO (el ejecutor elige un hallazgo del catálogo; las
--     opciones son los códigos que el admin escogió) | CRITICIDAD (criticidad del cargo) |
--     CONDICION (Sí habilita los hallazgos "solo con condición").
--   calculo (columnas calculadas, las llena el sistema): PUNTAJE | FORMULA | NIVEL | SEMAFORO_BAJO | SEMAFORO_MEDIO |
--     SEMAFORO_ALTO | NUMERO_FILA | NOTA | y en la tabla de hallazgos REF | DESCRIPCION | FUENTE |
--     DETALLE | PUNTOS.
--   referencia + texto_nota (NOTA): el texto sale si el campo/tabla de referencia no tiene valor
--     (vacío, "No", "—" o tabla sin filas).
--   fuente_hallazgo / detalle_hallazgo: cómo se escribe el hallazgo en la tabla de hallazgos;
--     {clave} = valor de ese campo (de la misma fila si es una tabla).
ALTER TABLE formulario_columnas ADD COLUMN uso_calificacion VARCHAR(20);
ALTER TABLE formulario_columnas ADD COLUMN calculo VARCHAR(20);
ALTER TABLE formulario_columnas ADD COLUMN referencia VARCHAR(50);
ALTER TABLE formulario_columnas ADD COLUMN texto_nota VARCHAR(500);
ALTER TABLE formulario_columnas ADD COLUMN fuente_hallazgo VARCHAR(300);
ALTER TABLE formulario_columnas ADD COLUMN detalle_hallazgo VARCHAR(300);

-- Hallazgo que genera una opción de una lista normal (ej. "No encontrada" → LEG-01).
CREATE TABLE formulario_columna_hallazgos (
    id          SERIAL      PRIMARY KEY,
    id_columna  INTEGER     NOT NULL REFERENCES formulario_columnas (id_columna) ON DELETE CASCADE,
    opcion      VARCHAR(200) NOT NULL,
    codigo      VARCHAR(20) NOT NULL,
    CONSTRAINT ux_formulario_columna_hallazgos UNIQUE (id_columna, opcion)
);
