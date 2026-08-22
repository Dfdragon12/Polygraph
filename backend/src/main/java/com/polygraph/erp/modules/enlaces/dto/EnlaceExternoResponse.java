package com.polygraph.erp.modules.enlaces.dto;

import com.polygraph.erp.shared.enums.CategoriaEnlace;

import java.time.LocalDateTime;

public record EnlaceExternoResponse(
        Integer idEnlace,
        CategoriaEnlace categoria,
        String nombreEntidad,
        String url,
        String correoContacto,
        String telefonoContacto,
        String ciudad,
        Boolean requiereLogin,
        String notas,
        Boolean activo,
        LocalDateTime fechaCreacion,
        String creadoPorNombre
) {}
