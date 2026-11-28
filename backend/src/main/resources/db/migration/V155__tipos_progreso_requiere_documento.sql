-- Marca qué subprocesos necesitan un documento/verificación estructurada (como Antecedentes) —
-- generaliza lo que antes era una suposición implícita (nombre_progreso ILIKE '%antecedentes%'
-- regado por el código). El admin lo activa/desactiva desde admin/catálogo → Subprocesos, al
-- editar cada uno. Por ahora solo "Antecedentes" tiene su constructor de documento implementado;
-- los demás subprocesos que ya tienen tablas de verificación esbozadas en la base (Validación
-- Académica, Análisis Financiero, Validación de Referencias, Poligrafía) se activan cuando se
-- construya su propio módulo — no antes, para no mostrar un botón que no lleva a ningún lado.
ALTER TABLE tipos_progreso
    ADD COLUMN requiere_documento BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tipos_progreso SET requiere_documento = TRUE WHERE nombre_progreso = 'Antecedentes';
