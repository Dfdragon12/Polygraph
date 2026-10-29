-- El catálogo de hallazgos ya no dice a quién le aplica cada hallazgo (decisión del usuario
-- 2026-10-06): eso lo define la plantilla, que escoge qué hallazgos ofrece cada campo. Todo el
-- catálogo activo está disponible en el constructor de cualquier plantilla.
ALTER TABLE codigos_calificacion DROP CONSTRAINT IF EXISTS ck_codigos_calificacion_area;
ALTER TABLE codigos_calificacion DROP COLUMN IF EXISTS area;
