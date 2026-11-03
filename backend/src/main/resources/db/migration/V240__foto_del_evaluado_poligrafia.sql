-- PF-06 (2026-11-02, pedido del usuario): foto obligatoria del evaluado, tomada en vivo con la cámara al iniciar la prueba. Es la constancia de que la prueba
-- se le hizo a la persona indicada.
-- · Cada foto queda registrada con fecha, hora y usuario que la tomó, y si verificó la cédula original (no se imprime). Solo inserción: reemplazar la foto
--   (el gestor, con motivo) deja la anterior en el historial.
-- · «bloqueada_en»: desde que el poligrafista pasa a la fase 2 la foto queda fija; solo el gestor la reemplaza.
-- · El Word imprime la foto del evaluado (vigente) en el marcador de campo ev_foto, recortada en proporción vertical 3:4.

CREATE TABLE IF NOT EXISTS fotos_evaluado_poligrafia (
    id                       BIGSERIAL PRIMARY KEY,
    id_servicio_subproceso   BIGINT       NOT NULL,
    ruta                     VARCHAR(500) NOT NULL,
    id_usuario               BIGINT,
    nombre_usuario           VARCHAR(200),
    fecha                    TIMESTAMP    NOT NULL DEFAULT NOW(),
    verificacion_cedula      BOOLEAN      NOT NULL,
    vigente                  BOOLEAN      NOT NULL DEFAULT TRUE,
    motivo_reemplazo         VARCHAR(500),
    bloqueada_en             TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fotos_poligrafia_subproceso ON fotos_evaluado_poligrafia (id_servicio_subproceso);
CREATE UNIQUE INDEX IF NOT EXISTS ux_fotos_poligrafia_vigente ON fotos_evaluado_poligrafia (id_servicio_subproceso) WHERE vigente;

-- Campo ev_foto de la plantilla (el marcador del Word). Su valor no se guarda en el formulario: sale de la tabla de arriba. La pantalla lo esconde.
INSERT INTO formulario_columnas (id_seccion, clave, nombre, tipo_dato, orden, calculado, obligatorio, activo, califica_riesgo)
SELECT s.id_seccion, 'ev_foto', 'Foto del evaluado (la toma la cámara del poligrafista)', 'ARCHIVO', 102, FALSE, FALSE, TRUE, FALSE
FROM formulario_secciones s JOIN tipos_progreso t ON t.id_tipo_progreso = s.id_tipo_progreso
WHERE t.nombre_progreso = 'Poligrafia Pre Empleo' AND s.clave = 'datos_prueba' AND s.activo
  AND NOT EXISTS (SELECT 1 FROM formulario_columnas c WHERE c.id_seccion = s.id_seccion AND c.clave = 'ev_foto');
