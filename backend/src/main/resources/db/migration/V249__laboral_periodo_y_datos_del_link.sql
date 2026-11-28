-- LB-02 (2026-11-04): Verificación laboral. (1) Años del periodo validado por tipo de estudio (se sugiere al analista; el valor lo cambia el administrador en la base
-- hasta que exista la casilla en el catálogo): Avanzado, Básico, Básico virtual y Validación documental 4 años; Quick 2. Los demás quedan sin valor (sin sugerencia).
-- (2) El lugar de expedición ya no se escribe a mano: viene del link del evaluado, como en antecedentes (AN-02). (3) Tipo de novedad «un dato del evaluado no coincide».
ALTER TABLE procesos ADD COLUMN IF NOT EXISTS anios_periodo_laboral SMALLINT;

UPDATE procesos SET anios_periodo_laboral = 4 WHERE anios_periodo_laboral IS NULL AND nombre_proceso IN ('AVANZADO', 'BASICO', 'BASICO VIRTUAL', 'Validacion documental');
UPDATE procesos SET anios_periodo_laboral = 2 WHERE anios_periodo_laboral IS NULL AND nombre_proceso = 'QUICK';

UPDATE formulario_columnas
   SET campo_candidato = 'CIUDAD_EXPEDICION'
 WHERE clave = 'ev_lugar_exp'
   AND id_seccion IN (SELECT s.id_seccion FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
                       WHERE t.nombre_progreso = 'Validacion Laboral' AND s.clave = 'datos_evaluado');

INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, 'LAB_DATO_NO_COINCIDE', 'Un dato del evaluado no coincide con el documento',
       'El analista encontró que un dato del link del evaluado es distinto al del documento. El dato del link no se borra.', 0
FROM categorias_novedad c
WHERE c.clave = 'INFORMACION'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = 'LAB_DATO_NO_COINCIDE');

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, 'ANALISTA_INTERNO'
FROM tipos_novedad tn
WHERE tn.clave = 'LAB_DATO_NO_COINCIDE'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = 'ANALISTA_INTERNO');
