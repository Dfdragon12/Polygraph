-- Hasta ahora la conversación cliente↔gestor era UNA sola por empresa, compartida entre el
-- ADMIN_CLIENTE y todos sus ANALISTA_CLIENTE (cualquiera de ellos veía y "contestaba" el mismo
-- hilo). Se separa en un hilo por cada usuario del lado cliente — id_usuario_cliente identifica
-- de quién es el hilo (a diferencia de id_usuario_emisor, que es solo quién escribió ESE mensaje
-- puntual: en un mensaje GESTOR, el emisor es el gestor pero el hilo sigue siendo el de un
-- usuario-cliente específico).
ALTER TABLE mensajes_cliente_gestor ADD COLUMN id_usuario_cliente BIGINT;

-- Backfill: un mensaje que ya escribió un cliente es, por definición, del hilo de quien lo escribió.
UPDATE mensajes_cliente_gestor SET id_usuario_cliente = id_usuario_emisor WHERE origen = 'CLIENTE';

-- Backfill: mensajes históricos del lado GESTOR (de cuando todavía no existían hilos separados)
-- se asignan al ADMIN_CLIENTE de esa empresa, como mejor aproximación razonable — es casi
-- siempre con quien hablaba el gestor antes de que existiera el autoservicio de crear analistas.
UPDATE mensajes_cliente_gestor m
SET id_usuario_cliente = (
    SELECT u.id_usuario FROM usuarios u
    WHERE u.id_cliente = m.id_cliente AND u.rol = 'ADMIN_CLIENTE'
    ORDER BY u.id_usuario ASC LIMIT 1
)
WHERE m.origen = 'GESTOR' AND m.id_usuario_cliente IS NULL;

-- Los mensajes son temporales (no un historial permanente, se purgan por fecha_expiracion) — un
-- residual sin dueño claro (cliente sin ningún ADMIN_CLIENTE, no debería pasar) se descarta en
-- vez de bloquear la migración.
DELETE FROM mensajes_cliente_gestor WHERE id_usuario_cliente IS NULL;

ALTER TABLE mensajes_cliente_gestor ALTER COLUMN id_usuario_cliente SET NOT NULL;
ALTER TABLE mensajes_cliente_gestor
    ADD CONSTRAINT mensajes_cliente_gestor_id_usuario_cliente_fkey
    FOREIGN KEY (id_usuario_cliente) REFERENCES usuarios(id_usuario);

CREATE INDEX mensajes_cliente_gestor_id_usuario_cliente_idx
    ON mensajes_cliente_gestor (id_usuario_cliente, fecha_envio);
