-- Bitácora de auditoría del módulo de pagos: quién generó cada cobro, cada cambio de estado
-- (creación / webhook / conciliación), IP de origen del webhook y si su checksum fue válido.
-- Solo-inserción — no se actualiza ni se borra ninguna fila.

CREATE TABLE "auditoria_pagos" (
  "id" UUID PRIMARY KEY NOT NULL,
  "referencia" VARCHAR(100) NOT NULL,
  "id_pago" UUID,
  "tipo_evento" VARCHAR(40) NOT NULL,
  "id_generado_por" BIGINT,
  "monto_en_centavos" BIGINT,
  "estado_anterior" VARCHAR(20),
  "estado_nuevo" VARCHAR(20),
  "ip_origen" VARCHAR(45),
  "checksum_valido" BOOLEAN,
  "detalle" TEXT,
  "fecha_evento" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON "auditoria_pagos" ("referencia");
CREATE INDEX ON "auditoria_pagos" ("id_pago");
CREATE INDEX ON "auditoria_pagos" ("fecha_evento");

ALTER TABLE "auditoria_pagos" ADD FOREIGN KEY ("id_pago") REFERENCES "pagos_wompi" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "auditoria_pagos" ADD FOREIGN KEY ("id_generado_por") REFERENCES "usuarios" ("id_usuario") DEFERRABLE INITIALLY IMMEDIATE;
