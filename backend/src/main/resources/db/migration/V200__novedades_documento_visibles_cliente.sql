-- Las novedades automáticas por documento devuelto (Denegado, Ilegible, Vencido, Incompleto,
-- No corresponde — V198/V199) las ve también el cliente en el detalle de su solicitud: así sabe
-- que el estudio espera un documento corregido del candidato. El admin puede apagarlo por tipo
-- (Admin → Novedades, interruptor "Visible para el cliente").
UPDATE tipos_novedad SET visible_cliente = TRUE WHERE clave LIKE 'DOCUMENTO\_%';
