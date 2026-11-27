package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PC-02 · Regla única de «la conclusión del ejecutor» (pura, sin Spring):
 * <ul>
 *   <li>qué campo de la plantilla es la conclusión en cada formato (el que el ejecutor ya diligencia; no se le pide escribirla dos veces);</li>
 *   <li>que esa conclusión nunca diga «Favorable» ni «Desfavorable»: el resultado lo da el nivel Bajo, Medio o Alto.</li>
 * </ul>
 * La ventana Publicar, el envío a revisión y los formularios leen de aquí, así que no hay dos listas que se desalineen.
 * Un formato que no tiene ninguno de estos campos usa el cuadro genérico «Conclusión» del último paso (servicio_subprocesos.conclusion).
 */
public final class ConclusionFormato {

    private ConclusionFormato() {}

    /** {clave de la sección, clave de la columna}. Antecedentes, académica, laboral, personal y financiero: «Concepto del analista»; poligrafía: «Conclusión»; visita: «Conclusión» (V277). */
    private static final List<String[]> CAMPOS = List.of(
            new String[] {"concepto_analista", "concepto"},
            new String[] {"firma", "pol_conclusion"},
            new String[] {"concepto_final", "cf_conclusion"});

    public static final String AYUDA = "No escribas Favorable o Desfavorable: el resultado lo da el nivel Bajo, Medio o Alto.";
    public static final String AVISO_FAVORABLE = "La conclusión no debe decir «Favorable» ni «Desfavorable»: el resultado lo da el nivel Bajo, Medio o Alto. Quita esa palabra para continuar.";

    private static final Pattern FAVORABLE = Pattern.compile("(?<![\\p{L}])(des)?favorables?(?![\\p{L}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** ¿Esta columna de esta sección es la conclusión del formato? */
    public static boolean esCampo(String claveSeccion, String claveColumna) {
        if (claveSeccion == null || claveColumna == null) return false;
        return CAMPOS.stream().anyMatch(p -> p[0].equals(claveSeccion) && p[1].equals(claveColumna));
    }

    /** La columna de conclusión de una plantilla (secciones y columnas activas), si la tiene. */
    public static Optional<FormularioColumna> columnaDe(Collection<FormularioSeccion> secciones) {
        for (FormularioSeccion s : secciones) {
            if (!Boolean.TRUE.equals(s.getActivo())) continue;
            for (FormularioColumna c : s.getColumnas()) {
                if (Boolean.TRUE.equals(c.getActivo()) && esCampo(s.getClave(), c.getClave())) return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** ¿El texto dice «favorable» o «desfavorable» (sin importar mayúsculas)? «Favorablemente» no cuenta: es otra palabra. */
    public static boolean contieneFavorable(String texto) {
        return texto != null && FAVORABLE.matcher(texto).find();
    }
}
