package com.polygraph.erp.modules.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoCreditoPospagoResponse(
        Long idMovimiento,
        Integer idServicio,
        String nombreProceso,
        String nombreCandidato,
        String nombreCiudad,
        BigDecimal montoBase,
        BigDecimal montoRecargoCiudad,
        BigDecimal montoIvaBase,
        BigDecimal montoIvaRecargo,
        BigDecimal montoTotal,
        LocalDateTime fechaGenerado
) {}
