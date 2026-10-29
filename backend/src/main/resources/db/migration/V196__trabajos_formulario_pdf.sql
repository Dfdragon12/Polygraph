-- Nuevo tipo de trabajo: el "Descargar PDF" del formulario (roles internos) también pasa por la
-- cola, igual que el informe del cliente — ver FormularioPdfProcesador.
ALTER TABLE trabajos_asincronos DROP CONSTRAINT IF EXISTS trabajos_asincronos_tipo_check;
ALTER TABLE trabajos_asincronos ADD CONSTRAINT trabajos_asincronos_tipo_check
    CHECK (tipo IN ('CARGA_MASIVA', 'INFORME_PDF', 'FORMULARIO_PDF'));
