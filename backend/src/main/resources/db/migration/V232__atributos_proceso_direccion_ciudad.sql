-- NS-08: qué le pide Nueva solicitud al cliente según el servicio.
--  · requiere_direccion: el servicio incluye una visita domiciliaria → dirección de la visita obligatoria y la ciudad se rotula «Ciudad de la visita».
--  · ciudad_es_de_prueba: el servicio incluye poligrafía o VerifEye → la ciudad se rotula «Ciudad donde presentará la prueba».
-- Se deducen de los subprocesos que ya tiene cada servicio (rol VISITADOR / POLIGRAFISTA o nombre VerifEye); el administrador los ajusta en Catálogo.
ALTER TABLE procesos ADD COLUMN requiere_direccion BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE procesos ADD COLUMN ciudad_es_de_prueba BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE servicios ADD COLUMN direccion_visita TEXT;

UPDATE procesos p SET requiere_direccion = TRUE
 WHERE EXISTS (SELECT 1 FROM procesos_tipos_progreso pt JOIN tipos_progreso t ON t.id_tipo_progreso = pt.id_tipo_progreso
                WHERE pt.id_proceso = p.id_proceso AND COALESCE(pt.habilitado, TRUE) AND t.rol_responsable = 'VISITADOR')
    OR p.nombre_proceso ILIKE '%visita%';

UPDATE procesos p SET ciudad_es_de_prueba = TRUE
 WHERE EXISTS (SELECT 1 FROM procesos_tipos_progreso pt JOIN tipos_progreso t ON t.id_tipo_progreso = pt.id_tipo_progreso
                WHERE pt.id_proceso = p.id_proceso AND COALESCE(pt.habilitado, TRUE)
                  AND (t.rol_responsable = 'POLIGRAFISTA' OR t.nombre_progreso ILIKE '%verifeye%'))
    OR p.nombre_proceso ILIKE '%poligraf%' OR p.nombre_proceso ILIKE '%verifeye%';

-- Qué quedó marcado, para revisarlo antes de producción (solo lectura; no se usa en el código).
CREATE TABLE migracion_atributos_proceso_reporte AS
SELECT id_proceso, nombre_proceso, requiere_direccion, ciudad_es_de_prueba FROM procesos
 WHERE requiere_direccion OR ciudad_es_de_prueba;
