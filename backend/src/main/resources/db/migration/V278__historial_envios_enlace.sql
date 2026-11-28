-- PC-03 · Historial de envíos del enlace del candidato. Idempotente; no borra ni cambia ningún dato existente.
--
-- 1) Cada envío (o reenvío) deja SU fila en envios_enlace (que solo se inserta): id_envio agrupa las filas de un mismo envío (una por canal) y
--    vigencia_hasta dice hasta cuándo valía ese envío (2 días hábiles desde su fecha). Las filas anteriores no los tienen: cada una cuenta como un envío.
ALTER TABLE envios_enlace ADD COLUMN IF NOT EXISTS id_envio VARCHAR(36);
ALTER TABLE envios_enlace ADD COLUMN IF NOT EXISTS vigencia_hasta DATE;

-- 2) Canal «ANTERIOR»: el primer envío del sistema anterior (antes de PB-04 no se registraba por dónde salió ni cuándo; solo se sabe que el candidato ya había entrado).
ALTER TABLE envios_enlace DROP CONSTRAINT IF EXISTS envios_enlace_canal_check;
ALTER TABLE envios_enlace ADD CONSTRAINT envios_enlace_canal_check
    CHECK (canal IN ('CORREO', 'WHATSAPP', 'COPIA', 'RECORDATORIO', 'CAMBIO_CONTACTO', 'VIGENCIA', 'ANTERIOR'));

-- 3) Enlaces que ya tenían señales de haberse enviado (el candidato entró con su cédula o ya envió su información) pero sin primer envío registrado, o con un
--    «primer envío» POSTERIOR a su primer ingreso (el reenvío de PB-04 llenó enviado_en después de que el candidato ya lo había abierto: #45):
--    el primer envío pasa a ser la fecha de creación del enlace (nunca después del primer ingreso) y queda en el historial como «ANTERIOR».
--    Los reenvíos que ya estaban registrados no se tocan. Un enlace SIN ninguna señal de envío se deja como estaba (No enviado): no se inventa un envío.
--    (vigencia_hasta queda vacía: la fecha de vigencia de aquel envío no se guardó y la de links_candidato ya pudo cambiar con un reenvío.)
INSERT INTO envios_enlace (id_link, canal, destino, detalle, id_usuario, automatico, fecha, vigencia_hasta)
SELECT l.id, 'ANTERIOR', NULL, 'Envío del sistema anterior: fecha de creación del enlace',
       l.generado_por_id, FALSE, LEAST(COALESCE(l.fecha_creacion, l.fecha_primer_ingreso), COALESCE(l.fecha_primer_ingreso, l.fecha_creacion)), NULL
FROM links_candidato l
WHERE (l.fecha_primer_ingreso IS NOT NULL OR l.usado = TRUE)
  AND COALESCE(l.fecha_creacion, l.fecha_primer_ingreso) IS NOT NULL
  AND (l.enviado_en IS NULL OR (l.fecha_primer_ingreso IS NOT NULL AND l.enviado_en > l.fecha_primer_ingreso))
  AND NOT EXISTS (SELECT 1 FROM envios_enlace e WHERE e.id_link = l.id AND e.canal = 'ANTERIOR');

UPDATE links_candidato l
SET enviado_en = LEAST(COALESCE(l.fecha_creacion, l.fecha_primer_ingreso), COALESCE(l.fecha_primer_ingreso, l.fecha_creacion))
WHERE (l.fecha_primer_ingreso IS NOT NULL OR l.usado = TRUE)
  AND COALESCE(l.fecha_creacion, l.fecha_primer_ingreso) IS NOT NULL
  AND (l.enviado_en IS NULL OR (l.fecha_primer_ingreso IS NOT NULL AND l.enviado_en > l.fecha_primer_ingreso))
  AND EXISTS (SELECT 1 FROM envios_enlace e WHERE e.id_link = l.id AND e.canal = 'ANTERIOR');
