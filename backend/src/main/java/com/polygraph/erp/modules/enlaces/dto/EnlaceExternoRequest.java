package com.polygraph.erp.modules.enlaces.dto;

import com.polygraph.erp.shared.enums.CategoriaEnlace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnlaceExternoRequest(
        @NotNull CategoriaEnlace categoria,
        @NotBlank @Size(max = 200) String nombreEntidad,
        @Size(max = 500) String url,
        @Size(max = 150) String correoContacto,
        @Size(max = 30) String telefonoContacto,
        @Size(max = 100) String ciudad,
        Boolean requiereLogin,
        String notas
) {}
