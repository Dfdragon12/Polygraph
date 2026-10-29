package com.polygraph.erp.shared.enums;

/** Datos del estudio (el servicio y el subproceso) que una columna de plantilla puede mostrar sin
 *  que nadie los escriba — mismo mecanismo que CampoCandidato y CampoCliente: se resuelven en vivo
 *  (CandidatoInfoService.resolverUnoServicio), nunca se guardan como FormularioDato. */
public enum CampoServicio {
    NUMERO_ESTUDIO("N° de estudio"),
    FECHA_EMISION("Fecha de emisión"),
    CLIENTE("Cliente"),
    CARGO("Cargo"),
    PROFESIONAL("Profesional encargado"),
    /** "Sí" si el estudio es OEA (el nombre de su proceso lo dice), "No" si no. */
    ES_OEA("¿Estudio OEA?");

    private final String etiqueta;

    CampoServicio(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
