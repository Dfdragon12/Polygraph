package com.polygraph.erp.shared.enums;

/** Criticidad del cargo del evaluado — define qué umbrales de riesgo se aplican (tabla
 *  umbrales_riesgo). Más crítico = umbrales más bajos (menos puntos para llegar a MEDIO/ALTO). */
public enum CriticidadCargo {
    ESTANDAR,
    SENSIBLE,
    CRITICO
}
