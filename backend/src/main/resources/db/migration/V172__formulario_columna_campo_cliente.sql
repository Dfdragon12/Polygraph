-- Columna manual que muestra un dato del cliente que solicitó el servicio (NIT, nombre de la
-- empresa, teléfono) resuelto en vivo — mismo mecanismo que campo_candidato, pero para Cliente.
ALTER TABLE formulario_columnas ADD COLUMN campo_cliente VARCHAR(50);
