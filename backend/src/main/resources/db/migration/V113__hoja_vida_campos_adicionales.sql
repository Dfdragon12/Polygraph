-- Campos del formulario de evaluado que el candidato ya diligenciaba pero nunca se persistían.
ALTER TABLE hoja_vida_evaluado
    ADD COLUMN IF NOT EXISTS rh varchar(5),
    ADD COLUMN IF NOT EXISTS libreta_militar varchar(50),
    ADD COLUMN IF NOT EXISTS visa varchar(50),
    ADD COLUMN IF NOT EXISTS pasaporte varchar(50),
    ADD COLUMN IF NOT EXISTS fondo_pensiones varchar(100),
    ADD COLUMN IF NOT EXISTS eps varchar(100),
    ADD COLUMN IF NOT EXISTS telefono_fijo varchar(20);
