package com.polygraph.erp.modules.solicitudes.validacion;

import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.shared.enums.CriticidadCargo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * NS-07: los datos del evaluado que toda solicitud necesita para enviarle el link y calcular el riesgo. Pura (sin Spring): la usa el envío desde
 * la pantalla y la reutilizará la carga masiva (NS-12), así la regla vive en un solo lugar. Devuelve un mensaje por campo.
 */
public final class DatosEvaluadoValidador {

    private static final Pattern CEDULA = Pattern.compile("[0-9]{5,15}");
    private static final Pattern CELULAR = Pattern.compile("3[0-9]{9}");
    private static final Pattern EMAIL = Pattern.compile("[^@ 	]+@[^@ 	]+[.][^@ 	]{2,}");

    private DatosEvaluadoValidador() {}

    /** campo → mensaje, en el orden del formulario. Vacío si todo está bien. */
    public static Map<String, String> errores(SolicitudRequest r) {
        return errores(r, false);
    }

    /** requiereDireccion: algún servicio elegido incluye una visita (NS-08) y entonces la dirección de la visita es obligatoria. */
    public static Map<String, String> errores(SolicitudRequest r, boolean requiereDireccion) {
        Map<String, String> e = new LinkedHashMap<>();
        String cedula = limpio(r.cedula());
        if (cedula.isEmpty()) e.put("cedula", "La cédula es obligatoria");
        else if (!CEDULA.matcher(cedula).matches()) e.put("cedula", "La cédula debe ser numérica (solo dígitos, sin puntos ni espacios)");
        if (limpio(r.nombres()).isEmpty()) e.put("nombres", "Los nombres son obligatorios");
        if (limpio(r.apellidos()).isEmpty()) e.put("apellidos", "Los apellidos son obligatorios");
        if (limpio(r.cargo()).isEmpty()) e.put("cargo", "El cargo es obligatorio");
        if (limpio(r.criticidadCargo()).isEmpty()) e.put("criticidadCargo", "La criticidad del cargo es obligatoria");
        else if (criticidad(r.criticidadCargo()) == null) e.put("criticidadCargo", "La criticidad del cargo debe ser Estándar, Sensible o Crítico");
        String celular = limpio(r.celular());
        if (celular.isEmpty()) e.put("celular", "El celular es obligatorio");
        else if (!CELULAR.matcher(celular).matches()) e.put("celular", "El celular debe tener 10 dígitos y empezar por 3");
        String email = limpio(r.email());
        if (email.isEmpty()) e.put("email", "El correo es obligatorio");
        else if (!EMAIL.matcher(email).matches()) e.put("email", "El correo no tiene un formato válido");
        if (r.idCiudad() == null) e.put("idCiudad", "La ciudad es obligatoria");
        if (requiereDireccion) {
            String direccion = limpio(r.direccionVisita());
            if (direccion.isEmpty()) e.put("direccionVisita", "La dirección de la visita es obligatoria");
            else if (direccion.length() < 6) e.put("direccionVisita", "La dirección de la visita está incompleta (calle o carrera, número y barrio)");
        }
        return e;
    }

    /** ESTANDAR | SENSIBLE | CRITICO (sin importar mayúsculas ni tildes); null si no es ninguno. */
    public static CriticidadCargo criticidad(String valor) {
        String v = limpio(valor).toUpperCase().replace("Á", "A").replace("É", "E").replace("Í", "I");
        try {
            return v.isEmpty() ? null : CriticidadCargo.valueOf(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Un solo texto con todos los campos que fallan: "cedula: …; email: …". */
    public static String mensaje(Map<String, String> errores) {
        return "Revisa los datos del evaluado — " + String.join("; ", errores.values()) + ".";
    }

    private static String limpio(String s) {
        return s == null ? "" : s.trim();
    }
}
