package com.polygraph.erp.modules.gestor.service;

import java.util.Locale;
import java.util.Map;

/**
 * PC-03 · Cómo se nombra el servicio pedido en el mensaje al candidato: «tu prueba de poligrafía pre empleo», «tu consulta de antecedentes», «tu validación
 * laboral»… Antes el mensaje decía siempre «tu estudio de seguridad», aun para una poligrafía. «Estudio de seguridad» solo sale cuando el servicio se llama así.
 * Pura, sin Spring. Los nombres del catálogo vienen sin tilde («Poligrafia Pre Empleo»): se restauran las palabras conocidas.
 */
final class FraseServicio {

    private FraseServicio() {}

    private static final Map<String, String> TILDES = Map.of(
            "poligrafia", "poligrafía", "validacion", "validación", "verificacion", "verificación",
            "academica", "académica", "analisis", "análisis", "economica", "económica");

    static String de(String nombreProceso) {
        if (nombreProceso == null || nombreProceso.isBlank()) return "tu estudio";
        StringBuilder n = new StringBuilder();
        for (String palabra : nombreProceso.trim().toLowerCase(Locale.forLanguageTag("es-CO")).split("\\s+")) {
            if (n.length() > 0) n.append(' ');
            n.append(TILDES.getOrDefault(palabra, palabra));
        }
        String nombre = n.toString();
        if (nombre.startsWith("poligrafía")) return "tu prueba de " + nombre;
        if (nombre.startsWith("antecedentes")) return "tu consulta de " + nombre;
        return "tu " + nombre;
    }
}
