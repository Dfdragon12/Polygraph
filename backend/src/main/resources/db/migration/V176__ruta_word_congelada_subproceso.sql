-- Qué archivo Word (.docx con marcadores) estaba activo cuando este subproceso se completó —
-- hasta ahora, version_plantilla congelaba la ESTRUCTURA (secciones/columnas) pero el documento
-- Word usado para generar/descargar seguía siendo siempre el ACTUAL, sin importar cuál estaba
-- vigente al completarse. Si el administrador subía un .docx nuevo después, un subproceso viejo ya
-- completado/publicado se re-generaba mezclado con el Word nuevo, perdiendo en silencio los datos
-- de campos que ya no tienen marcador en ese archivo.
ALTER TABLE servicio_subprocesos ADD COLUMN ruta_word_congelada VARCHAR(500);
