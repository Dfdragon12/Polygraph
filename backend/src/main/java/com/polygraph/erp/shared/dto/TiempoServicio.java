package com.polygraph.erp.shared.dto;

import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.OrigenPausa;

import java.time.LocalDate;

/**
 * Todo lo del tiempo de una solicitud, leído de lo que guardó SemaforoTiempoService (nada se calcula al leer). Va en los DTOs de solicitud del
 * cliente y del panel interno, así que todas las pantallas dicen lo mismo.
 *
 *  · estadoTiempo: A_TIEMPO, POR_VENCER, VENCIDA, EN_PAUSA, ENTREGADA_A_TIEMPO o ENTREGADA_TARDE (null si está cancelada).
 *  · diasRestantesHabiles: los que faltan para la fecha de compromiso ajustada (solicitudes abiertas); diasRetrasoHabiles: los que lleva de
 *    retraso (vencidas) o con los que se entregó tarde (entregadas).
 *  · fechaRecepcionEfectiva y plazoDiasHabiles: cuándo se considera recibida y el plazo del catálogo (días hábiles) con que se prometió.
 *  · fechaCompromiso: la vigente; fechaCompromisoOriginal: la primera prometida (si difieren, se reprogramó); fechaCompromisoAjustada: la vigente
 *    corrida por las pausas (y medida contra la original si la causa de una reprogramación fue de Polygraph): contra ella se mide el semáforo.
 *  · diasPausaHabiles: días hábiles en que el reloj estuvo detenido; pausaOrigen: quién lo tiene detenido ahora (si está EN_PAUSA).
 *  · cumplimiento y fechaEntregaReal: solo en las entregadas; se congelan al publicar.
 *  · etiqueta: el texto listo para mostrar ("Vencida · 17 días hábiles"), para que toda la interfaz diga lo mismo.
 */
public record TiempoServicio(
        EstadoTiempo estadoTiempo,
        int diasRestantesHabiles,
        int diasRetrasoHabiles,
        LocalDate fechaRecepcionEfectiva,
        int plazoDiasHabiles,
        LocalDate fechaCompromiso,
        LocalDate fechaCompromisoOriginal,
        LocalDate fechaCompromisoAjustada,
        int diasPausaHabiles,
        OrigenPausa pausaOrigen,
        Cumplimiento cumplimiento,
        LocalDate fechaEntregaReal,
        String etiqueta) {}
