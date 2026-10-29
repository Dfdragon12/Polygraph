-- Evidencia de cada aceptación de textos legales (términos, privacidad, marketing, autorización del
-- candidato): quién, cuál texto (versión), cuándo y desde dónde (IP solo como hash, minimización).
-- Es un registro histórico: no se actualiza ni se borra; revocar es una fila nueva con aceptado = false.
CREATE TABLE aceptaciones_legales (
    id             BIGSERIAL PRIMARY KEY,
    id_usuario     BIGINT,
    email          VARCHAR(150),
    referencia     VARCHAR(100),
    tipo           VARCHAR(40)  NOT NULL,
    version_texto  VARCHAR(20)  NOT NULL,
    aceptado       BOOLEAN      NOT NULL,
    fecha          TIMESTAMP    NOT NULL DEFAULT NOW(),
    ip_hash        VARCHAR(64),
    CONSTRAINT ck_aceptacion_tipo CHECK (tipo IN
        ('TERMINOS', 'PRIVACIDAD', 'MARKETING', 'AUTORIZACION_CANDIDATO'))
);

CREATE INDEX ix_aceptaciones_usuario ON aceptaciones_legales (id_usuario);
CREATE INDEX ix_aceptaciones_email ON aceptaciones_legales (email);
