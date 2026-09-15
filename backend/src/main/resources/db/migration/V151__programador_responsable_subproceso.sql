-- El gestor ahora ELIGE a qué PROGRAMADOR se le encarga coordinar un subproceso de poligrafía/
-- visita (antes solo se notificaba a todos los PROGRAMADOR activos por igual, sin dejar rastro
-- de a quién quedó encargado). El PROGRAMADOR elegido sigue siendo quien decide el poligrafista/
-- visitador puntual y la fecha (columna id_usuario_asignado, sin cambios) — esta columna es solo
-- de quién quedó a cargo de coordinarlo.
ALTER TABLE servicio_subprocesos
    ADD COLUMN id_programador_responsable BIGINT REFERENCES usuarios (id_usuario);
