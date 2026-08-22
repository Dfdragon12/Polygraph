-- Agrega las Foreign Keys faltantes de usuarios hacia empleados y clientes.
-- (usuarios.id_empleado / usuarios.id_cliente se crearon en V2 sin restricción)

-- Limpieza defensiva: si algún usuario quedó apuntando a un id inexistente,
-- se libera la referencia para que el ALTER de abajo no falle.
UPDATE usuarios SET id_empleado = NULL
WHERE id_empleado IS NOT NULL
  AND id_empleado NOT IN (SELECT id_empleado FROM empleados);

UPDATE usuarios SET id_cliente = NULL
WHERE id_cliente IS NOT NULL
  AND id_cliente NOT IN (SELECT id_cliente FROM clientes);

ALTER TABLE usuarios
    ADD FOREIGN KEY (id_empleado) REFERENCES empleados (id_empleado) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE usuarios
    ADD FOREIGN KEY (id_cliente) REFERENCES clientes (id_cliente) DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX ON usuarios (id_empleado);
CREATE INDEX ON usuarios (id_cliente);
