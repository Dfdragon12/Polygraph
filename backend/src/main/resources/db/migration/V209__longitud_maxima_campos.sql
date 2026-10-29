-- Máximo de caracteres de un campo de texto (2026-10-06, pedido del usuario): lo pone el admin en el
-- constructor y quien diligencia ve el contador "120 / 500". NULL = el máximo por defecto (2.000).
ALTER TABLE formulario_columnas ADD COLUMN longitud_maxima INTEGER;
ALTER TABLE formulario_columnas ADD CONSTRAINT ck_formulario_columnas_longitud_maxima
    CHECK (longitud_maxima IS NULL OR longitud_maxima BETWEEN 1 AND 10000);
