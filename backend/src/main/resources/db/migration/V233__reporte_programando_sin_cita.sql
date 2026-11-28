-- AJ-03: SOLO REPORTA (no cambia ningún dato). Qué ha estado en PROGRAMANDO sin llevar cita (poligrafía/visita = rol POLIGRAFISTA o VISITADOR):
--  · SUBPROCESO_ACTUAL:  subproceso sin cita que hoy está en PROGRAMANDO.
--  · SUBPROCESO_HISTORIA: subproceso sin cita que alguna vez pasó a PROGRAMANDO (registro de cambios), con quién y cuándo.
--  · SERVICIO_ACTUAL:    solicitud cuyo estado propio es PROGRAMANDO y NINGUNO de sus subprocesos lleva cita (el cambio automático que hacía el
--                        portal del candidato al enviar su hoja de vida, ya retirado), con quién y cuándo según historial_estados_servicio.
-- Corregir esos datos requiere confirmación: no se hace aquí.
CREATE TABLE migracion_programando_sin_cita_reporte AS
SELECT 'SUBPROCESO_ACTUAL'::text AS tipo, ss.id_servicio, ss.id AS id_subproceso, t.nombre_progreso AS detalle,
       NULL::bigint AS id_usuario, NULL::timestamp AS fecha
  FROM servicio_subprocesos ss JOIN tipos_progreso t ON t.id_tipo_progreso = ss.id_tipo_progreso
 WHERE ss.estado = 'PROGRAMANDO' AND t.rol_responsable NOT IN ('POLIGRAFISTA', 'VISITADOR')
UNION ALL
SELECT 'SUBPROCESO_HISTORIA', r.id_servicio, r.id_servicio_subproceso, t.nombre_progreso, r.id_usuario, r.fecha_bogota::timestamp
  FROM registro_cambios_subproceso r JOIN tipos_progreso t ON t.id_tipo_progreso = r.id_tipo_progreso
 WHERE r.estado_nuevo = 'PROGRAMANDO' AND t.rol_responsable NOT IN ('POLIGRAFISTA', 'VISITADOR')
UNION ALL
SELECT 'SERVICIO_ACTUAL', s.id_servicio, NULL, p.nombre_proceso, h.id_usuario, h.fecha_cambio
  FROM servicios s
  LEFT JOIN procesos p ON p.id_proceso = s.id_proceso
  LEFT JOIN LATERAL (SELECT id_usuario, fecha_cambio FROM historial_estados_servicio
                      WHERE id_servicio = s.id_servicio AND estado_nuevo = 'PROGRAMANDO' ORDER BY fecha_cambio DESC LIMIT 1) h ON TRUE
 WHERE s.estado = 'PROGRAMANDO'
   AND NOT EXISTS (SELECT 1 FROM servicio_subprocesos ss JOIN tipos_progreso t ON t.id_tipo_progreso = ss.id_tipo_progreso
                    WHERE ss.id_servicio = s.id_servicio AND t.rol_responsable IN ('POLIGRAFISTA', 'VISITADOR'));
