package com.polygraph.erp.modules.riesgo.dto;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.MotivoAjuste;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.ResultadoServicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Riesgo de un caso para la pestaña "Riesgo" del gestor: la recomendación vigente del motor (con
 *  su registro interno), la decisión del gestor y el historial de cálculos. Nada de esto se le
 *  muestra al cliente salvo el nivel decidido y el puntaje final (Fase E, marcadores). */
public record CalificacionRiesgoResponse(
        Integer idServicio,
        String nombreProceso,
        String criticidad,
        String criticidadProceso,
        boolean criticidadAjustada,
        boolean procesoCalifica,
        String motivoProcesoNoCalifica,
        // Recomendación del motor (cálculo vigente); null si todavía no hay ninguno
        String estado,
        BigDecimal puntajePonderado,
        String nivelPorPuntaje,
        BigDecimal puntajeFinal,
        String nivelFinal,
        String tipoAjuste,
        List<MotivoAjuste> motivosAjuste,
        BigDecimal cobertura,
        BigDecimal coberturaMinima,
        boolean requiereRevision,
        List<String> motivosRevision,
        List<ResultadoServicio> subprocesos,
        String versionConfiguracion,
        LocalDateTime fechaCalculo,
        // Decisión: automática (= nivelFinal) salvo que el gestor la cambie
        String nivelDecidido,
        String nivelEfectivo,
        boolean cambiadoPorGestor,
        String justificacionDecision,
        String decididoPorNombre,
        LocalDateTime fechaDecision,
        boolean bloqueado,
        boolean puedeDecidir,
        boolean puedeCambiarCriticidad,
        List<Historial> historial
) {
    public record Historial(Long id, LocalDateTime fechaCalculo, String estado, BigDecimal puntajeFinal,
                            String nivelFinal, String nivelDecidido, String calculadoPorNombre) {}
}
