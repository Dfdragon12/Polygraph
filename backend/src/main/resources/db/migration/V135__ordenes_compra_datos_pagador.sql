-- Datos del pagador y de la transacción que ya llegan en el webhook de Wompi (customer_email,
-- customer_data.full_name/phone_number, payment_method.extra.external_identifier) pero no se
-- guardaban en campos propios — solo en el payload crudo de transacciones_pago.
ALTER TABLE "ordenes_compra" ADD COLUMN "nombre_pagador" VARCHAR(150);
ALTER TABLE "ordenes_compra" ADD COLUMN "telefono_pagador" VARCHAR(30);
ALTER TABLE "ordenes_compra" ADD COLUMN "email_pagador" VARCHAR(150);
ALTER TABLE "ordenes_compra" ADD COLUMN "numero_aprobacion" VARCHAR(50);
