-- Una sección tipo TABLA puede venir de dos orígenes: "manual" (el analista la llena a mano,
-- comportamiento de siempre, origen_fijo = NULL) o "automática" (se llena sola con una lista de
-- la hoja de vida del candidato — referencias, educacion, experiencia_laboral o inactividades —
-- ver CandidatoInfoService.CLAVES_TABLAS_FIJAS). Cuando origen_fijo no es NULL, la sección no
-- tiene columnas propias (la forma de los datos ya está fija) y no aparece en el formulario que
-- diligencia el analista — el valor sale directo de lo que cargó el candidato.
ALTER TABLE formulario_secciones
    ADD COLUMN origen_fijo VARCHAR(30);
