package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.entity.ExperienciaLaboralEvaluado;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.entity.InactividadLaboralEvaluado;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class HistorialLaboralService {

    private static final int DIAS_UMBRAL = 30;
    private static final int ANOS_HISTORIAL = 4;

    public List<InactividadLaboralEvaluado> calcularInactividades(
            HojaVidaEvaluado hojaVida,
            List<ExperienciaLaboralEvaluado> experiencias) {

        if (experiencias == null || experiencias.isEmpty()) {
            return List.of();
        }

        LocalDate limiteInferior = LocalDate.now().minusYears(ANOS_HISTORIAL);
        List<InactividadLaboralEvaluado> inactividades = new ArrayList<>();

        List<ExperienciaLaboralEvaluado> ordenadas = experiencias.stream()
                .sorted(Comparator.comparing(ExperienciaLaboralEvaluado::getFechaInicio))
                .toList();

        LocalDate finPeriodoAnterior = limiteInferior;

        for (ExperienciaLaboralEvaluado exp : ordenadas) {
            LocalDate inicioExp = exp.getFechaInicio();

            if (inicioExp.isBefore(limiteInferior)) {
                // Empleo empezó antes del límite de 4 años, solo interesa la parte reciente
                LocalDate finExp = obtenerFechaFin(exp);
                if (finExp.isAfter(finPeriodoAnterior)) {
                    finPeriodoAnterior = finExp;
                }
                continue;
            }

            long diasGap = ChronoUnit.DAYS.between(finPeriodoAnterior, inicioExp);
            if (diasGap > DIAS_UMBRAL) {
                inactividades.add(InactividadLaboralEvaluado.builder()
                        .hojaVidaEvaluado(hojaVida)
                        .fechaInicio(finPeriodoAnterior)
                        .fechaFin(inicioExp.minusDays(1))
                        .diasInactivo((int) diasGap)
                        .requiereCuestionario(true)
                        .build());
            }

            LocalDate finExp = obtenerFechaFin(exp);
            if (finExp.isAfter(finPeriodoAnterior)) {
                finPeriodoAnterior = finExp;
            }
        }

        // Brecha desde el último empleo hasta hoy
        long diasFinal = ChronoUnit.DAYS.between(finPeriodoAnterior, LocalDate.now());
        if (diasFinal > DIAS_UMBRAL) {
            inactividades.add(InactividadLaboralEvaluado.builder()
                    .hojaVidaEvaluado(hojaVida)
                    .fechaInicio(finPeriodoAnterior)
                    .fechaFin(LocalDate.now())
                    .diasInactivo((int) diasFinal)
                    .requiereCuestionario(true)
                    .build());
        }

        return inactividades;
    }

    private LocalDate obtenerFechaFin(ExperienciaLaboralEvaluado exp) {
        if (Boolean.TRUE.equals(exp.getLaboraActualmente()) || exp.getFechaFin() == null) {
            return LocalDate.now();
        }
        return exp.getFechaFin();
    }
}
