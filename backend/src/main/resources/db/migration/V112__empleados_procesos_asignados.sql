-- Subprocesos (tipos_progreso) del catálogo asignados a un empleado (uso principal: ANALISTA_INTERNO).
CREATE TABLE IF NOT EXISTS empleados_tipos_progreso (
    id_empleado     INTEGER NOT NULL REFERENCES empleados (id_empleado) ON DELETE CASCADE,
    id_tipo_progreso INTEGER NOT NULL REFERENCES tipos_progreso (id_tipo_progreso) ON DELETE CASCADE,
    PRIMARY KEY (id_empleado, id_tipo_progreso)
);
