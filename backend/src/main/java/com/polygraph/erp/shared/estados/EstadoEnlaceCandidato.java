package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.utils.FechasLegibles;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PC-03 · UN solo estado del enlace del candidato, con su texto para el cliente, calculado aquí y no pegando frases sueltas en cada pantalla
 * (el Dashboard del cliente decía «aún no ha enviado el enlace» y «lo abrió con su cédula» a la vez). Pura, sin Spring.
 *
 * Orden de decisión:
 * <ol>
 *   <li>El candidato ya envió su información → ENVIADO_POR_CANDIDATO.</li>
 *   <li>Polygraph no lo ha enviado (y nadie lo ha abierto) → NO_ENVIADO.</li>
 *   <li>La fecha de vigencia ya pasó → VENCIDO, siempre.</li>
 *   <li>El candidato lo abrió → ABIERTO (con x de y secciones). Si no → ENVIADO.</li>
 * </ol>
 * El recordatorio automático, cuando existe, se agrega al final del texto.
 */
public final class EstadoEnlaceCandidato {

    private EstadoEnlaceCandidato() {}

    public enum Estado { NO_ENVIADO, ENVIADO, ABIERTO, VENCIDO, ENVIADO_POR_CANDIDATO }

    /**
     * enviadoEn: la PRIMERA vez que Polygraph lo envió; abiertoEn: el primer ingreso con la cédula; enviadoPorCandidatoEn: cuando el candidato envió
     * su información (null si no lo ha hecho); vence: fecha de vigencia del último envío; recordatorioEn: el último recordatorio automático.
     */
    public record Datos(LocalDateTime enviadoEn, LocalDateTime abiertoEn, boolean enviadoPorCandidato, LocalDateTime enviadoPorCandidatoEn,
                        int seccionesHechas, int seccionesTotal, LocalDate vence, LocalDateTime recordatorioEn) {}

    public record Resultado(Estado estado, String texto) {}

    public static Resultado calcular(Datos d, LocalDate hoy) {
        if (d.enviadoPorCandidato()) {
            LocalDate cuando = d.enviadoPorCandidatoEn() == null ? null : d.enviadoPorCandidatoEn().toLocalDate();
            return new Resultado(Estado.ENVIADO_POR_CANDIDATO, cuando == null ? "El candidato ya envió su información."
                    : "El candidato envió su información el " + FechasLegibles.corta(cuando) + ".");
        }
        if (d.enviadoEn() == null && d.abiertoEn() == null) {
            return new Resultado(Estado.NO_ENVIADO, "Polygraph todavía no ha enviado el enlace al candidato.");
        }
        String recordatorio = d.recordatorioEn() == null ? "" : " Recordatorio automático enviado el " + FechasLegibles.corta(d.recordatorioEn().toLocalDate()) + ".";
        if (d.vence() != null && d.vence().isBefore(hoy)) {
            return new Resultado(Estado.VENCIDO, "El enlace venció el " + FechasLegibles.corta(d.vence()) + ". Tu gestor debe reenviarlo." + recordatorio);
        }
        String vigencia = d.vence() == null ? "" : " Vence el " + FechasLegibles.corta(d.vence()) + ".";
        if (d.abiertoEn() != null) {
            return new Resultado(Estado.ABIERTO, "Lo abrió con su cédula y lleva " + d.seccionesHechas() + " de " + d.seccionesTotal() + " secciones." + vigencia + recordatorio);
        }
        return new Resultado(Estado.ENVIADO, "Polygraph le envió el enlace el " + FechasLegibles.corta(d.enviadoEn().toLocalDate()) + ". Todavía no lo ha abierto." + vigencia + recordatorio);
    }
}
