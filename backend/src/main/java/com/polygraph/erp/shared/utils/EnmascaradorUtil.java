package com.polygraph.erp.shared.utils;

/** Enmascara datos sensibles (correos, etc.) antes de que lleguen a un log. */
public final class EnmascaradorUtil {

    private EnmascaradorUtil() {
    }

    /** "juan.perez@empresa.com" -> "j***@empresa.com". Nunca imprimir el correo completo en logs. */
    public static String enmascararEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
