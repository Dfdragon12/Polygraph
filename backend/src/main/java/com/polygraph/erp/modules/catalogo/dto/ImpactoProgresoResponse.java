package com.polygraph.erp.modules.catalogo.dto;

import java.util.List;

public record ImpactoProgresoResponse(
        List<String> procesosAfectados,
        long total
) {}
