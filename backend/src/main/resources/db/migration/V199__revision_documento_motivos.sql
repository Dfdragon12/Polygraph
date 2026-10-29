-- Revisión de documentos del candidato con resultado específico (2026-09-28): en vez de solo
-- "Devuelto", quien revisa elige de una lista: Aprobado, Denegado, Ilegible, Vencido, Incompleto o
-- No corresponde, con observación (obligatoria si no se aprueba). Todo lo que no es Aprobado cuenta
-- como devolución: el candidato ve el motivo y el documento queda bloqueado hasta que cargue uno
-- nuevo (ver FichaCandidatoService.revisarSoporte).

-- Primero se quita la restricción vieja (solo aceptaba DEVUELTO), luego se migran los datos.
ALTER TABLE documento_evaluado DROP CONSTRAINT IF EXISTS documento_evaluado_estado_revision_check;

UPDATE documento_evaluado SET estado_revision = 'DENEGADO' WHERE estado_revision = 'DEVUELTO';

ALTER TABLE documento_evaluado ADD CONSTRAINT documento_evaluado_estado_revision_check
    CHECK (estado_revision IN ('PENDIENTE', 'APROBADO', 'DENEGADO', 'ILEGIBLE', 'VENCIDO', 'INCOMPLETO', 'NO_CORRESPONDE'));

-- Un tipo de novedad por motivo: cuando analista/poligrafista/visitador devuelve un documento, la
-- novedad automática dice exactamente qué pasó. El "Documento devuelto" de V198 pasa a ser el de
-- "Denegado" (conserva su id y las novedades que ya lo usen).
UPDATE tipos_novedad
   SET clave = 'DOCUMENTO_DENEGADO', nombre = 'Documento del candidato denegado',
       texto_ayuda = 'Se registra sola al revisar un soporte como Denegado en el paso "Soportes".'
 WHERE clave = 'DOCUMENTO_DEVUELTO';

INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, t.clave, t.nombre, t.ayuda, 0
FROM (VALUES
    ('DOCUMENTO_DENEGADO',       'Documento del candidato denegado',       'Se registra sola al revisar un soporte como Denegado en el paso "Soportes".'),
    ('DOCUMENTO_ILEGIBLE',       'Documento del candidato ilegible',       'Se registra sola al revisar un soporte como Ilegible en el paso "Soportes".'),
    ('DOCUMENTO_VENCIDO',        'Documento del candidato vencido',        'Se registra sola al revisar un soporte como Vencido en el paso "Soportes".'),
    ('DOCUMENTO_INCOMPLETO',     'Documento del candidato incompleto',     'Se registra sola al revisar un soporte como Incompleto en el paso "Soportes".'),
    ('DOCUMENTO_NO_CORRESPONDE', 'Documento del candidato no corresponde', 'Se registra sola al revisar un soporte como No corresponde en el paso "Soportes".')
) AS t (clave, nombre, ayuda)
JOIN categorias_novedad c ON c.clave = 'INFORMACION'
WHERE NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = t.clave);

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, r.rol
FROM tipos_novedad tn
CROSS JOIN (VALUES ('ANALISTA_INTERNO'), ('POLIGRAFISTA'), ('VISITADOR')) AS r (rol)
WHERE tn.clave IN ('DOCUMENTO_DENEGADO', 'DOCUMENTO_ILEGIBLE', 'DOCUMENTO_VENCIDO', 'DOCUMENTO_INCOMPLETO', 'DOCUMENTO_NO_CORRESPONDE')
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = r.rol);
