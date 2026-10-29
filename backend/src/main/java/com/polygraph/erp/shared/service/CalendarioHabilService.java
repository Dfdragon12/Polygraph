package com.polygraph.erp.shared.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * El calendario hábil de Colombia: lunes a viernes que no sean festivo. TODA la aritmética de días hábiles de la plataforma pasa por
 * aquí. Las fechas y horas se cuentan en la zona horaria de los parámetros (America/Bogota), nunca en la del servidor.
 */
@Service
public class CalendarioHabilService {

    private final IntFunction<Set<LocalDate>> festivos;
    private final Supplier<ParametrosTiempo> parametros;
    private final Clock reloj;

    @Autowired
    public CalendarioHabilService(FestivoService festivos, ParametrosTiempoService parametros, Clock reloj) {
        this(festivos::activosDelAnio, parametros::actuales, reloj);
    }

    /** Para pruebas: festivos por año, parámetros y reloj vienen de afuera. */
    public CalendarioHabilService(IntFunction<Set<LocalDate>> festivos, Supplier<ParametrosTiempo> parametros, Clock reloj) {
        this.festivos = festivos;
        this.parametros = parametros;
        this.reloj = reloj;
    }

    public ZoneId zona() {
        return parametros.get().zona();
    }

    /** Ahora, en la zona horaria del negocio. */
    public ZonedDateTime ahora() {
        return ZonedDateTime.now(reloj.withZone(zona()));
    }

    /** Hoy, en la zona horaria del negocio. */
    public LocalDate hoy() {
        return ahora().toLocalDate();
    }

    public boolean esDiaHabil(LocalDate fecha) {
        return switch (fecha.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> !festivos.apply(fecha.getYear()).contains(fecha);
        };
    }

    /** El primer día hábil DESPUÉS de la fecha dada. */
    public LocalDate siguienteDiaHabil(LocalDate fecha) {
        LocalDate d = fecha.plusDays(1);
        while (!esDiaHabil(d)) d = d.plusDays(1);
        return d;
    }

    /** El último día hábil que sea la fecha dada o anterior. */
    public LocalDate ultimoDiaHabilHasta(LocalDate fecha) {
        LocalDate d = fecha;
        while (!esDiaHabil(d)) d = d.minusDays(1);
        return d;
    }

    /** Suma n días hábiles. El día de inicio NO cuenta: sumar 5 desde el miércoles 16-sep-2026 da el miércoles 23-sep-2026. */
    public LocalDate sumarDiasHabiles(LocalDate inicio, int n) {
        LocalDate d = inicio;
        for (int contados = 0; contados < n; ) {
            d = d.plusDays(1);
            if (esDiaHabil(d)) contados++;
        }
        return d;
    }

    /** Días hábiles en (desdeExclusivo, hastaInclusivo]. Negativo si hasta es anterior a desde. */
    public int diasHabilesEntre(LocalDate desdeExclusivo, LocalDate hastaInclusivo) {
        if (hastaInclusivo.isBefore(desdeExclusivo)) return -diasHabilesEntre(hastaInclusivo, desdeExclusivo);
        int dias = 0;
        for (LocalDate d = desdeExclusivo.plusDays(1); !d.isAfter(hastaInclusivo); d = d.plusDays(1)) {
            if (esDiaHabil(d)) dias++;
        }
        return dias;
    }

    /** El día en que se considera recibida una solicitud: el mismo día si llegó en día hábil y hasta la hora de corte; si llegó después
     *  (o en día no hábil), el siguiente día hábil. La hora se mira en la zona horaria del negocio. */
    public LocalDate fechaRecepcionEfectiva(ZonedDateTime creada) {
        ZonedDateTime local = creada.withZoneSameInstant(zona());
        LocalDate fecha = local.toLocalDate();
        if (esDiaHabil(fecha) && !local.toLocalTime().isAfter(parametros.get().horaCorte())) return fecha;
        return siguienteDiaHabil(fecha);
    }

    /** Lo mismo para una fecha y hora que ya están en la zona del negocio (como las que guarda la plataforma). */
    public LocalDate fechaRecepcionEfectiva(LocalDateTime creadaLocal) {
        return fechaRecepcionEfectiva(creadaLocal.atZone(zona()));
    }
}
