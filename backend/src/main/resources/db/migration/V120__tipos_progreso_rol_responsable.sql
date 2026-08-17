-- Qué rol interno ejecuta cada subproceso — necesario para poder filtrar/validar la asignación
-- (antes cualquier empleado podía recibir cualquier "capacidad" sin ninguna relación con su rol).
-- POLIGRAFISTA ejecuta la prueba de polígrafo, VISITADOR ejecuta la visita domiciliaria; el resto
-- de subprocesos los ejecuta ANALISTA_INTERNO. El PROGRAMADOR no ejecuta: coordina/agenda estos
-- dos primeros, por eso no aparece aquí como rol_responsable.
ALTER TABLE tipos_progreso
    ADD COLUMN rol_responsable VARCHAR(30) NOT NULL DEFAULT 'ANALISTA_INTERNO';

UPDATE tipos_progreso SET rol_responsable = 'POLIGRAFISTA' WHERE nombre_progreso ILIKE '%poligraf%';
UPDATE tipos_progreso SET rol_responsable = 'VISITADOR'    WHERE nombre_progreso ILIKE '%visita%';
