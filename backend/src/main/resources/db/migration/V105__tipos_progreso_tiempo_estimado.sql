-- Tiempo estimado (en minutos) para realizar cada subproceso (tipo_progreso).
-- Se guarda como minutos totales; el frontend lo edita/muestra como horas + minutos.
ALTER TABLE tipos_progreso ADD COLUMN minutos_estimados INTEGER;
