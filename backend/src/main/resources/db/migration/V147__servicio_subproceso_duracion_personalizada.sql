-- Duración real de ESTE subproceso puntual — si es null, se usa el estimado del catálogo
-- (tipos_progreso.minutos_estimados). El tiempo real varía caso a caso (una poligrafía o una
-- visita no siempre duran lo mismo), así que gestor/programador pueden ajustarlo al asignar,
-- sin tener que cambiar el estimado general de todo el catálogo.
ALTER TABLE servicio_subprocesos ADD COLUMN duracion_minutos INTEGER;
