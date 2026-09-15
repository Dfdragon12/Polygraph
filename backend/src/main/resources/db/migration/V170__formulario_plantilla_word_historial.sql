-- Registro de cuándo se subió la plantilla Word activa y quién la subió, más un historial de
-- las versiones anteriores (cada vez que se reemplaza el .docx, la versión saliente se archiva
-- aquí antes de sobrescribir formulario_plantilla_config).
ALTER TABLE formulario_plantilla_config
    ADD COLUMN fecha_carga TIMESTAMP,
    ADD COLUMN id_usuario_carga BIGINT REFERENCES usuarios(id_usuario);

CREATE TABLE formulario_plantilla_word_historial (
    id BIGSERIAL PRIMARY KEY,
    id_tipo_progreso INTEGER NOT NULL REFERENCES tipos_progreso(id_tipo_progreso),
    ruta VARCHAR(255) NOT NULL,
    fecha_carga TIMESTAMP NOT NULL,
    id_usuario_carga BIGINT REFERENCES usuarios(id_usuario)
);

CREATE INDEX formulario_plantilla_word_historial_tipo_idx
    ON formulario_plantilla_word_historial (id_tipo_progreso);
