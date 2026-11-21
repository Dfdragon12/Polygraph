package com.polygraph.erp.modules.pagos.dto;

import java.time.LocalDate;

public record SaldoServicioResponse(
        Integer idProceso,
        String nombreProceso,
        /** Todas las unidades que puede usar: por sede + nacionales. */
        Integer cantidadDisponible,
        Integer cantidadComprada,
        Integer cantidadConsumida,
        /** TP-08: de las disponibles, cuántas son del paquete nacional (0 si no tiene). */
        Integer disponibleNacional,
        /** TP-09: día en que vence el lote más próximo con unidades por usar (null si nada vence o la vigencia está apagada). */
        LocalDate proximoVencimiento,
        /** Unidades de ese lote que vencen ese día. */
        Integer unidadesQueVencen
) {
    public SaldoServicioResponse(Integer idProceso, String nombreProceso, Integer cantidadDisponible, Integer cantidadComprada, Integer cantidadConsumida) {
        this(idProceso, nombreProceso, cantidadDisponible, cantidadComprada, cantidadConsumida, 0, null, null);
    }

    public SaldoServicioResponse(Integer idProceso, String nombreProceso, Integer cantidadDisponible, Integer cantidadComprada, Integer cantidadConsumida,
                                 Integer disponibleNacional) {
        this(idProceso, nombreProceso, cantidadDisponible, cantidadComprada, cantidadConsumida, disponibleNacional, null, null);
    }

    public SaldoServicioResponse conVencimiento(LocalDate fecha, Integer unidades) {
        return new SaldoServicioResponse(idProceso, nombreProceso, cantidadDisponible, cantidadComprada, cantidadConsumida, disponibleNacional, fecha, unidades);
    }
}
