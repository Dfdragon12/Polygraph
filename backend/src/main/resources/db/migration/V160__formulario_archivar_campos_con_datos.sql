-- Protege las respuestas ya guardadas cuando el administrador edita una plantilla: en vez de
-- borrar y recrear TODA la estructura cada vez (lo que arrastraba en cascada — y borraba sin
-- avisar — las respuestas de formulario_datos), ahora una sección o columna que ya tiene datos
-- guardados se ARCHIVA (activo = false) en vez de eliminarse de verdad. Deja de aparecer para
-- llenar formularios nuevos, pero los datos que ya se habían guardado se conservan y se siguen
-- viendo en los formularios donde ya se habían diligenciado, y en el documento Word generado.
-- Una sección/columna sin ningún dato guardado sí se puede seguir borrando de verdad — no hay
-- nada que proteger.
ALTER TABLE formulario_secciones ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE formulario_columnas ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
