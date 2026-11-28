package com.polygraph.erp.modules.solicitudes.dto;

import java.util.List;

/** PB-03: emitir el estudio. tipo = PRELIMINAR o FINAL; concepto = la nota del gestor (obligatoria); alertas = para el cliente, cada una con su justificación interna. */
public record EmitirEstudioRequest(String tipo, String concepto, boolean confirmaRevisionManual, List<Alerta> alertas) {
    public record Alerta(String texto, String justificacion) {}
}
