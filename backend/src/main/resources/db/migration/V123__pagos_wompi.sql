-- Registro de cobros vía Wompi (Web Checkout). El id de la transacción de Wompi y el estado
-- final del pago se conocen solo por webhook (ver módulo pagos.config.WompiProperties);
-- nunca se almacena número de tarjeta, CVV ni ningún otro dato sensible del medio de pago,
-- solo el nombre del método (CARD, NEQUI, PSE, BANCOLOMBIA_TRANSFER).

CREATE TABLE "pagos_wompi" (
  "id" UUID PRIMARY KEY NOT NULL,
  "referencia" VARCHAR(100) NOT NULL UNIQUE,
  "transaccion_wompi_id" VARCHAR(100),
  "monto_en_centavos" BIGINT NOT NULL,
  "moneda" VARCHAR(3) NOT NULL DEFAULT 'COP',
  "estado" VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  "metodo_pago" VARCHAR(30),
  "id_cliente" INTEGER NOT NULL,
  "id_creado_por" BIGINT NOT NULL,
  "factura_o_concepto_id" BIGINT,
  "fecha_creacion" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "fecha_actualizacion" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "payload_ultimo_evento" TEXT
);

CREATE UNIQUE INDEX "uq_pagos_wompi_referencia" ON "pagos_wompi" ("referencia");
CREATE UNIQUE INDEX "uq_pagos_wompi_transaccion_wompi_id" ON "pagos_wompi" ("transaccion_wompi_id") WHERE "transaccion_wompi_id" IS NOT NULL;

CREATE INDEX ON "pagos_wompi" ("id_cliente");
CREATE INDEX ON "pagos_wompi" ("estado");

ALTER TABLE "pagos_wompi" ADD FOREIGN KEY ("id_cliente") REFERENCES "clientes" ("id_cliente") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "pagos_wompi" ADD FOREIGN KEY ("id_creado_por") REFERENCES "usuarios" ("id_usuario") DEFERRABLE INITIALLY IMMEDIATE;
