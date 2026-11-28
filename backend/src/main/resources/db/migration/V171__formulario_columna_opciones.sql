-- Opciones de una columna tipo LISTA (lista desplegable) — texto libre separado por coma,
-- definido por el administrador al armar la plantilla. Null/vacío para cualquier otro tipo de dato.
ALTER TABLE formulario_columnas ADD COLUMN opciones VARCHAR(1000);
