package com.polygraph.erp.modules.formularios.service;

import java.util.Arrays;
import java.util.List;

/** Las opciones de una columna LISTA se guardan una por línea en formulario_columnas.opciones
 *  (V203). Antes iban separadas por coma y una opción que llevaba coma se partía en dos. Único
 *  lugar que sabe cómo se guardan: nadie más debe hacer split sobre ese texto. */
public final class OpcionesLista {

    private static final String SEPARADOR = "\n";

    private OpcionesLista() {}

    public static List<String> leer(String guardadas) {
        if (guardadas == null || guardadas.isBlank()) return List.of();
        return Arrays.stream(guardadas.split("\\R")).map(String::trim).filter(o -> !o.isBlank()).toList();
    }

    public static String unir(List<String> opciones) {
        return String.join(SEPARADOR, opciones);
    }
}
