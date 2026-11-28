-- Diccionarios de calificación (2026-10-06): el diccionario de cada servicio vive en la plataforma
-- (Admin → Diccionarios), con las mismas hojas del Excel: Secciones, Campos (y columnas de Tablas),
-- Listas, Catálogo y Reglas. Desde aquí se genera la plantilla del formulario y se calcula el
-- servicio — no hay nada propio de un servicio escrito en el código. Se importa / descarga como
-- Excel (mismo formato de docs/antecedentes/Diccionario_marcadores_Antecedentes_FINAL.xlsx).
--
-- El Catálogo sigue siendo codigos_calificacion (V202), por grupo; aquí solo se le agregan las
-- alertas (ALR, sin puntos, solo informativas) y "dónde lo marca el analista".

CREATE TABLE diccionarios (
    id                    SERIAL       PRIMARY KEY,
    id_tipo_progreso      INTEGER      NOT NULL UNIQUE REFERENCES tipos_progreso (id_tipo_progreso),
    grupo                 VARCHAR(30)  NOT NULL UNIQUE,
    nombre                VARCHAR(150) NOT NULL,
    version               INTEGER      NOT NULL DEFAULT 1,
    fecha_actualizacion   TIMESTAMP    NOT NULL DEFAULT now(),
    id_usuario_actualiza  BIGINT       REFERENCES usuarios (id_usuario),
    fecha_plantilla       TIMESTAMP,
    version_plantilla     INTEGER
);

-- Bloques del formulario, en orden. numeros = valores de la columna Sección de la hoja Campos que
-- van en el bloque (separados por coma); si clave es una tabla, el bloque es esa tabla.
CREATE TABLE diccionario_secciones (
    id             SERIAL       PRIMARY KEY,
    id_diccionario INTEGER      NOT NULL REFERENCES diccionarios (id) ON DELETE CASCADE,
    orden          INTEGER      NOT NULL,
    numeros        VARCHAR(100),
    clave          VARCHAR(50)  NOT NULL,
    nombre         VARCHAR(150) NOT NULL
);

-- Campos sueltos (tabla NULL, hoja Campos) y columnas de tablas (hoja Tablas).
CREATE TABLE diccionario_campos (
    id               SERIAL       PRIMARY KEY,
    id_diccionario   INTEGER      NOT NULL REFERENCES diccionarios (id) ON DELETE CASCADE,
    orden            INTEGER      NOT NULL,
    tabla            VARCHAR(50),
    seccion          VARCHAR(30),
    clave            VARCHAR(50)  NOT NULL,
    etiqueta         VARCHAR(150) NOT NULL,
    tipo_dato        VARCHAR(20)  NOT NULL,
    origen           VARCHAR(20)  NOT NULL CHECK (origen IN ('FICHA', 'ANALISTA', 'CALCULADO', 'PLATAFORMA')),
    dato_plataforma  VARCHAR(50),
    imprime          BOOLEAN      NOT NULL DEFAULT TRUE,
    obligatorio      BOOLEAN      NOT NULL DEFAULT FALSE,
    valor_defecto    VARCHAR(200),
    observacion      TEXT
);

CREATE TABLE diccionario_listas (
    id             SERIAL       PRIMARY KEY,
    id_diccionario INTEGER      NOT NULL REFERENCES diccionarios (id) ON DELETE CASCADE,
    campo          VARCHAR(50)  NOT NULL,
    orden          INTEGER      NOT NULL,
    opcion         VARCHAR(200) NOT NULL,
    codigo         VARCHAR(20),
    nota           VARCHAR(300)
);

-- Reglas de cálculo (ver la descripción de la hoja Reglas y diccionarios/motor/MotorDiccionario).
CREATE TABLE diccionario_reglas (
    id             SERIAL       PRIMARY KEY,
    id_diccionario INTEGER      NOT NULL REFERENCES diccionarios (id) ON DELETE CASCADE,
    orden          INTEGER      NOT NULL,
    tipo           VARCHAR(20)  NOT NULL,
    tabla          VARCHAR(50),
    campos         VARCHAR(300),
    valores        TEXT,
    codigo         VARCHAR(20),
    fuente         VARCHAR(300),
    texto          TEXT,
    destino        VARCHAR(50),
    condiciones    TEXT,
    lugar          VARCHAR(200)
);

CREATE INDEX idx_diccionario_secciones ON diccionario_secciones (id_diccionario, orden);
CREATE INDEX idx_diccionario_campos ON diccionario_campos (id_diccionario, orden);
CREATE INDEX idx_diccionario_listas ON diccionario_listas (id_diccionario, campo, orden);
CREATE INDEX idx_diccionario_reglas ON diccionario_reglas (id_diccionario, orden);

ALTER TABLE codigos_calificacion ADD COLUMN es_alerta BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE codigos_calificacion ADD COLUMN donde VARCHAR(200);
ALTER TABLE codigos_calificacion ALTER COLUMN descripcion TYPE VARCHAR(500);
