-- Solicitud de servicios (cliente): información extra de las poligrafías y temas a evaluar.
--
-- 1) procesos.formato_solicitud: qué formato adicional se diligencia al solicitar ese proceso
--    (Pre-empleo, Rutina o Específica). NULL = el proceso no pide información extra.
--    Los campos de cada formato viven en el código (modules/solicitudes/formato/FormatoSolicitud).
-- 2) preguntas_poligrafia: catálogo administrable de los temas que el cliente puede pedir evaluar
--    (hasta 4 por solicitud) cuando el servicio es de poligrafía.
-- 3) servicio_preguntas_poligrafia: los temas que eligió el cliente en cada solicitud. Se copia el
--    texto: editar o borrar un tema del catálogo no cambia lo que ya se pidió.
-- 4) servicio_datos_solicitud: lo que el cliente respondió en el formato adicional (clave → valor, JSON).

ALTER TABLE procesos ADD COLUMN formato_solicitud VARCHAR(20);
ALTER TABLE procesos ADD CONSTRAINT ck_procesos_formato_solicitud
    CHECK (formato_solicitud IS NULL OR formato_solicitud IN ('PRE_EMPLEO', 'RUTINA', 'ESPECIFICA'));

UPDATE procesos SET formato_solicitud = 'PRE_EMPLEO' WHERE nombre_proceso ILIKE 'poligraf_a pre empleo';
UPDATE procesos SET formato_solicitud = 'RUTINA'     WHERE nombre_proceso ILIKE 'poligraf_a de rutina';
UPDATE procesos SET formato_solicitud = 'ESPECIFICA' WHERE nombre_proceso ILIKE 'poligraf_a especifica';

CREATE TABLE preguntas_poligrafia (
    id               SERIAL PRIMARY KEY,
    nombre           VARCHAR(150) NOT NULL,
    requiere_detalle BOOLEAN      NOT NULL DEFAULT FALSE,
    orden            INTEGER      NOT NULL DEFAULT 0,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_preguntas_poligrafia_nombre ON preguntas_poligrafia (LOWER(nombre));

INSERT INTO preguntas_poligrafia (nombre, requiere_detalle, orden) VALUES
    ('Robos', FALSE, 10),
    ('Vínculos con grupos criminales', FALSE, 20),
    ('Crímenes cibernéticos (Sabotaje)', FALSE, 30),
    ('Fraude documental', FALSE, 40),
    ('Temas para servicios generales / niñeras', FALSE, 50),
    ('Sobornos', FALSE, 60),
    ('Drogas ilegales / Tráfico de drogas', FALSE, 70),
    ('Beneficios indebidos', FALSE, 80),
    ('Lavado de dinero', FALSE, 90),
    ('Despido de trabajos anteriores', FALSE, 100),
    ('Mintió en su hoja de vida', FALSE, 110),
    ('Mintió en su experiencia laboral', FALSE, 120),
    ('Sabotajes', FALSE, 130),
    ('La que determine POLYGRAPH', FALSE, 140),
    ('Otros ¿Cuál?', TRUE, 150);

CREATE TABLE servicio_preguntas_poligrafia (
    id          BIGSERIAL PRIMARY KEY,
    id_servicio INTEGER      NOT NULL REFERENCES servicios (id_servicio) ON DELETE CASCADE,
    id_pregunta INTEGER      REFERENCES preguntas_poligrafia (id) ON DELETE SET NULL,
    nombre      VARCHAR(150) NOT NULL,
    detalle     TEXT,
    orden       INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_servicio_preguntas_poligrafia_servicio ON servicio_preguntas_poligrafia (id_servicio);

CREATE TABLE servicio_datos_solicitud (
    id_servicio INTEGER     PRIMARY KEY REFERENCES servicios (id_servicio) ON DELETE CASCADE,
    formato     VARCHAR(20) NOT NULL,
    datos       TEXT        NOT NULL
);
