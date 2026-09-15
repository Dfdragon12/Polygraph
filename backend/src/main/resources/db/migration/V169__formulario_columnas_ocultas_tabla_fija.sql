-- Para una sección tipo Tabla con origen automático (referencias, educación...), el admin puede
-- elegir cuáles de las columnas fijas mostrar en el formulario que diligencia el analista —
-- lista separada por comas de las claves que se OCULTAN (ej. "motivo_retiro,cargo_jefe"). Vacío/
-- NULL = se muestran todas, comportamiento de siempre.
ALTER TABLE formulario_secciones
    ADD COLUMN columnas_ocultas VARCHAR(255);
