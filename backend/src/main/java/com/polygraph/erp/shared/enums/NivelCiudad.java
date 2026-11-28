package com.polygraph.erp.shared.enums;

/** Clasifica una ciudad/municipio por su costo logístico de desplazamiento, usado para tarifas
 * diferenciadas en servicios con visita presencial. Una ciudad sin clasificar (null) se cobra
 * como MUNICIPIO_SECUNDARIO por defecto — nunca se asume el nivel más barato. */
public enum NivelCiudad {
    PRINCIPAL,
    INTERMEDIA,
    MUNICIPIO_SECUNDARIO
}
