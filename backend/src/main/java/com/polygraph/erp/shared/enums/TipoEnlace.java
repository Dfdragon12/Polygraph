package com.polygraph.erp.shared.enums;

import java.text.Normalizer;
import java.util.Collection;

/**
 * PB-04: qué le pide el enlace al candidato. Sigue siendo UN enlace por candidato, que se abre con su cédula; lo que cambia es qué secciones muestra.
 * El tipo lo decide el sistema según los subprocesos de la solicitud; el gestor no lo elige.
 * <ul>
 *   <li>CORTO: la solicitud tiene ÚNICAMENTE Antecedentes. Pide datos personales, las tres declaraciones judiciales y la autorización de datos (Ley 1581).
 *       No pide educación, experiencia, referencias, las declaraciones financieras ni documentos.</li>
 *   <li>COMPLETO: cualquier otra combinación (estudios de seguridad, visita, poligrafía, Avanzado, Básico Virtual...).</li>
 * </ul>
 */
public enum TipoEnlace {
    CORTO, COMPLETO;

    /** Recibe los nombres de los subprocesos NO cancelados de la solicitud. Sin subprocesos todavía, el enlace es completo (lo más seguro). */
    public static TipoEnlace de(Collection<String> nombresSubprocesosVigentes) {
        if (nombresSubprocesosVigentes == null || nombresSubprocesosVigentes.isEmpty()) return COMPLETO;
        return nombresSubprocesosVigentes.stream().allMatch(TipoEnlace::esAntecedentes) ? CORTO : COMPLETO;
    }

    private static boolean esAntecedentes(String nombre) {
        if (nombre == null) return false;
        String n = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase();
        return n.startsWith("antecedentes");
    }
}
