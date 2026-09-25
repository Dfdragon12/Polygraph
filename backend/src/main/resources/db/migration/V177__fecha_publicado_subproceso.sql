-- Cuándo el gestor/programador aprobó (PUBLICADO) un subproceso completado — hasta ahora solo se
-- guardaba fecha_completado (cuando quien lo diligenció terminó), sin ningún registro de cuándo
-- se aprobó después, aunque son momentos distintos y pueden estar separados por días.
ALTER TABLE servicio_subprocesos ADD COLUMN fecha_publicado TIMESTAMP;
