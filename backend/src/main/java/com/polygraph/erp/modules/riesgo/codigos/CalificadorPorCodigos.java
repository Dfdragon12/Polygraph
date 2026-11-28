package com.polygraph.erp.modules.riesgo.codigos;

import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoCatalogo;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoEncontrado;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.Hallazgo;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.ResultadoCalificacionCodigos;
import com.polygraph.erp.modules.riesgo.motor.ClasificadorNivel;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Califica un subservicio a partir de los códigos que el analista marcó — traducción de la segunda
 * mitad de calificar() en docs/antecedentes/motor_ant.py (desde "hall, vistos, avisos"). Es la parte
 * común a todos los subservicios; cada uno solo aporta cómo recolecta sus códigos (ej.
 * RecolectorAntecedentes). Cálculo puro: sin estado, sin base de datos.
 *
 * Reglas (hoja Cálculos del diccionario):
 *  · Un código que no está en el catálogo activo se ignora ("—", vacíos, ALR).
 *  · Sin conducción, los códigos solo_conduccion se descartan con un aviso (uno por aparición).
 *  · por_proceso suma por cada fila; los demás cuentan una vez (la primera aparición).
 *  · Con algún crítico: 100 y ALTO. Si no: suma con tope 100 y nivel por los umbrales de la
 *    criticidad del cargo — el mismo ClasificadorNivel del motor del estudio.
 */
public final class CalificadorPorCodigos {

    private static final double TOPE = 100;

    private CalificadorPorCodigos() {}

    /** umbrales: los de la criticidad del cargo; solo se usan si no hay críticos. */
    public static ResultadoCalificacionCodigos calificar(List<CodigoEncontrado> encontrados, Map<String, CodigoCatalogo> catalogo,
                                                         boolean conduccion, Umbrales umbrales) {
        return calificar(encontrados, catalogo, conduccion, "{codigo} ignorado: el cargo no implica conducción", umbrales);
    }

    /** condicionCumplida: si es false, los códigos "solo conducción" del catálogo se ignoran con el
     *  aviso avisoCondicion ({codigo} = el código), uno por aparición — como motor_ant.py. */
    public static ResultadoCalificacionCodigos calificar(List<CodigoEncontrado> encontrados, Map<String, CodigoCatalogo> catalogo,
                                                         boolean conduccion, String avisoCondicion, Umbrales umbrales) {
        return calificar(encontrados, catalogo, conduccion, avisoCondicion, umbrales, false);
    }

    /** calificacionUnica: el subservicio tiene UN solo campo de calificación (ej. Validación académica, que
     *  verifica un único estudio). Con un hallazgo la fórmula dice "ACA-03: 45 de 100 posibles" en vez de
     *  "45 = 45 de 100 posibles" (motor_aca.py). */
    public static ResultadoCalificacionCodigos calificar(List<CodigoEncontrado> encontrados, Map<String, CodigoCatalogo> catalogo,
                                                         boolean conduccion, String avisoCondicion, Umbrales umbrales,
                                                         boolean calificacionUnica) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        List<String> avisos = new ArrayList<>();
        for (CodigoEncontrado e : encontrados) {
            CodigoCatalogo c = catalogo.get(e.codigo());
            if (c == null) continue;
            if (c.soloConduccion() && !conduccion) {
                avisos.add(avisoCondicion.replace("{codigo}", c.codigo()));
                continue;
            }
            if (!c.porProceso() && vistos.contains(c.codigo())) continue;
            vistos.add(c.codigo());
            hallazgos.add(new Hallazgo(c.codigo(), c.descripcion(), texto(e.fuente()), texto(e.detalle()),
                    c.critico() ? "Crítico" : String.valueOf(c.puntos())));
        }

        List<String> criticos = hallazgos.stream().filter(h -> "Crítico".equals(h.puntos())).map(Hallazgo::ref).toList();
        List<Integer> numeros = hallazgos.stream().filter(h -> !"Crítico".equals(h.puntos()))
                .map(h -> Integer.valueOf(h.puntos())).toList();
        int bruto = numeros.stream().mapToInt(Integer::intValue).sum();

        double puntaje;
        NivelRiesgo nivel;
        String formula;
        if (!criticos.isEmpty()) {
            puntaje = TOPE;
            nivel = NivelRiesgo.ALTO;
            formula = "Hallazgo crítico (" + String.join(", ", new LinkedHashSet<>(criticos)) + "): nivel ALTO";
        } else {
            puntaje = Math.min(TOPE, bruto);
            nivel = ClasificadorNivel.clasificar(BigDecimal.valueOf(puntaje), umbrales);
            String sumandos = numeros.stream().map(String::valueOf).collect(Collectors.joining(" + "));
            formula = numeros.isEmpty() ? "Sin hallazgos que sumen puntos"
                    : calificacionUnica && numeros.size() == 1
                        ? hallazgos.get(0).ref() + ": " + bruto + " de 100 posibles"
                        : sumandos + (bruto > TOPE ? " = " + bruto + " → 100 (tope máximo)" : " = " + bruto + " de 100 posibles");
        }
        return new ResultadoCalificacionCodigos(List.copyOf(hallazgos), puntaje, nivel, !criticos.isEmpty(),
                criticos, formula, List.copyOf(avisos));
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }
}
