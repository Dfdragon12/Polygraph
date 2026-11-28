-- SE-07: confirmación de citas, cita no realizada y reprogramación.
--
-- 1) motivos_subproceso.codigo: identifica los dos motivos de reprogramación con reglas propias, sin depender del texto que el administrador
--    pueda renombrar: NO_SE_PRESENTO (solo se registra pasados los 15 minutos de tolerancia) y AVISO_NO_ASISTIRA (lo registra quien recibe el
--    aviso: programador o gestor).
-- 2) servicio_subprocesos.aviso_no_llegado_en: cuándo el profesional avisó «El evaluado no ha llegado» (un solo aviso por cita).
--    servicio_subprocesos.aviso_por_confirmar_en: cuándo se avisó que la cita quedó en «Por confirmar» (un solo aviso por cita).
--    Las citas que ya estaban vencidas y sin registrar al migrar se marcan como avisadas para no mandar un aluvión de avisos viejos;
--    siguen apareciendo en la bandeja «Por confirmar».
-- Idempotente: solo agrega columnas y rellena datos.

ALTER TABLE motivos_subproceso ADD COLUMN IF NOT EXISTS codigo VARCHAR(30);

UPDATE motivos_subproceso SET codigo = 'NO_SE_PRESENTO'
WHERE tipo = 'REPROGRAMACION' AND codigo IS NULL AND nombre ILIKE 'El candidato no se presentó%';
UPDATE motivos_subproceso SET codigo = 'AVISO_NO_ASISTIRA'
WHERE tipo = 'REPROGRAMACION' AND codigo IS NULL AND nombre ILIKE 'El candidato avisó que no asistirá%';

CREATE UNIQUE INDEX IF NOT EXISTS ux_motivos_subproceso_codigo ON motivos_subproceso (codigo) WHERE codigo IS NOT NULL;

ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS aviso_no_llegado_en   TIMESTAMP;
ALTER TABLE servicio_subprocesos ADD COLUMN IF NOT EXISTS aviso_por_confirmar_en TIMESTAMP;

UPDATE servicio_subprocesos ss
SET aviso_por_confirmar_en = NOW()
FROM tipos_progreso tp
WHERE tp.id_tipo_progreso = ss.id_tipo_progreso
  AND tp.rol_responsable IN ('POLIGRAFISTA', 'VISITADOR')
  AND ss.estado = 'EN_EJECUCION'
  AND ss.iniciado = FALSE
  AND ss.fecha_programada IS NOT NULL
  AND ss.fecha_programada < NOW() - INTERVAL '15 minutes'
  AND ss.aviso_por_confirmar_en IS NULL;
