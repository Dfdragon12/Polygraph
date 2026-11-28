-- V249 creó la columna como SMALLINT y la entidad la lee como Integer: Hibernate (ddl-auto=validate) no arrancaba. Se deja en INTEGER.
ALTER TABLE procesos ALTER COLUMN anios_periodo_laboral TYPE INTEGER;
