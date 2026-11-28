package com.polygraph.erp.shared.service;

import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.ImputableA;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * El semáforo de tiempo: dado el estado de una solicitud, sus fechas, sus pausas y el calendario hábil, dice en qué va frente a su plazo.
 * Función pura: no consulta la base de datos ni lee el reloj (recibe "ahora"). SemaforoTiempoService lo usa y guarda el resultado.
 *
 * Reglas (en días hábiles de Colombia):
 *  · diasPausaHabiles = suma de los días hábiles de cada pausa, desde su día de inicio (inclusive) hasta su día de fin (exclusivo);
 *    una pausa abierta cuenta hasta hoy (exclusivo). Una pausa que empieza y termina el mismo día vale 0.
 *  · fechaCompromisoAjustada = base + diasPausaHabiles días hábiles. La base es el compromiso vigente, salvo que la última reprogramación
 *    sea por causa de Polygraph y el parámetro diga que se mide contra el original.
 *  · Abierta: pausa abierta → EN_PAUSA; hoy posterior al compromiso ajustado → VENCIDA (retraso = días hábiles después de él, hasta hoy
 *    inclusive); faltan 1 día hábil o menos (umbral) → POR_VENCER; si no, A_TIEMPO. Se vence al FINAL del día de compromiso.
 *  · Entregada (PUBLICADO): ENTREGADA_A_TIEMPO si se publicó hasta el compromiso ajustado; si no ENTREGADA_TARDE con su retraso.
 *  · Cancelada: sin estado de tiempo.
 */
public class SemaforoTiempoCalculator {

    /** Un lapso en que el reloj no corrió. fin null = sigue abierta. */
    public record Lapso(LocalDate inicio, LocalDate fin) {}

    /** Lo que el calculador necesita saber de la solicitud. ultimaReprogramacionPor: null si nunca se reprogramó. */
    public record EntradaTiempo(EstadoServicio etapa, LocalDate fechaCompromiso, LocalDate fechaCompromisoOriginal,
                                ImputableA ultimaReprogramacionPor, List<Lapso> pausas, LocalDate fechaEntregaReal) {}

    public record ResultadoTiempo(EstadoTiempo estado, int diasRestantesHabiles, int diasRetrasoHabiles, LocalDate fechaCompromisoAjustada,
                                  int diasPausaHabiles) {}

    private final CalendarioHabilService calendario;
    private final ParametrosTiempo parametros;

    public SemaforoTiempoCalculator(CalendarioHabilService calendario, ParametrosTiempo parametros) {
        this.calendario = calendario;
        this.parametros = parametros;
    }

    public ResultadoTiempo calcular(EntradaTiempo e, ZonedDateTime ahora) {
        LocalDate hoy = ahora.withZoneSameInstant(parametros.zona()).toLocalDate();
        if (e.etapa() == null || e.etapa() == EstadoServicio.CANCELADO || e.fechaCompromiso() == null) {
            return new ResultadoTiempo(null, 0, 0, e == null ? null : e.fechaCompromiso(), 0);
        }
        boolean entregada = e.etapa() == EstadoServicio.PUBLICADO;
        LocalDate fechaPausasHasta = entregada && e.fechaEntregaReal() != null ? e.fechaEntregaReal() : hoy;

        int diasPausa = 0;
        boolean pausaAbierta = false;
        for (Lapso l : e.pausas()) {
            LocalDate fin = l.fin() != null ? l.fin() : fechaPausasHasta;
            if (l.fin() == null) pausaAbierta = true;
            diasPausa += diasDePausa(l.inicio(), fin);
        }

        LocalDate base = usaOriginal(e) ? e.fechaCompromisoOriginal() : e.fechaCompromiso();
        LocalDate ajustada = calendario.sumarDiasHabiles(base, diasPausa);

        if (entregada) {
            LocalDate real = e.fechaEntregaReal() != null ? e.fechaEntregaReal() : hoy;
            int retraso = Math.max(0, calendario.diasHabilesEntre(ajustada, real));
            boolean tarde = real.isAfter(ajustada);
            return new ResultadoTiempo(tarde ? EstadoTiempo.ENTREGADA_TARDE : EstadoTiempo.ENTREGADA_A_TIEMPO, 0, retraso, ajustada, diasPausa);
        }
        if (pausaAbierta) {
            return new ResultadoTiempo(EstadoTiempo.EN_PAUSA, Math.max(0, calendario.diasHabilesEntre(hoy, ajustada)), 0, ajustada, diasPausa);
        }
        if (hoy.isAfter(ajustada)) {
            return new ResultadoTiempo(EstadoTiempo.VENCIDA, 0, calendario.diasHabilesEntre(ajustada, hoy), ajustada, diasPausa);
        }
        int restantes = calendario.diasHabilesEntre(hoy, ajustada);
        EstadoTiempo estado = restantes <= parametros.umbralPorVencerDias() ? EstadoTiempo.POR_VENCER : EstadoTiempo.A_TIEMPO;
        return new ResultadoTiempo(estado, restantes, 0, ajustada, diasPausa);
    }

    /** Días hábiles desde el día de inicio (inclusive) hasta el de fin (exclusivo). */
    int diasDePausa(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) return 0;
        return calendario.diasHabilesEntre(inicio.minusDays(1), fin.minusDays(1));
    }

    private boolean usaOriginal(EntradaTiempo e) {
        return e.fechaCompromisoOriginal() != null && e.ultimaReprogramacionPor() == ImputableA.POLYGRAPH && parametros.polygraphMideContraOriginal();
    }
}
