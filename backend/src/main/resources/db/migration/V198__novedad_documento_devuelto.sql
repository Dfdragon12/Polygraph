-- Tipo de novedad que se registra SOLO cuando analista/poligrafista/visitador devuelve un documento
-- del candidato desde el paso "Soportes" (FichaCandidatoService → NovedadSubprocesoService
-- .registrarPorDocumentoDevuelto). Queda en el catálogo como cualquier otro: el admin puede
-- renombrarlo, cambiar su visibilidad al cliente o desactivarlo (si está inactivo, devolver el
-- documento sigue funcionando, solo que sin novedad automática). Idempotente por clave.
INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, 'DOCUMENTO_DEVUELTO', 'Documento devuelto al candidato',
       'Se registra sola al devolver un soporte desde el paso "Soportes": indica qué documento y por qué.', 0
FROM categorias_novedad c
WHERE c.clave = 'INFORMACION'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad WHERE clave = 'DOCUMENTO_DEVUELTO');

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, r.rol
FROM tipos_novedad tn
CROSS JOIN (VALUES ('ANALISTA_INTERNO'), ('POLIGRAFISTA'), ('VISITADOR')) AS r (rol)
WHERE tn.clave = 'DOCUMENTO_DEVUELTO'
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = r.rol);
