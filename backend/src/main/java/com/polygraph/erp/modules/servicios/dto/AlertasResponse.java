package com.polygraph.erp.modules.servicios.dto;

/**
 * Las alertas que van aparte del semáforo (SE-09). Una que no aplica al perfil viene en null y la pantalla no la dibuja.
 * sinAsignar y citasDeHoy son subprocesos; vencidas y paraHoy, servicios; porConfirmar, citas; novedadesAbiertas, novedades.
 */
public record AlertasResponse(Long sinAsignar, Long novedadesAbiertas, Long citasDeHoy, Long vencidas, Long paraHoy, Long porConfirmar) {}
