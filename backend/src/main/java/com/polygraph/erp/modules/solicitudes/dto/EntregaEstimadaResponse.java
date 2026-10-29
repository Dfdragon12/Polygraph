package com.polygraph.erp.modules.solicitudes.dto;

import java.time.LocalDate;

/** NS-06: lo que Nueva solicitud muestra como fecha estimada. `texto` ya viene escrito ("miércoles, 28 de octubre"); la pantalla no calcula nada. */
public record EntregaEstimadaResponse(
        LocalDate fecha,
        String texto,
        int diasHabiles,
        LocalDate recepcion,
        boolean despuesDelCorte,
        boolean pausaPorHojaDeVida,
        String nota
) {}
