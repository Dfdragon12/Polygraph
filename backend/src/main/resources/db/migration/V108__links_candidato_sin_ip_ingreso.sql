-- Se elimina el registro de IP del candidato al ingresar al link: es un dato
-- sensible bajo habeas data que no aporta valor operativo al seguimiento del gestor.
ALTER TABLE links_candidato
    DROP COLUMN IF EXISTS ip_ingreso;
