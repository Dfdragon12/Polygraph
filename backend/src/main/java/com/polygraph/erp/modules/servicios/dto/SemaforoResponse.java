package com.polygraph.erp.modules.servicios.dto;

import java.util.List;

/**
 * El semáforo de estado de un perfil (SE-04). {@code estados} trae solo los estados que ve ese perfil, en el orden y con los colores de
 * pantalla. {@code unidad}: SERVICIOS (cliente, gestor, administrador) o SUBPROCESOS (analista, poligrafista, visitador, programador).
 * {@code total}: cantidad de servicios (o de subprocesos, según la unidad); la suma de los estados puede superarlo.
 */
public record SemaforoResponse(String unidad, int total, List<EstadoConteo> estados) {

    /** {@code servicios}: servicios distintos con al menos un subproceso en el estado. {@code subprocesos}: cuántos subprocesos hay en él. */
    public record EstadoConteo(String codigo, String nombre, String color, int orden, String textoCliente, int servicios, int subprocesos) {}
}
