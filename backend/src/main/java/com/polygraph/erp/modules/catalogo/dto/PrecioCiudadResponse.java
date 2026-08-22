package com.polygraph.erp.modules.catalogo.dto;

import com.polygraph.erp.shared.enums.NivelCiudad;

import java.math.BigDecimal;

public record PrecioCiudadResponse(
        Integer     idPrecioCiudad,
        NivelCiudad nivelCiudad,
        BigDecimal  valor
) {}
