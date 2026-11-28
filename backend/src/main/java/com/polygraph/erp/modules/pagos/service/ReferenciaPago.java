package com.polygraph.erp.modules.pagos.service;

import java.util.regex.Pattern;

/**
 * Wompi acepta UNA sola transacción por referencia: si el cliente ya llegó al checkout con una referencia
 * (aunque la tarjeta se haya rechazado o haya cerrado la ventana) y se le manda la misma otra vez, Wompi responde
 * «la referencia ya ha sido usada». Por eso cada intento de pago lleva una referencia nueva: la base de la orden o
 * del pago más un sufijo propio del intento. La base identifica siempre el mismo registro nuestro, así que un aviso
 * tardío de un intento anterior se sigue reconociendo.
 */
public final class ReferenciaPago {

    private static final Pattern SUFIJO_INTENTO = Pattern.compile("-R[0-9]+$");

    private ReferenciaPago() {}

    /** Referencia sin el sufijo del intento: la que identifica la orden o el pago. */
    public static String base(String referencia) {
        return referencia == null ? null : SUFIJO_INTENTO.matcher(referencia).replaceFirst("");
    }

    /** Referencia nueva (nunca usada en Wompi) para un intento de pago de esa orden o pago. */
    public static String nuevoIntento(String referencia, long marcaDeTiempo) {
        return base(referencia) + "-R" + marcaDeTiempo;
    }

    public static String nuevoIntento(String referencia) {
        return nuevoIntento(referencia, System.currentTimeMillis());
    }
}
