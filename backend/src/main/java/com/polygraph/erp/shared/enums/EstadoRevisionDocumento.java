package com.polygraph.erp.shared.enums;

/** Resultado de la revisión interna de un documento que cargó el candidato (V197/V199).
 *  Todo lo que no es APROBADO ni PENDIENTE es una devolución: el candidato ve el motivo y el
 *  documento queda bloqueado hasta que cargue uno nuevo (que vuelve a nacer PENDIENTE). */
public enum EstadoRevisionDocumento {
    PENDIENTE("Por revisar"),
    APROBADO("Aprobado"),
    DENEGADO("Denegado"),
    ILEGIBLE("Ilegible"),
    VENCIDO("Vencido"),
    INCOMPLETO("Incompleto"),
    NO_CORRESPONDE("No corresponde");

    private final String etiqueta;

    EstadoRevisionDocumento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean esDevolucion() {
        return this != PENDIENTE && this != APROBADO;
    }

    /** Clave del tipo de novedad automática para esta devolución (V199). */
    public String claveTipoNovedad() {
        return "DOCUMENTO_" + name();
    }
}
