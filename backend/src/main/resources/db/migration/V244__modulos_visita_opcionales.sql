-- VD-07 (2026-11-03): los módulos de la visita domiciliaria (rutina, virtual, vehicular, OEA, teletrabajo y las fotos de vehicular y teletrabajo)
-- se encienden y apagan desde «Datos generales». Apagar un módulo que ya tenía filas guardadas necesita poder quitarlas, y un visitador solo puede
-- quitar filas de una sección OPCIONAL (V212, «Quitar sección»). Se marcan opcionales; no cambia ninguna clave ni dato guardado, y el Word sigue
-- recibiendo la lista vacía cuando el módulo no está activo.
UPDATE formulario_secciones
   SET opcional = TRUE
 WHERE clave IN ('rutina', 'virtual', 'vehicular', 'oea_bd', 'oea', 'teletrabajo', 'fotos_vehicular', 'fotos_teletrabajo')
   AND id_tipo_progreso = (SELECT id_tipo_progreso FROM tipos_progreso WHERE id_tipo_progreso = 7);
