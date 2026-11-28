-- PB-03: cierre del estudio. Idempotente; no borra datos.
-- 1) La nota del gestor (concepto final) y quién emitió. Antes la observación de «Publicar estudio» quedaba solo en historial_estados_servicio y el cliente veía «Ninguno».
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS concepto_gestor TEXT;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS emitido_por BIGINT;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS emitido_en TIMESTAMP;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS revision_manual_por BIGINT;
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS revision_manual_en TIMESTAMP;

-- Lo que ya se publicó con el flujo anterior: la observación que escribió el gestor pasa a ser su nota (la última publicación).
UPDATE servicios s SET concepto_gestor = h.observacion
FROM (SELECT DISTINCT ON (id_servicio) id_servicio, observacion FROM historial_estados_servicio
      WHERE estado_nuevo = 'PUBLICADO' AND observacion IS NOT NULL AND btrim(observacion) <> '' ORDER BY id_servicio, fecha_cambio DESC) h
WHERE s.id_servicio = h.id_servicio AND s.concepto_gestor IS NULL;

-- 2) El PDF único del estudio completo: una fila por versión (v1, v2...). Solo la vigente se entrega al cliente; la descarga entrega el archivo guardado, no lo regenera.
CREATE TABLE IF NOT EXISTS documentos_estudio (
    id          BIGSERIAL PRIMARY KEY,
    id_servicio INTEGER      NOT NULL,
    version     INTEGER      NOT NULL,
    tipo        VARCHAR(12)  NOT NULL CHECK (tipo IN ('PRELIMINAR', 'FINAL')),
    nivel       VARCHAR(10),
    estado      VARCHAR(12)  NOT NULL DEFAULT 'GENERANDO' CHECK (estado IN ('GENERANDO', 'LISTO', 'ERROR')),
    vigente     BOOLEAN      NOT NULL DEFAULT TRUE,
    ruta        VARCHAR(300),
    paginas     INTEGER,
    detalle     TEXT,
    id_usuario  BIGINT,
    fecha       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (id_servicio, version)
);
CREATE INDEX IF NOT EXISTS ix_documentos_estudio_servicio ON documentos_estudio (id_servicio, vigente);

-- 3) Alertas que el gestor agrega para el cliente al emitir (la justificación es interna).
CREATE TABLE IF NOT EXISTS alertas_estudio (
    id            BIGSERIAL PRIMARY KEY,
    id_servicio   INTEGER   NOT NULL,
    texto         TEXT      NOT NULL,
    justificacion TEXT      NOT NULL,
    id_usuario    BIGINT,
    fecha         TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_alertas_estudio_servicio ON alertas_estudio (id_servicio);

-- 4) Nuevo tipo de trabajo en la cola: armar el PDF único.
ALTER TABLE trabajos_asincronos DROP CONSTRAINT IF EXISTS trabajos_asincronos_tipo_check;
ALTER TABLE trabajos_asincronos ADD CONSTRAINT trabajos_asincronos_tipo_check
    CHECK (tipo IN ('CARGA_MASIVA', 'INFORME_PDF', 'FORMULARIO_PDF', 'RECALCULO_TIEMPO', 'ESTUDIO_PDF'));

-- 5) Pesos de AVANZADO (Poligrafía 25, Visita 15, Antecedentes 25, Financiero 15, Académica 6, Laboral 10, Personal 4; obligatorios Antecedentes y Poligrafía).
-- Solo donde el peso aún no está configurado (NULL): lo que ya se ajustó desde el catálogo no se pisa. Cobertura mínima 60 ya quedó en V190.
UPDATE procesos_tipos_progreso pt SET peso = v.peso
FROM procesos p, tipos_progreso t, (VALUES
    ('poligraf', 25.00), ('visita', 15.00), ('antecedentes', 25.00), ('financiero', 15.00),
    ('academica', 6.00), ('laboral', 10.00), ('personal', 4.00)) AS v(clave, peso)
WHERE pt.id_proceso = p.id_proceso AND pt.id_tipo_progreso = t.id_tipo_progreso
  AND upper(p.nombre_proceso) = 'AVANZADO' AND pt.peso IS NULL
  AND lower(translate(t.nombre_progreso, 'áéíóúÁÉÍÓÚ', 'aeiouaeiou')) LIKE '%' || v.clave || '%'
  AND NOT (v.clave = 'laboral' AND lower(t.nombre_progreso) LIKE '%4 años%');
