-- SE-05: ajustes del semáforo de tiempo.
--
-- 1) procesos.requiere_hoja_vida: la pausa por candidato (esperar el link / la hoja de vida) solo aplica a los servicios que la necesitan.
--    Antecedentes y los demás que no la necesitan arrancan a contar desde la recepción. Por defecto TRUE; Antecedentes pasa a FALSE.
--    Lo que ya existía: las pausas de espera de hoja de vida de esos servicios se dejan en 0 días (fin = inicio), sin borrarlas, y quedan
--    anotadas en migracion_pausas_sin_hoja_vida_reporte. El recálculo de tiempo de esos servicios lo hace la tarea del arranque
--    (TiempoHistoricoInicializador) y el de cada noche.
-- 2) Las columnas de origen de la pausa pasan de 10 a 20 caracteres: nace el origen REPROGRAMACION (mientras algún subproceso del servicio
--    esté en REPROGRAMADO el reloj del servicio se pausa).
-- Idempotente: solo agrega columnas y ajusta datos; no borra.

ALTER TABLE procesos ADD COLUMN IF NOT EXISTS requiere_hoja_vida BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE procesos SET requiere_hoja_vida = FALSE WHERE nombre_proceso ILIKE 'antecedentes';

ALTER TABLE solicitud_pausa ALTER COLUMN origen TYPE VARCHAR(20);
ALTER TABLE servicios ALTER COLUMN pausa_origen TYPE VARCHAR(20);

CREATE TABLE IF NOT EXISTS migracion_pausas_sin_hoja_vida_reporte (
    id_pausa     BIGINT PRIMARY KEY,
    id_servicio  INTEGER   NOT NULL,
    proceso      VARCHAR(100),
    inicio       TIMESTAMP NOT NULL,
    fin_anterior TIMESTAMP,
    migrado_en   TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO migracion_pausas_sin_hoja_vida_reporte (id_pausa, id_servicio, proceso, inicio, fin_anterior)
SELECT p.id, p.solicitud_id, pr.nombre_proceso, p.inicio, p.fin
FROM solicitud_pausa p
JOIN servicios s ON s.id_servicio = p.solicitud_id
JOIN procesos pr ON pr.id_proceso = s.id_proceso
WHERE p.motivo = 'Espera la hoja de vida del candidato'
  AND pr.requiere_hoja_vida = FALSE
  AND (p.fin IS NULL OR p.fin > p.inicio)
ON CONFLICT (id_pausa) DO NOTHING;

UPDATE solicitud_pausa p
SET fin = p.inicio
FROM migracion_pausas_sin_hoja_vida_reporte r
WHERE r.id_pausa = p.id AND (p.fin IS NULL OR p.fin > p.inicio);

-- Lo que se ajustó queda para recalcular: las abiertas lo hace la tarea de cada noche; las entregadas, el arranque (ver Java).
UPDATE servicios s
SET cumplimiento = NULL
WHERE s.estado = 'PUBLICADO'
  AND s.id_servicio IN (SELECT id_servicio FROM migracion_pausas_sin_hoja_vida_reporte);
