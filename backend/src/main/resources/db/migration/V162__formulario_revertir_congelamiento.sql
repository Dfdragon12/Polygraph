-- Revierte V161: el congelamiento por fecha (ocultar campos nuevos según cuándo se llenó el
-- formulario por primera vez) se reemplaza por una regla más simple: mientras el subproceso no
-- esté COMPLETADO se puede ver y agregar cualquier campo activo; una vez COMPLETADO, el
-- formulario queda de solo lectura (ver ServicioSubproceso.estado / FormularioService).
ALTER TABLE formulario_secciones DROP COLUMN fecha_creacion;
ALTER TABLE formulario_columnas DROP COLUMN fecha_creacion;
ALTER TABLE formularios DROP COLUMN fecha_primer_llenado;
