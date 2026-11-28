-- PC-04 · (1) Qué pasó con un informe devuelto y (2) tipos de novedad para «Solicitar corrección» de un informe ya publicado. No borra ni cambia datos existentes.
--
-- 1) La devolución deja de depender de «observaciones» (que se pisa al reasignar): motivo, detalle, quién y cuándo se guardan aparte y se limpian cuando el
--    ejecutor vuelve a enviar el informe. Sirven a la marca «Devuelto · motivo» de las bandejas y al aviso «Devuelto por … el …» de arriba del formato.
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS motivo_devolucion VARCHAR(400);
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS detalle_devolucion TEXT;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS devuelto_por BIGINT;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS fecha_devuelto TIMESTAMP;

-- Los que hoy están devueltos (marca devuelto = true): se toma la última devolución del registro de cambios (solo inserción).
UPDATE servicio_subprocesos ss
SET motivo_devolucion = r.motivo, detalle_devolucion = r.texto_adicional, devuelto_por = r.id_usuario, fecha_devuelto = r.fecha_bogota
FROM (SELECT DISTINCT ON (id_servicio_subproceso) id_servicio_subproceso, motivo, texto_adicional, id_usuario, fecha_bogota
      FROM registro_cambios_subproceso WHERE marca = 'DEVUELTO' ORDER BY id_servicio_subproceso, fecha_hora DESC) r
WHERE ss.id = r.id_servicio_subproceso AND ss.devuelto = TRUE AND ss.motivo_devolucion IS NULL;

-- 2) «Solicitar corrección» (informe ya publicado): una novedad del subproceso para el gestor. Un tipo por motivo, como los de documentos (V199). El cliente NO la ve
--    (visible_cliente queda en false). Una categoría propia para que no se confunda con las novedades del trabajo en campo.
INSERT INTO categorias_novedad (clave, nombre, color, orden)
SELECT 'CORRECCION', 'Corrección de informe publicado', 'violet', 6
WHERE NOT EXISTS (SELECT 1 FROM categorias_novedad WHERE clave = 'CORRECCION');

INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, t.clave, t.nombre, t.ayuda, t.orden
FROM (VALUES
    ('CORRECCION_DATO_ERRADO',      'Corrección: dato errado',              'El ejecutor pide corregir un dato del informe ya publicado.', 1),
    ('CORRECCION_SOPORTE_EQUIVOCADO', 'Corrección: soporte equivocado',     'El ejecutor pide corregir un soporte del informe ya publicado.', 2),
    ('CORRECCION_HALLAZGO_MAL_CALIF', 'Corrección: hallazgo mal calificado', 'El ejecutor pide corregir la calificación de un hallazgo del informe ya publicado.', 3),
    ('CORRECCION_OTRO',             'Corrección: otro',                     'El ejecutor pide corregir algo más del informe ya publicado.', 4)
) AS t (clave, nombre, ayuda, orden)
JOIN categorias_novedad c ON c.clave = 'CORRECCION'
WHERE NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = t.clave);

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, r.rol
FROM tipos_novedad tn CROSS JOIN (VALUES ('ANALISTA_INTERNO'), ('POLIGRAFISTA'), ('VISITADOR')) AS r (rol)
WHERE tn.clave LIKE 'CORRECCION\_%'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = r.rol);
