-- Agrega la Foreign Key faltante de enlaces_externos hacia usuarios.
-- (enlaces_externos.creado_por_id se creó en V101 sin restricción)

UPDATE enlaces_externos SET creado_por_id = NULL
WHERE creado_por_id IS NOT NULL
  AND creado_por_id NOT IN (SELECT id_usuario FROM usuarios);

ALTER TABLE enlaces_externos
    ADD FOREIGN KEY (creado_por_id) REFERENCES usuarios (id_usuario) DEFERRABLE INITIALLY IMMEDIATE;
