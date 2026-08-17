-- Relación real de empleados con el catálogo de ciudades (antes solo texto libre)
ALTER TABLE empleados ADD COLUMN IF NOT EXISTS id_ciudad_residencia INTEGER;

ALTER TABLE empleados ADD CONSTRAINT fk_empleados_ciudad
    FOREIGN KEY (id_ciudad_residencia) REFERENCES ciudades (id_ciudad) DEFERRABLE INITIALLY IMMEDIATE;

-- Mejor esfuerzo: mapear el texto libre existente al catálogo por nombre de ciudad
UPDATE empleados e SET id_ciudad_residencia = c.id_ciudad
    FROM ciudades c
    WHERE e.id_ciudad_residencia IS NULL
      AND e.ciudad_residencia IS NOT NULL
      AND UPPER(TRIM(e.ciudad_residencia)) = UPPER(c.nombre_ciudad);

-- La columna vieja ciudad_residencia se deja intacta (no se borra) por si quedaron
-- registros sin match; deja de usarse desde el código de aquí en adelante.
