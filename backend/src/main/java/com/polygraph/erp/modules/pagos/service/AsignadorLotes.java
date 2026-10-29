package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.pagos.entity.LotePrepago;

import java.util.List;
import java.util.Optional;

/** Regla pura del FIFO (NS-02): una unidad sale del lote más antiguo que todavía tenga. {@code lotes} ya viene del más antiguo al más nuevo. */
public final class AsignadorLotes {

    private AsignadorLotes() {}

    public static Optional<LotePrepago> siguiente(List<LotePrepago> lotes) {
        return lotes.stream().filter(l -> l.restante() > 0).findFirst();
    }
}
