package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NotaAjusteResponse(
        Long idNota,
        String tipo,
        String referencia,
        BigDecimal montoTotal,
        String motivo,
        String observaciones,
        LocalDateTime fechaGeneracion,
        String generadaPorNombre
) {}
