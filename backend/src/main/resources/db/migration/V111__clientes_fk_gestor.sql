-- Vincula cada cliente a un usuario de rol GESTOR como punto de contacto.
ALTER TABLE clientes ADD COLUMN id_gestor BIGINT NULL;

ALTER TABLE clientes
    ADD FOREIGN KEY (id_gestor) REFERENCES usuarios (id_usuario) DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX ON clientes (id_gestor);
