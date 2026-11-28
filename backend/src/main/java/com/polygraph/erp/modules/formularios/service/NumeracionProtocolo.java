package com.polygraph.erp.modules.formularios.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VD-03 · Numeración continua del protocolo de seguridad de la visita domiciliaria. Las claves internas (ps_1 … ps_17) NO cambian y las reglas del motor
 * y lo que pasa a otros servicios siguen apuntando a ellas; solo cambia el número que se MUESTRA. Con la pregunta 10 (sindical) apagada se numeran 1 a 16
 * sin saltos; encendida, 1 a 17. La pantalla ({@code frontend/.../fasesVisita.js}, {@code numerosProtocolo}) y el Word usan este mismo orden.
 *
 * Pura: sin Spring ni base de datos.
 */
public final class NumeracionProtocolo {

    /** La pregunta 10 (sindical) está apagada por decisión jurídica: {@code ps_sindical} va vacía en el Word (FormularioPlantillaWordService). */
    public static final boolean SINDICAL_ACTIVA = false;

    /** Orden de las claves; ps_10 va en su posición y solo se numera si está encendida. */
    private static final int[] PREGUNTAS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    /** Las que se comparan con los servicios laboral, financiero y de antecedentes (el texto del Word las nombra). */
    private static final int[] COMPARADAS = {1, 2, 3, 13, 14};

    private NumeracionProtocolo() {}

    /** clave de la pregunta (1..17) → número que se muestra; la 10 apagada no tiene número. */
    public static Map<Integer, Integer> numeros(boolean sindical) {
        Map<Integer, Integer> r = new LinkedHashMap<>();
        int n = 0;
        for (int p : PREGUNTAS) {
            if (p == 10 && !sindical) continue;
            r.put(p, ++n);
        }
        return r;
    }

    /** Marcadores del Word: {@code ps_N_num} (número de la pregunta N, vacío si no se muestra) y {@code ps_comparadas} ("1, 2, 3, 12 y 13"). */
    public static Map<String, Object> marcadores(boolean sindical) {
        Map<Integer, Integer> num = numeros(sindical);
        Map<String, Object> m = new LinkedHashMap<>();
        for (int p : PREGUNTAS) m.put("ps_" + p + "_num", num.containsKey(p) ? String.valueOf(num.get(p)) : "");
        List<String> comparadas = new ArrayList<>();
        for (int p : COMPARADAS) comparadas.add(String.valueOf(num.get(p)));
        String ultimo = comparadas.remove(comparadas.size() - 1);
        m.put("ps_comparadas", String.join(", ", comparadas) + " y " + ultimo);
        return m;
    }
}
