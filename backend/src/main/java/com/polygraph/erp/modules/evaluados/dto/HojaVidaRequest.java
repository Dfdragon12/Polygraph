package com.polygraph.erp.modules.evaluados.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record HojaVidaRequest(
        @NotNull(message = "Debe aceptar la autorización de datos")
        Boolean autorizacionDatos,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        LocalDate fechaNacimiento,

        String lugarNacimiento,

        @NotNull(message = "La ciudad de nacimiento es obligatoria")
        Integer idCiudadNacimiento,

        @NotNull(message = "La ciudad de residencia es obligatoria")
        Integer idCiudadResidencia,

        @NotBlank(message = "El estado civil es obligatorio")
        String estadoCivil,

        String nivelEducativo,

        @NotBlank(message = "La dirección de residencia es obligatoria")
        String direccion,

        @NotBlank(message = "El barrio de residencia es obligatorio")
        String barrio,

        @NotBlank(message = "El estrato es obligatorio")
        String estrato,

        @NotBlank(message = "El email es obligatorio")
        String email,

        @NotBlank(message = "El celular es obligatorio")
        String celular,

        @NotBlank(message = "El RH es obligatorio")
        String rh,

        String libretaMilitar,
        String visa,
        String pasaporte,
        String fondoPensiones,
        String eps,
        String telefonoFijo,

        @Valid @NotEmpty(message = "Debe registrar al menos un estudio")
        List<EducacionRequest> educacion,

        @Valid @NotEmpty(message = "Debe registrar al menos una experiencia laboral")
        List<ExperienciaLaboralRequest> experienciaLaboral,

        @Valid @Size(min = 2, message = "Se requieren mínimo 2 referencias personales")
        List<ReferenciaPersonalRequest> referencias,

        // Clave "fechaInicio_fechaFin" (yyyy-MM-dd) -> texto de justificación del tiempo muerto laboral
        Map<String, String> justificacionesInactividad,

        Integer pasoActual
) {}
