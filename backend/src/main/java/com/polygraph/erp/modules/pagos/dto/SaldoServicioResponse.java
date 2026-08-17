package com.polygraph.erp.modules.pagos.dto;

public record SaldoServicioResponse(
        Integer idProceso,
        String nombreProceso,
        Integer cantidadDisponible,
        Integer cantidadComprada,
        Integer cantidadConsumida
) {}
