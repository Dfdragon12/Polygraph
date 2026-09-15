package com.polygraph.erp.modules.evaluados.dto;

import java.util.List;

/** Perfil editable del candidato para uso interno (analista/poligrafista/visitador/gestor/admin)
 *  — identidad básica de solo lectura + las 4 tablas de su hoja de vida real, editables. */
public record CandidatoHojaVidaResponse(
        Long idCandidato,
        String nombreCompleto,
        String cedula,
        List<TablaFijaEditableResponse> tablas
) {}
