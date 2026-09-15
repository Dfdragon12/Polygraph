package com.polygraph.erp.shared.enums;

/** Campos del candidato que se pueden mostrar en el encabezado del formulario/documento — el
 *  administrador elige cuáles (y en qué orden) desde la plantilla, una sola vez por tipo de
 *  subproceso (ver FormularioPlantillaConfig.camposCandidato). El valor real siempre se resuelve
 *  en vivo desde la tabla de candidatos, nunca se retipea a mano. */
public enum CampoCandidato {
    NOMBRE_COMPLETO("Nombre completo"),
    NOMBRES("Nombres"),
    APELLIDOS("Apellidos"),
    CEDULA("N° de documento"),
    TIPO_DOCUMENTO("Tipo de documento"),
    FECHA_NACIMIENTO("Fecha de nacimiento"),
    LUGAR_NACIMIENTO("Lugar de nacimiento"),
    FECHA_EXPEDICION("Fecha de expedición"),
    CIUDAD_NACIMIENTO("Ciudad de nacimiento"),
    CIUDAD_RESIDENCIA("Ciudad de residencia"),
    DIRECCION_RESIDENCIA("Dirección de residencia"),
    BARRIO_RESIDENCIA("Barrio"),
    TELEFONO_FIJO("Teléfono fijo"),
    CELULAR("Celular"),
    EMAIL_PRINCIPAL("Correo electrónico"),
    SECTOR("Sector"),
    ESTRATO("Estrato"),
    LIBRETA_MILITAR("Libreta militar"),
    RUT("RUT"),
    ESTADO_CIVIL("Estado civil"),
    NIVEL_EDUCATIVO("Nivel educativo"),
    RH("RH"),
    VISA("Visa"),
    PASAPORTE("Pasaporte"),
    FONDO_PENSIONES("Fondo de pensiones"),
    EPS("EPS");

    private final String etiqueta;

    CampoCandidato(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
