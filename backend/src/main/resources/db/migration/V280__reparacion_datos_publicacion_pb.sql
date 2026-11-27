-- PC-07 · Reparación de los datos que dejó la migración de PB (decisiones del 2026-11-26). No borra ningún dato: todo lo que cambia queda en
-- migracion_reparacion_pb_reporte (tipo, estudio, subproceso, antes y después) y, donde hay un cambio de estado, en el historial de cambios que solo se inserta.
--
--   1) Estudios con la pasta EMITIDA por el «Publicar estudio» anterior que NUNCA tuvieron PDF (en este entorno #12, #29, #34, #35 y #53): vuelven a pasta
--      PENDIENTE y el servicio a FINALIZADO (con sus subprocesos ya publicados), que es el punto donde el gestor emite el estudio por la ventana nueva «Emitir estudio
--      completo» (decisión: no se inventa una nota del gestor ni se genera un PDF a escondidas). El riesgo del estudio se desbloquea (solo se bloquea al emitir).
--   2) Subprocesos PUBLICADOS sin conclusión ni nivel:
--        · conclusión = la del formato del ejecutor (el mismo campo que lee la ventana Publicar: ConclusionFormato) o, si no hay, la del cuadro genérico. Si dice
--          «Favorable»/«Desfavorable» como veredicto final («Concepto: FAVORABLE, nivel de riesgo BAJO.») se quita esa última frase; si aun así queda la palabra, o no hay
--          ninguna conclusión escrita, se pone el texto estándar «Conclusión no registrada…». El formato del ejecutor no se toca.
--        · nivel y puntaje = los del estudio (el que fijó el gestor en Riesgo, o el del motor) SOLO cuando el estudio tiene un único subproceso vigente, porque entonces
--          son los mismos. En estudios con varios subprocesos el nivel de uno no se deduce del estudio: queda vacío («Sin calificar») hasta que se publique de nuevo.
--   3) Poligrafía del #52 (publicada sin conclusión ni nivel): vuelve a FINALIZADO con motivo «Auditoría PB» para que el gestor la publique de nuevo con conclusión y nivel.
--      Queda en el registro de cambios. La pasta preliminar del #52 (que contaba esa Poligrafía publicada) vuelve a PENDIENTE y su PDF preliminar, que nunca se armó,
--      queda anulado (no vigente, en ERROR); el historial de la emisión no se borra.
--   4) PDF del estudio #42 (pasta EMITIDA y PDF en GENERANDO para siempre): aquí solo se marca. Al arrancar, ReparacionPdfEstudios lo reemplaza por una versión NUEVA (v2) que va por
--      la cola de PC-01; la v1 queda en el historial, en ERROR y no vigente. Cuando la v2 esté lista, el cliente recibe «El PDF de tu estudio se actualizó».
--   5) Al final se comprueba que ningún subproceso PUBLICADO queda con la conclusión vacía; si quedara uno, toda la migración se deshace.

CREATE TABLE IF NOT EXISTS migracion_reparacion_pb_reporte (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(40) NOT NULL,
    id_servicio    INTEGER,
    id_subproceso  BIGINT,
    antes          TEXT,
    despues        TEXT,
    creado_en      TIMESTAMP NOT NULL DEFAULT now()
);

-- ───────── 3) Poligrafía del #52 (primero, para que el paso 2 ya no la vea como PUBLICADA) ─────────
DROP TABLE IF EXISTS rep_revertir;
CREATE TEMP TABLE rep_revertir ON COMMIT DROP AS
SELECT ss.id AS id_sp, ss.id_servicio, ss.id_tipo_progreso, tp.nombre_progreso, ss.id_usuario_asignado
FROM servicio_subprocesos ss
JOIN tipos_progreso tp ON tp.id_tipo_progreso = ss.id_tipo_progreso
WHERE ss.id_servicio = 52 AND tp.nombre_progreso = 'Poligrafia Pre Empleo' AND ss.estado = 'PUBLICADO'
  AND COALESCE(btrim(ss.conclusion_publicada), '') = '' AND ss.nivel_publicado IS NULL;

INSERT INTO registro_cambios_subproceso (id_servicio, id_servicio_subproceso, id_tipo_progreso, tipo_subproceso, estado_anterior, estado_nuevo, marca, motivo,
                                         texto_adicional, id_responsable, id_usuario, fecha_hora, fecha_bogota, origen, id_historial_asignacion)
SELECT r.id_servicio, r.id_sp, r.id_tipo_progreso, r.nombre_progreso, 'PUBLICADO', 'FINALIZADO', NULL, 'Auditoría PB',
       'Se publicó sin conclusión ni nivel. Vuelve a FINALIZADO para que el gestor la publique de nuevo (reparación PC-07).',
       r.id_usuario_asignado, NULL, now(), now() AT TIME ZONE 'America/Bogota', 'MIGRADO', NULL
FROM rep_revertir r;

INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, id_subproceso, antes, despues)
SELECT 'SUBPROCESO_REVERTIDO', r.id_servicio, r.id_sp, 'PUBLICADO', 'FINALIZADO' FROM rep_revertir r;

UPDATE servicio_subprocesos ss
SET estado = 'FINALIZADO', fecha_publicado = NULL, publicado_por = NULL, conclusion_publicada = NULL, nivel_publicado = NULL, puntaje_publicado = NULL
WHERE ss.id IN (SELECT id_sp FROM rep_revertir);

-- La pasta preliminar del #52 contaba esa Poligrafía publicada: ya no hay ningún informe publicado.
INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, antes, despues)
SELECT 'PASTA_PRELIMINAR_ANULADA', s.id_servicio, s.pasta_estado, 'PENDIENTE'
FROM servicios s
WHERE s.id_servicio IN (SELECT id_servicio FROM rep_revertir) AND s.pasta_estado = 'PRELIMINAR'
  AND NOT EXISTS (SELECT 1 FROM servicio_subprocesos x WHERE x.id_servicio = s.id_servicio AND x.estado = 'PUBLICADO');

UPDATE documentos_estudio d
SET vigente = FALSE, estado = 'ERROR', detalle = 'Preliminar anulada: la Poligrafía volvió a revisión (reparación PC-07).'
WHERE d.tipo = 'PRELIMINAR' AND d.vigente = TRUE AND d.estado = 'GENERANDO' AND d.ruta IS NULL
  AND d.id_servicio IN (SELECT id_servicio FROM rep_revertir)
  AND NOT EXISTS (SELECT 1 FROM servicio_subprocesos x WHERE x.id_servicio = d.id_servicio AND x.estado = 'PUBLICADO');

UPDATE servicios s
SET pasta_estado = 'PENDIENTE'
WHERE s.id_servicio IN (SELECT id_servicio FROM rep_revertir) AND s.pasta_estado = 'PRELIMINAR'
  AND NOT EXISTS (SELECT 1 FROM servicio_subprocesos x WHERE x.id_servicio = s.id_servicio AND x.estado = 'PUBLICADO');

-- ───────── 2) Conclusión y nivel de lo que sigue PUBLICADO sin ellos ─────────
DROP TABLE IF EXISTS rep_conclusion;
CREATE TEMP TABLE rep_conclusion ON COMMIT DROP AS
WITH formato AS (
    SELECT DISTINCT ON (ss.id) ss.id AS id_sp, btrim(d.valor) AS texto
    FROM servicio_subprocesos ss
    JOIN formularios f ON f.id_servicio_subproceso = ss.id
    JOIN formulario_datos d ON d.id_formulario = f.id_formulario AND d.id_fila IS NULL
    JOIN formulario_columnas c ON c.id_columna = d.id_columna AND COALESCE(c.activo, TRUE)
    JOIN formulario_secciones s ON s.id_seccion = c.id_seccion AND COALESCE(s.activo, TRUE)
    WHERE ss.estado = 'PUBLICADO' AND COALESCE(btrim(ss.conclusion_publicada), '') = ''
      AND btrim(COALESCE(d.valor, '')) <> ''
      AND ((s.clave = 'concepto_analista' AND c.clave = 'concepto') OR (s.clave = 'firma' AND c.clave = 'pol_conclusion')
           OR (s.clave = 'concepto_final' AND c.clave = 'cf_conclusion'))
    ORDER BY ss.id, d.id_dato
), base AS (
    SELECT ss.id AS id_sp, ss.id_servicio,
           COALESCE(f.texto, NULLIF(btrim(COALESCE(ss.conclusion, '')), '')) AS texto,
           CASE WHEN f.texto IS NOT NULL THEN 'FORMATO' WHEN NULLIF(btrim(COALESCE(ss.conclusion, '')), '') IS NOT NULL THEN 'GENERICA' END AS origen
    FROM servicio_subprocesos ss LEFT JOIN formato f ON f.id_sp = ss.id
    WHERE ss.estado = 'PUBLICADO' AND COALESCE(btrim(ss.conclusion_publicada), '') = ''
), limpia AS (
    SELECT b.*, btrim(regexp_replace(b.texto, '\s*Concepto\s*:\s*(des)?favorable[^.]*\.?\s*$', '', 'i')) AS sin_veredicto
    FROM base b
)
SELECT l.id_sp, l.id_servicio, l.origen, l.texto AS original,
       CASE WHEN l.texto IS NULL OR l.sin_veredicto = ''
                 OR l.sin_veredicto ~* '(^|[^[:alpha:]])(des)?favorables?($|[^[:alpha:]])'
            THEN 'Conclusión no registrada: este informe se publicó antes de que la plataforma guardara la conclusión por informe.'
            ELSE l.sin_veredicto END AS final,
       CASE WHEN l.texto IS NULL THEN 'ESTANDAR'
            WHEN l.sin_veredicto = '' OR l.sin_veredicto ~* '(^|[^[:alpha:]])(des)?favorables?($|[^[:alpha:]])' THEN 'ESTANDAR'
            WHEN l.sin_veredicto <> l.texto THEN l.origen || '_SIN_VEREDICTO'
            ELSE l.origen END AS como
FROM limpia l;

INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, id_subproceso, antes, despues)
SELECT 'CONCLUSION_' || r.como, r.id_servicio, r.id_sp, r.original, r.final FROM rep_conclusion r;

UPDATE servicio_subprocesos ss
SET conclusion_publicada = r.final
FROM rep_conclusion r
WHERE ss.id = r.id_sp;

WITH cambiados AS (
    UPDATE servicio_subprocesos ss
    SET nivel_publicado = COALESCE(cr.nivel_decidido, cr.nivel_final), puntaje_publicado = cr.puntaje_final
    FROM calificaciones_riesgo cr
    WHERE ss.estado = 'PUBLICADO' AND ss.nivel_publicado IS NULL
      AND cr.id_servicio = ss.id_servicio AND cr.vigente = TRUE AND cr.estado = 'CALCULADO'
      AND COALESCE(cr.nivel_decidido, cr.nivel_final) IS NOT NULL
      AND (SELECT count(*) FROM servicio_subprocesos x WHERE x.id_servicio = ss.id_servicio AND x.estado <> 'CANCELADO') = 1
    RETURNING ss.id AS id_sp, ss.id_servicio, ss.nivel_publicado, ss.puntaje_publicado
)
INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, id_subproceso, antes, despues)
SELECT 'NIVEL_DEL_ESTUDIO', c.id_servicio, c.id_sp, NULL, c.nivel_publicado || ' ' || COALESCE(c.puntaje_publicado::text, '')
FROM cambiados c;

-- ───────── 1) Pasta EMITIDA sin ningún PDF ─────────
DROP TABLE IF EXISTS rep_pasta;
CREATE TEMP TABLE rep_pasta ON COMMIT DROP AS
SELECT s.id_servicio, COALESCE(c.id_gestor, s.emitido_por, (SELECT min(u.id_usuario) FROM usuarios u WHERE u.rol = 'ADMIN_POLYGRAPH')) AS id_usuario
FROM servicios s
JOIN clientes c ON c.id_cliente = s.id_cliente
WHERE s.estado = 'PUBLICADO' AND s.pasta_estado = 'EMITIDA'
  AND NOT EXISTS (SELECT 1 FROM documentos_estudio d WHERE d.id_servicio = s.id_servicio)
  AND EXISTS (SELECT 1 FROM servicio_subprocesos x WHERE x.id_servicio = s.id_servicio AND x.estado = 'PUBLICADO')
  AND NOT EXISTS (SELECT 1 FROM servicio_subprocesos x WHERE x.id_servicio = s.id_servicio AND x.estado NOT IN ('PUBLICADO', 'CANCELADO'));

INSERT INTO historial_estados_servicio (id_servicio, estado_anterior, estado_nuevo, id_usuario, fecha_cambio, observacion)
SELECT p.id_servicio, 'PUBLICADO', 'FINALIZADO', p.id_usuario, now(),
       'Reparación PC-07: el «Publicar estudio» anterior marcó la pasta como emitida sin generar el PDF. El estudio vuelve a FINALIZADO para emitirlo por «Emitir estudio completo».'
FROM rep_pasta p;

INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, antes, despues)
SELECT 'PASTA_A_PENDIENTE', p.id_servicio, 'servicio PUBLICADO · pasta EMITIDA · sin PDF', 'servicio FINALIZADO · pasta PENDIENTE' FROM rep_pasta p;

UPDATE calificaciones_riesgo SET bloqueado = FALSE
WHERE vigente = TRUE AND bloqueado = TRUE AND id_servicio IN (SELECT id_servicio FROM rep_pasta);

UPDATE servicios s SET estado = 'FINALIZADO', pasta_estado = 'PENDIENTE'
WHERE s.id_servicio IN (SELECT id_servicio FROM rep_pasta);

-- ───────── 4) PDF del estudio que nunca se armó (el #42) ─────────
-- Solo se MARCA aquí: al arrancar, ReparacionPdfEstudios lo reemplaza por una versión nueva que va por la cola (la anterior queda en el historial, en ERROR y no vigente).
INSERT INTO migracion_reparacion_pb_reporte (tipo, id_servicio, antes, despues)
SELECT 'PDF_POR_REGENERAR', d.id_servicio, 'v' || d.version || ' ' || d.tipo || ' ' || d.estado || ' sin archivo', d.id::text
FROM documentos_estudio d
JOIN servicios s ON s.id_servicio = d.id_servicio
WHERE d.tipo = 'FINAL' AND d.vigente = TRUE AND d.ruta IS NULL AND d.estado IN ('GENERANDO', 'ERROR') AND d.fecha < now() - interval '15 minutes'
  AND s.estado = 'PUBLICADO' AND s.pasta_estado = 'EMITIDA'
  AND NOT EXISTS (SELECT 1 FROM migracion_reparacion_pb_reporte r WHERE r.tipo = 'PDF_POR_REGENERAR' AND r.despues = d.id::text);

-- ───────── 5) Ningún subproceso PUBLICADO queda sin conclusión ─────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM servicio_subprocesos WHERE estado = 'PUBLICADO' AND COALESCE(btrim(conclusion_publicada), '') = '') THEN
        RAISE EXCEPTION 'PC-07: quedó un subproceso PUBLICADO sin conclusión';
    END IF;
END $$;
