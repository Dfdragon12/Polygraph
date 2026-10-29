package com.polygraph.erp.modules.evaluados.dto;

import com.polygraph.erp.shared.enums.FranjaHoraria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** El candidato dice cuándo tiene tiempo: 1 a 3 días con franja, y una nota opcional. */
public record SugerenciaDisponibilidadRequest(
        @NotEmpty(message = "Elige al menos un día")
        @Size(max = 3, message = "Puedes proponer hasta 3 días")
        List<@Valid Opcion> opciones,
        @Size(max = 500, message = "La nota puede tener hasta 500 caracteres")
        String comentario
) {
    public record Opcion(
            @NotNull(message = "Falta la fecha") LocalDate fecha,
            @NotNull(message = "Falta la franja") FranjaHoraria franja
    ) {}
}
