-- Descripción libre del cobro (visible para el cliente en el checkout / historial de pagos).
ALTER TABLE "pagos_wompi" ADD COLUMN "descripcion" VARCHAR(255);
