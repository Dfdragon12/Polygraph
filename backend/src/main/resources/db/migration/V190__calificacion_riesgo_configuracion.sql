-- Fase A del motor de calificación de riesgo (ver docs/modelo-calificacion-riesgo.md): la
-- configuración que el motor necesita, toda en base de datos.
--
--   procesos 1 ──< procesos_tipos_progreso >── 1 tipos_progreso
--                  (peso, obligatorio, habilitado)
--   umbrales_riesgo (una fila por criticidad)    parametros_riesgo (una sola fila)
--
-- · procesos_tipos_progreso.peso: % que aporta cada subproceso al puntaje del proceso. NULL = aún
--   sin configurar (el proceso no califica). 0 = el subproceso se hace pero no califica. El
--   proceso solo califica cuando los pesos de sus subprocesos habilitados y activos suman 100.
-- · procesos_tipos_progreso.obligatorio: ya existía (el cliente la ve como Obligatorio/Opcional);
--   el motor la reutiliza — si un obligatorio queda No realizado, el caso va a revisión manual.
-- · procesos.version_calificacion: sube con cada cambio de pesos/obligatorios/cobertura/
--   criticidad, para saber con qué versión se calculó cada caso (fase D).

ALTER TABLE procesos_tipos_progreso
    ADD COLUMN peso NUMERIC(5,2) CHECK (peso BETWEEN 0 AND 100);

ALTER TABLE procesos
    ADD COLUMN cobertura_minima     NUMERIC(5,2) NOT NULL DEFAULT 100 CHECK (cobertura_minima BETWEEN 0 AND 100),
    ADD COLUMN criticidad_defecto   VARCHAR(10)  NOT NULL DEFAULT 'ESTANDAR'
        CHECK (criticidad_defecto IN ('ESTANDAR', 'SENSIBLE', 'CRITICO')),
    ADD COLUMN version_calificacion INTEGER      NOT NULL DEFAULT 1;

-- Umbrales por criticidad del cargo: nivel BAJO si puntaje <= limite_bajo; MEDIO si <= limite_medio;
-- ALTO si es mayor. minimo_medio: puntaje al que sube el "piso" cuando aplica.
CREATE TABLE umbrales_riesgo (
    criticidad    VARCHAR(10)  PRIMARY KEY CHECK (criticidad IN ('ESTANDAR', 'SENSIBLE', 'CRITICO')),
    limite_bajo   NUMERIC(5,2) NOT NULL,
    limite_medio  NUMERIC(5,2) NOT NULL,
    minimo_medio  NUMERIC(5,2) NOT NULL,
    CHECK (limite_bajo >= 0 AND limite_bajo < minimo_medio AND minimo_medio <= limite_medio AND limite_medio <= 100)
);

-- Parámetros de las reglas de piso (una sola fila, id = 1).
CREATE TABLE parametros_riesgo (
    id                            INTEGER      PRIMARY KEY CHECK (id = 1),
    umbral_servicio_alto          NUMERIC(5,2) NOT NULL CHECK (umbral_servicio_alto BETWEEN 0 AND 100),
    umbral_servicio_moderado      NUMERIC(5,2) NOT NULL CHECK (umbral_servicio_moderado BETWEEN 0 AND 100),
    cantidad_servicios_moderados  INTEGER      NOT NULL CHECK (cantidad_servicios_moderados >= 1),
    version                       INTEGER      NOT NULL DEFAULT 1,
    actualizado_por               BIGINT       REFERENCES usuarios (id_usuario),
    fecha_actualizacion           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Datos iniciales (valores de la especificación de negocio) ──
INSERT INTO umbrales_riesgo (criticidad, limite_bajo, limite_medio, minimo_medio) VALUES
    ('ESTANDAR', 30, 60, 31),
    ('SENSIBLE', 25, 50, 26),
    ('CRITICO',  20, 40, 21);

INSERT INTO parametros_riesgo (id, umbral_servicio_alto, umbral_servicio_moderado, cantidad_servicios_moderados)
VALUES (1, 70, 50, 2);

-- Procesos de un solo subproceso habilitado ("Antecedentes", "Análisis Financiero"...): ese
-- subproceso pesa 100 %. El resto de procesos queda SIN pesos — el reparto lo define el negocio
-- desde el catálogo; aquí no se inventa ningún valor.
UPDATE procesos_tipos_progreso pt
SET peso = 100
WHERE pt.habilitado = true
  AND pt.id_proceso IN (
      SELECT id_proceso FROM procesos_tipos_progreso
      WHERE habilitado = true
      GROUP BY id_proceso
      HAVING count(*) = 1);

-- Cobertura mínima explícita de la especificación para AVANZADO. Los obligatorios NO se tocan
-- (el cliente los ve): se ajustan desde el catálogo.
UPDATE procesos SET cobertura_minima = 60 WHERE upper(nombre_proceso) = 'AVANZADO';
