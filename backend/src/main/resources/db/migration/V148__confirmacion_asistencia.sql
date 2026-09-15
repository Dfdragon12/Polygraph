-- Confirmación de asistencia para poligrafía/visita domiciliaria — antes de que el
-- poligrafista/visitador se desplace, el programador llama al candidato para confirmar que va
-- a estar. Se guarda directo sobre el subproceso, sin auditoría aparte (no es tan crítico como
-- el historial de asignación).
ALTER TABLE servicio_subprocesos ADD COLUMN estado_confirmacion VARCHAR(20) NOT NULL DEFAULT 'SIN_LLAMAR';
ALTER TABLE servicio_subprocesos ADD COLUMN notas_confirmacion TEXT;
