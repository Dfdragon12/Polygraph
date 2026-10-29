package com.polygraph.erp.modules.riesgo.codigos;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Tipos de la calificación de UN subservicio por códigos de catálogo (Antecedentes legales hoy;
 *  Validaciones, Financiero, Visita y Poligrafía después — docs/antecedentes/). Inmutables y sin
 *  Spring ni JPA, como el motor del estudio (ModeloRiesgo). */
public final class ModeloCodigos {

    private ModeloCodigos() {}

    /** Un código del catálogo (codigos_calificacion). puntos null = crítico. */
    public record CodigoCatalogo(String codigo, String descripcion, Integer puntos, boolean critico,
                                 boolean porProceso, boolean soloConduccion) {}

    /** Un código que el analista marcó en alguna sección, con de dónde salió. */
    public record CodigoEncontrado(String codigo, String fuente, String detalle) {}

    /** Una fila de la tabla de hallazgos (h_ref, h_descripcion, h_fuente, h_detalle, h_puntos).
     *  puntos: el número como texto ("30") o "Crítico". */
    public record Hallazgo(String ref, String descripcion, String fuente, String detalle, String puntos) {}

    /** Resultado del subservicio. criticos: los códigos críticos en el orden de la tabla (un
     *  código por proceso puede repetirse, igual que en motor_ant.py). avisos: internos, no se
     *  imprimen. */
    public record ResultadoCalificacionCodigos(
            List<Hallazgo> hallazgos,
            double puntaje,
            NivelRiesgo nivel,
            boolean critico,
            List<String> criticos,
            String formula,
            List<String> avisos) {

        /** 60.0 → "60,0": un decimal con coma (mismo formato que ${calificacion['puntaje']}). */
        public String puntajeTexto() {
            return formatoPuntaje(puntaje);
        }
    }

    /** Los hallazgos de un subservicio, como los recibe el motor del estudio: el motor los suma con
     *  su mismo tope de 100 y su misma regla de crítico, así que el puntaje del subservicio en el
     *  estudio es el mismo que dio su cálculo propio (Prompt 5 de docs/antecedentes). */
    public static List<com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Hallazgo> paraMotorEstudio(ResultadoCalificacionCodigos r) {
        if (r == null) return List.of();
        return r.hallazgos().stream()
                .map(h -> new com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Hallazgo(
                        h.ref() + " · " + h.descripcion() + " (" + h.fuente() + ")",
                        "Crítico".equals(h.puntos()) ? BigDecimal.ZERO : new BigDecimal(h.puntos()),
                        "Crítico".equals(h.puntos())))
                .toList();
    }

    public static String formatoPuntaje(double puntaje) {
        return BigDecimal.valueOf(puntaje).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /**
     * Lo que el cálculo de un subservicio deja en el formulario y en el Word (columnas calculadas).
     *  · campos: clave → valor de las columnas calculadas de secciones CAMPOS (srv_puntaje, sem_*...).
     *  · tablas: clave de tabla → filas. Una tabla calculada (ej. hallazgos) trae sus filas completas;
     *    una tabla del analista trae, en el MISMO orden de sus filas, solo las columnas calculadas
     *    de cada fila (ej. p_numero).
     *  · resultado: la calificación, null si no se pudo calcular.
     *  · aviso: por qué no se calculó (ej. "Falta la criticidad del cargo."), null si se calculó.
     *  · criticidad: la criticidad del cargo que eligió el analista en este formulario (null si no
     *    la eligió) — el estudio usa la más exigente de sus subservicios (decisión 2026-10-05).
     *  · avisos: avisos para el analista que NO bloquean (ej. "Este código no suma porque…").
     */
    public record CalculoFormulario(
            java.util.Map<String, String> campos,
            java.util.Map<String, List<java.util.Map<String, String>>> tablas,
            ResultadoCalificacionCodigos resultado,
            String aviso,
            com.polygraph.erp.shared.enums.CriticidadCargo criticidad,
            List<AvisoFormulario> avisos) {}

    /** Aviso para quien diligencia: el texto y dónde está lo que lo causa ("Sección 11, proceso 2"). */
    public record AvisoFormulario(String texto, String lugar) {}
}
