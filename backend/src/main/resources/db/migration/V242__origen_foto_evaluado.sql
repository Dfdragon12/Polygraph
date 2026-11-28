-- FA-01 (2026-11-03): la foto del evaluado puede venir de la cámara o de un archivo del dispositivo. Se guarda el origen para que el gestor lo vea en la revisión.
-- Las fotos anteriores solo podían tomarse con la cámara.
ALTER TABLE fotos_evaluado_poligrafia ADD COLUMN IF NOT EXISTS origen VARCHAR(10) NOT NULL DEFAULT 'CAMARA';
ALTER TABLE fotos_evaluado_poligrafia DROP CONSTRAINT IF EXISTS ck_fotos_poligrafia_origen;
ALTER TABLE fotos_evaluado_poligrafia ADD CONSTRAINT ck_fotos_poligrafia_origen CHECK (origen IN ('CAMARA', 'ARCHIVO'));
