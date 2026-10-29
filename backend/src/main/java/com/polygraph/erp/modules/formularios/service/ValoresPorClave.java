package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioFila;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Puente entre lo guardado (por idColumna/idFila) y el cálculo propio de un subservicio (por
 *  clave, como en el Word y en docs/antecedentes/motor_ant.py). Lo usan el formulario en pantalla
 *  (FormularioService) y el documento (FormularioDocumentoService / FormularioPlantillaWordService),
 *  así los dos calculan con exactamente lo mismo. */
public final class ValoresPorClave {

    private ValoresPorClave() {}

    /** Lo que diligenció el analista, por clave: secciones activas, columnas activas que llena
     *  una persona; lo que nadie cambió vale su valor por defecto ("—"). Filas en su orden. */
    public static Entrada armar(List<FormularioSeccion> secciones, Map<Integer, String> valoresCampos,
                                Map<Integer, List<FormularioFila>> filasPorSeccion, Map<Integer, Map<Integer, String>> valoresPorFila) {
        Map<String, String> campos = new LinkedHashMap<>();
        Map<String, List<Map<String, String>>> tablas = new LinkedHashMap<>();
        for (FormularioSeccion s : secciones) {
            if (!Boolean.TRUE.equals(s.getActivo()) || s.getOrigenFijo() != null || Boolean.TRUE.equals(s.getCalculada())) continue;
            List<FormularioColumna> columnas = manuales(s);
            if (s.getTipo() == TipoSeccionFormulario.CAMPOS) {
                for (FormularioColumna c : columnas) campos.put(c.getClave(), valor(c, valoresCampos.get(c.getIdColumna())));
            } else if (s.getClave() != null) {
                List<Map<String, String>> filas = new ArrayList<>();
                for (FormularioFila f : filasOrdenadas(s, filasPorSeccion)) {
                    Map<Integer, String> valores = valoresPorFila.getOrDefault(f.getIdFila(), Map.of());
                    Map<String, String> fila = new LinkedHashMap<>();
                    for (FormularioColumna c : columnas) fila.put(c.getClave(), valor(c, valores.get(c.getIdColumna())));
                    filas.add(fila);
                }
                tablas.put(s.getClave(), filas);
            }
        }
        return new Entrada(campos, tablas);
    }

    public record Entrada(Map<String, String> campos, Map<String, List<Map<String, String>>> tablas) {}

    /** Valor calculado de una columna de sección CAMPOS ("" si el cálculo no la trae). */
    public static String campo(CalculoFormulario calculo, FormularioColumna c) {
        return calculo == null || c.getClave() == null ? "" : calculo.campos().getOrDefault(c.getClave(), "");
    }

    /** Valor calculado de una columna en la fila número indice (0, 1, 2…) de una tabla. */
    public static String celda(CalculoFormulario calculo, FormularioSeccion s, int indice, FormularioColumna c) {
        if (calculo == null || s.getClave() == null || c.getClave() == null) return "";
        List<Map<String, String>> filas = calculo.tablas().get(s.getClave());
        if (filas == null || indice >= filas.size()) return "";
        return filas.get(indice).getOrDefault(c.getClave(), "");
    }

    /** Filas completas de una tabla calculada (ej. hallazgos), vacía si el cálculo no la trae. */
    public static List<Map<String, String>> filasCalculadas(CalculoFormulario calculo, FormularioSeccion s) {
        if (calculo == null || s.getClave() == null) return List.of();
        return calculo.tablas().getOrDefault(s.getClave(), List.of());
    }

    public static List<FormularioFila> filasOrdenadas(FormularioSeccion s, Map<Integer, List<FormularioFila>> filasPorSeccion) {
        return filasPorSeccion.getOrDefault(s.getIdSeccion(), List.of()).stream()
                .sorted(Comparator.comparing(FormularioFila::getOrden)).toList();
    }

    private static List<FormularioColumna> manuales(FormularioSeccion s) {
        return s.getColumnas().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActivo()) && !c.esAutomatica() && c.getClave() != null).toList();
    }

    private static String valor(FormularioColumna c, String guardado) {
        if ((guardado == null || guardado.isBlank()) && c.getValorDefecto() != null) return c.getValorDefecto();
        return guardado == null ? "" : guardado;
    }
}
