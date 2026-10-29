-- Un solo link de candidato por servicio (2026-09-29). Generar uno nuevo reemplaza (elimina) el
-- anterior — ver LinkCandidatoGestorService.generar. Aquí se limpian los duplicados que ya existen
-- (se conserva el más reciente de cada servicio) y se deja la regla en la base con un índice único.
-- Ninguna tabla apunta a links_candidato: la hoja de vida y los documentos van por candidato.

DELETE FROM links_candidato l
USING links_candidato mas_reciente
WHERE mas_reciente.id_servicio = l.id_servicio
  AND (mas_reciente.fecha_creacion > l.fecha_creacion
       OR (mas_reciente.fecha_creacion = l.fecha_creacion AND mas_reciente.id > l.id));

CREATE UNIQUE INDEX IF NOT EXISTS ux_links_candidato_servicio ON links_candidato (id_servicio);
