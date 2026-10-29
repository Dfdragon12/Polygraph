package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.utils.FechasLegibles;

import java.time.LocalDate;

/**
 * Los textos de los avisos del semáforo de estado (SE-10), puros para poder comprobarlos sin base de datos. Las fechas van siempre como
 * «23 de septiembre de 2026». Cada método devuelve {título, mensaje}.
 */
public final class TextosAviso {

    private TextosAviso() {}

    /** Al cliente cuando se publica un subproceso: puede descargar ese informe sin esperar al resto; si era el último, el estudio está completo. */
    public static String[] publicado(String subproceso, String evaluado, boolean estudioCompleto, LocalDate fecha) {
        if (estudioCompleto) {
            return new String[] {"Tu estudio está completo",
                    "El estudio de " + evaluado + " quedó completo y publicado el " + FechasLegibles.larga(fecha) + ". Ya puedes descargar todos los informes."};
        }
        return new String[] {subproceso + " publicado",
                "El informe de " + subproceso + " de " + evaluado + " ya está publicado (" + FechasLegibles.larga(fecha)
                        + "). Puedes descargarlo ahora, sin esperar a los demás componentes del estudio."};
    }

    /** Al profesional cuando el programador o el gestor registran que el evaluado avisó que no asistirá. {@code cita}: «la cita de X de Y del … a las …». */
    public static String[] evaluadoNoAsistira(String cita, String detalle) {
        return new String[] {"El evaluado no asistirá",
                "El evaluado avisó que no asistirá a " + cita + (detalle == null || detalle.isBlank() ? "" : " (" + detalle + ")")
                        + ". Sale de tu agenda mientras se reagenda."};
    }
}
