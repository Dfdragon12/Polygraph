package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record HojaVidaRequest(
        @NotNull(message = "Debe aceptar la autorización de datos")
        Boolean autorizacionDatos,

        LocalDate fechaNacimiento,
        String lugarNacimiento,
        String estadoCivil,
        String nivelEducativo,
        String direccion,
        String barrio,
        String estrato,
        String email,
        String celular,

        List<EducacionRequest> educacion,
        List<ExperienciaLaboralRequest> experienciaLaboral,
        List<ReferenciaPersonalRequest> referencias
) {}
