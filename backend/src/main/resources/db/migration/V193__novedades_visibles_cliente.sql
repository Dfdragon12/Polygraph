-- Novedades que ve el CLIENTE en el detalle de su solicitud (decisión 25-sep-2026).
-- El administrador marca en Admin → Novedades qué tipos son visibles para el cliente. El cliente ve
-- solo tipo, subproceso, estado y la respuesta de quien la resolvió — nunca la descripción del
-- ejecutor ni las observaciones internas del seguimiento.
ALTER TABLE tipos_novedad ADD COLUMN visible_cliente BOOLEAN NOT NULL DEFAULT FALSE;

-- Por defecto: lo que tiene que ver con el candidato y la logística de la cita. Quedan internas
-- las técnicas, "Otra", la inconsistencia de información y la salud del candidato (sensible).
UPDATE tipos_novedad SET visible_cliente = TRUE
WHERE clave IN ('CANDIDATO_NO_ASISTIO', 'CANDIDATO_LLEGO_TARDE', 'CANDIDATO_SOLICITA_REPROGRAMAR',
                'CANDIDATO_SE_NEGO', 'DIRECCION_ERRADA', 'ZONA_RIESGO', 'DOCUMENTACION_INCOMPLETA',
                'REFERENCIA_NO_CONTACTABLE');
