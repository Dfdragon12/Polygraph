-- Revisión del módulo del candidato (Katherine, 2026-10-06) — 8 puntos clave:
--  · educación: estado del estudio (Graduado / Terminó materias…) y estudios en el exterior;
--  · experiencia: contratado por empresa de servicios temporales (y cuál);
--  · inactividad: qué actividad realizó y quién o qué documento puede confirmarlo;
--  · referencias: confirmaciones del candidato (no es familiar ni convive / hablaron en 30 días);
--  · declaraciones: textos de antecedentes más precisos y dos preguntas financieras nuevas;
--  · tipo de contrato: lista cerrada.

ALTER TABLE educacion_evaluado
    ADD COLUMN estado        VARCHAR(40),
    ADD COLUMN en_exterior   BOOLEAN,
    ADD COLUMN pais_exterior VARCHAR(100);
-- "Finalizado" no decía si hay título: solo se conoce con certeza "En curso"; el resto lo
-- vuelve a escoger el candidato (la lista nueva distingue Graduado de Terminó materias).
UPDATE educacion_evaluado SET estado = 'En curso' WHERE en_curso IS TRUE;

ALTER TABLE experiencia_laboral_evaluado
    ADD COLUMN contratado_por_temporal BOOLEAN,
    ADD COLUMN nombre_temporal         VARCHAR(200);

ALTER TABLE inactividad_laboral_evaluado
    ADD COLUMN actividad     VARCHAR(60),
    ADD COLUMN quien_confirma TEXT;

ALTER TABLE referencia_personal_evaluado
    ADD COLUMN confirma_no_familiar    BOOLEAN,
    ADD COLUMN hablo_ultimos_30_dias   BOOLEAN;

-- Tipo de contrato: lista cerrada (lo ya guardado como texto no se toca).
UPDATE items_hoja_vida SET nombre = 'Aprendizaje (SENA)'
 WHERE lista = 'TIPO_CONTRATO' AND nombre = 'Aprendizaje';
UPDATE items_hoja_vida SET nombre = 'Independiente'
 WHERE lista = 'TIPO_CONTRATO' AND nombre = 'Independiente / informal';
UPDATE items_hoja_vida SET activo = FALSE
 WHERE lista = 'TIPO_CONTRATO' AND nombre = 'Otro';
INSERT INTO items_hoja_vida (lista, nombre, orden)
SELECT 'TIPO_CONTRATO', 'Informal (sin contrato)', 70
 WHERE NOT EXISTS (SELECT 1 FROM items_hoja_vida WHERE lista = 'TIPO_CONTRATO' AND nombre = 'Informal (sin contrato)');

-- Declaraciones de antecedentes: se ajusta el texto de las 3 existentes (la respuesta ya
-- guardada conserva el texto que el candidato vio) y se agregan las 2 financieras (FIN-08, FIN-05).
UPDATE items_hoja_vida
   SET nombre = '¿Tiene o ha tenido procesos judiciales (penales, civiles, laborales, de familia o administrativos)?',
       descripcion = 'Si responde Sí: cuáles, en qué calidad (demandado, denunciado, investigado, demandante, denunciante u otra) y su estado.'
 WHERE lista = 'PREGUNTA' AND orden = 10 AND nombre LIKE '¿Tiene o ha tenido procesos judiciales%';
UPDATE items_hoja_vida
   SET nombre = '¿Tiene o ha tenido órdenes de captura, requerimientos, multas del Código de Policía u otros asuntos pendientes con autoridades?',
       descripcion = 'Si responde Sí: cuál y su estado o resultado.'
 WHERE lista = 'PREGUNTA' AND orden = 20 AND nombre LIKE '¿Tiene o ha tenido órdenes de captura%';
UPDATE items_hoja_vida
   SET descripcion = 'Si responde Sí: cuáles, la autoridad que las impuso y su estado o resultado.'
 WHERE lista = 'PREGUNTA' AND orden = 30 AND nombre LIKE '¿Tiene o ha tenido sanciones disciplinarias%';

INSERT INTO items_hoja_vida (lista, nombre, descripcion, orden)
SELECT 'PREGUNTA', '¿Tiene actualmente obligaciones en mora o reportes negativos en centrales de riesgo?',
       'Si responde Sí: con qué entidad y desde cuándo.', 40
 WHERE NOT EXISTS (SELECT 1 FROM items_hoja_vida WHERE lista = 'PREGUNTA' AND nombre LIKE '¿Tiene actualmente obligaciones en mora%');
INSERT INTO items_hoja_vida (lista, nombre, descripcion, orden)
SELECT 'PREGUNTA', '¿Tiene embargos o procesos de cobro jurídico en curso?',
       'Si responde Sí: cuál.', 50
 WHERE NOT EXISTS (SELECT 1 FROM items_hoja_vida WHERE lista = 'PREGUNTA' AND nombre LIKE '¿Tiene embargos o procesos de cobro%');
