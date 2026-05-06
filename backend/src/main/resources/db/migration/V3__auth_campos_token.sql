ALTER TABLE "usuarios"
    ADD COLUMN "email_verificado"        BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN "token_activacion"        VARCHAR(200),
    ADD COLUMN "token_activacion_expira" TIMESTAMP,
    ADD COLUMN "token_reset_password"    VARCHAR(200),
    ADD COLUMN "token_reset_expira"      TIMESTAMP;

CREATE INDEX ON "usuarios" ("token_activacion")     WHERE "token_activacion" IS NOT NULL;
CREATE INDEX ON "usuarios" ("token_reset_password") WHERE "token_reset_password" IS NOT NULL;
