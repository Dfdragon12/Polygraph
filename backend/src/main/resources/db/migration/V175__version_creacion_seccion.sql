-- Con qué versión de la plantilla (tipos_progreso.version_plantilla) se creó cada sección —
-- necesario para que un subproceso ya creado quede "congelado" a las secciones que existían en
-- ese momento, sin importar que admin agregue secciones nuevas después. Las secciones que ya
-- existen quedan en 1 (misma convención que tipos_progreso.version_plantilla y
-- servicio_subprocesos.version_plantilla — todo arranca en 1 como línea base).
ALTER TABLE formulario_secciones ADD COLUMN version_creacion INTEGER NOT NULL DEFAULT 1;
