package com.polygraph.erp.modules.tienda.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Porcentaje de IVA vigente para cobrar (TP-03). Con el interruptor cobroConIva apagado vale 0: nada cambia respecto a hoy. */
@Service
@RequiredArgsConstructor
public class IvaService {

    private final ParametrosTiendaService parametros;

    @Transactional(readOnly = true)
    public boolean activo() {
        return parametros.activo(ParametrosTiendaService.COBRO_CON_IVA);
    }

    /** 19 (por ejemplo) si el cobro con IVA está encendido; 0 si no. */
    @Transactional(readOnly = true)
    public BigDecimal porcentaje() {
        return activo() ? parametros.decimal("ivaPorcentaje") : BigDecimal.ZERO;
    }
}
