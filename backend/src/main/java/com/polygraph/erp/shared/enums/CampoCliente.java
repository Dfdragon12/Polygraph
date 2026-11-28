package com.polygraph.erp.shared.enums;

/** Campos del cliente (la empresa/persona que solicitó el servicio) que se pueden usar como
 *  columna manual en una plantilla — el administrador elige cuál, igual que con CampoCandidato.
 *  El valor real siempre se resuelve en vivo desde la tabla de clientes, nunca se retipea a
 *  mano. */
public enum CampoCliente {
    NIT("NIT"),
    NOMBRE_EMPRESA("Nombre de la empresa"),
    TELEFONO("Teléfono");

    private final String etiqueta;

    CampoCliente(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
