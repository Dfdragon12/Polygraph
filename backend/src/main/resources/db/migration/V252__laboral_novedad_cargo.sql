-- LB-07 (2026-11-04): Verificación laboral. El cargo llega tal cual lo escribió el cliente en la solicitud y no se corrige desde el código: si tiene un error, el analista
-- avisa al gestor con una novedad («el cargo tiene un error», con el texto actual).
INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, 'LAB_CARGO_NO_COINCIDE', 'El cargo de la solicitud tiene un error',
       'El analista avisa que el cargo escrito en la solicitud parece tener un error. El cargo de la solicitud no se modifica desde el formulario.', 0
FROM categorias_novedad c
WHERE c.clave = 'INFORMACION'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = 'LAB_CARGO_NO_COINCIDE');

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, 'ANALISTA_INTERNO'
FROM tipos_novedad tn
WHERE tn.clave = 'LAB_CARGO_NO_COINCIDE'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = 'ANALISTA_INTERNO');
