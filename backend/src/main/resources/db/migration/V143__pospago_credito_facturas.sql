CREATE TABLE "facturas_pospago" (
    "id_factura" BIGSERIAL PRIMARY KEY,
    "id_cliente" INTEGER NOT NULL REFERENCES "clientes"("id_cliente"),
    "referencia" VARCHAR(100) NOT NULL UNIQUE,
    "fecha_corte" DATE NOT NULL,
    "fecha_vencimiento" DATE NOT NULL,
    "monto_total" NUMERIC(15,2) NOT NULL,
    "estado" VARCHAR(20) NOT NULL,
    "fecha_generacion" TIMESTAMP NOT NULL,
    "fecha_pago" TIMESTAMP,
    "id_generada_por" BIGINT NOT NULL REFERENCES "usuarios"("id_usuario")
);

CREATE INDEX "idx_facturas_pospago_cliente" ON "facturas_pospago"("id_cliente");
CREATE INDEX "idx_facturas_pospago_estado" ON "facturas_pospago"("estado");

CREATE TABLE "movimientos_credito_pospago" (
    "id_movimiento" BIGSERIAL PRIMARY KEY,
    "id_cliente" INTEGER NOT NULL REFERENCES "clientes"("id_cliente"),
    "id_servicio" INTEGER NOT NULL UNIQUE REFERENCES "servicios"("id_servicio"),
    "monto_base" NUMERIC(15,2) NOT NULL,
    "monto_recargo_ciudad" NUMERIC(15,2) NOT NULL,
    "monto_total" NUMERIC(15,2) NOT NULL,
    "fecha_generado" TIMESTAMP NOT NULL,
    "id_factura" BIGINT REFERENCES "facturas_pospago"("id_factura")
);

CREATE INDEX "idx_movimientos_credito_pospago_cliente" ON "movimientos_credito_pospago"("id_cliente");
CREATE INDEX "idx_movimientos_credito_pospago_factura" ON "movimientos_credito_pospago"("id_factura");

ALTER TABLE "pagos_wompi" ADD COLUMN "tipo_concepto" VARCHAR(20) NOT NULL DEFAULT 'ORDEN_COMPRA';
