package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.tienda.service.CalculadoraVigencia;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Qué lote paga la siguiente unidad (NS-02, TP-07): el que vence primero; los que no vencen se usan al final.
 * Entre iguales, el más antiguo (FIFO). Un lote ya vencido o sin unidades no se toca. Con ningún lote con vencimiento esto es el FIFO de siempre.
 */
public final class AsignadorLotes {

    private AsignadorLotes() {}

    /** Orden: con vencimiento primero (el más próximo antes), sin vencimiento al final; después fecha de compra e id. */
    private static final Comparator<LotePrepago> ORDEN = Comparator
            .comparing((LotePrepago l) -> l.getFechaVencimiento() == null)
            .thenComparing(LotePrepago::getFechaVencimiento, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LotePrepago::getFechaCompra, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LotePrepago::getIdLote, Comparator.nullsLast(Comparator.naturalOrder()));

    public static Optional<LotePrepago> siguiente(List<LotePrepago> lotes, LocalDateTime ahora) {
        return lotes.stream()
                .filter(l -> l.restante() > 0)
                .filter(l -> !CalculadoraVigencia.vencido(l.getFechaVencimiento(), ahora))
                .min(ORDEN);
    }

    /** Sin hora de referencia (pruebas antiguas): nada se considera vencido. */
    public static Optional<LotePrepago> siguiente(List<LotePrepago> lotes) {
        return lotes.stream().filter(l -> l.restante() > 0).min(ORDEN);
    }
}
