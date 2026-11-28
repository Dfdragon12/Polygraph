-- VD-02 (2026-11-03): cuando el visitador cambia la configuración que llegó de la solicitud (tipo, clase o módulos de la visita) o ve que el
-- cargo real no coincide con el de la solicitud, se registra una novedad de su subproceso: queda en el historial y el gestor y el programador
-- se enteran como con cualquier otra. No pausan el reloj ni las ve el cliente.
INSERT INTO tipos_novedad (id_categoria_novedad, clave, nombre, texto_ayuda, orden)
SELECT c.id_categoria_novedad, t.clave, t.nombre, t.ayuda, 0
FROM (VALUES
    ('VISITA_CAMBIO_CONFIGURACION', 'Cambio en la configuración de la visita', 'Se registra sola cuando el visitador cambia el tipo, la clase o los módulos de la visita.'),
    ('VISITA_CARGO_NO_COINCIDE',    'El cargo no coincide con la solicitud',   'El visitador avisa que el cargo real del evaluado es distinto al de la solicitud.')
) AS t (clave, nombre, ayuda)
JOIN categorias_novedad c ON c.clave = 'INFORMACION'
WHERE NOT EXISTS (SELECT 1 FROM tipos_novedad x WHERE x.clave = t.clave);

INSERT INTO tipos_novedad_roles (id_tipo_novedad, rol)
SELECT tn.id_tipo_novedad, 'VISITADOR'
FROM tipos_novedad tn
WHERE tn.clave IN ('VISITA_CAMBIO_CONFIGURACION', 'VISITA_CARGO_NO_COINCIDE')
  AND NOT EXISTS (SELECT 1 FROM tipos_novedad_roles x WHERE x.id_tipo_novedad = tn.id_tipo_novedad AND x.rol = 'VISITADOR');
