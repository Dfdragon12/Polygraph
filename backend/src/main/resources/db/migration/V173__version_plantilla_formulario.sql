-- Contador simple de versión de la plantilla de formulario (secciones/columnas) por tipo de
-- progreso — se sube en 1 cada vez que admin/catálogo → Plantilla guarda cambios estructurales
-- (agrega/quita campos). Cada subproceso guarda la versión que estaba vigente cuando se creó,
-- para que analista/poligrafista/visitador/gestor/programador vean con qué versión de la
-- plantilla están trabajando (y si quedó desactualizada frente a la versión actual).
ALTER TABLE tipos_progreso ADD COLUMN version_plantilla INTEGER NOT NULL DEFAULT 1;
ALTER TABLE servicio_subprocesos ADD COLUMN version_plantilla INTEGER NOT NULL DEFAULT 1;
