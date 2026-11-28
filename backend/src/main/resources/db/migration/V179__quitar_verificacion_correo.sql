-- Se quitó la verificación por correo en el registro: las cuentas ahora quedan activas de
-- inmediato (ver AuthService.registrarNatural/registrarJuridica). Esto reactiva las cuentas que
-- se registraron antes de este cambio y se quedaron inactivas esperando un clic de activación
-- que nunca iban a completar.
UPDATE usuarios
SET activo = true, email_verificado = true
WHERE token_activacion IS NOT NULL AND activo = false;

ALTER TABLE usuarios DROP COLUMN token_activacion;
ALTER TABLE usuarios DROP COLUMN token_activacion_expira;
