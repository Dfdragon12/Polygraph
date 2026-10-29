package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.modules.catalogo.entity.ListaHojaVida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ItemHojaVidaRequest(
        @NotNull(message = "La lista es obligatoria")
        ListaHojaVida lista,

        @NotBlank(message = "El texto es obligatorio")
        @Size(max = 500, message = "El texto admite máximo 500 caracteres")
        String nombre,

        @Size(max = 2000, message = "La explicación admite máximo 2.000 caracteres")
        String descripcion,

        // null al crear = va al final de su lista
        Integer orden,

        Boolean activo
) {}
