package com.polygraph.erp.shared.service;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.dto.TiempoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.OrigenPausa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Presenta el estado de tiempo de una solicitud. NO calcula nada: lee lo que guardó SemaforoTiempoService (estado, días, fecha de compromiso
 * ajustada) y arma el texto ("Vencida · 17 días hábiles") para que toda la interfaz diga lo mismo. También fija los datos de una solicitud
 * nueva (recepción efectiva y compromiso).
 */
@Service
@RequiredArgsConstructor
public class TiempoServicioService {

    private final DiasHabilesService diasHabilesService;

    /** null cuando no aplica (cancelada) o aún no se calculó. */
    public TiempoServicio de(Servicio s) {
        EstadoTiempo estado = s.getEstadoTiempo();
        if (estado == null) return null;
        int restantes = valor(s.getDiasRestantesHabiles());
        int retraso = valor(s.getDiasRetrasoHabiles());
        return new TiempoServicio(estado, restantes, retraso, s.getFechaRecepcionEfectiva(), valor(s.getPlazoDiasHabiles()), s.getFechaEntregaEstimada(),
                s.getFechaCompromisoOriginal(), s.getFechaCompromisoAjustada(), valor(s.getDiasPausaHabiles()), s.getPausaOrigen(), s.getCumplimiento(),
                s.getFechaEntregaReal(), etiqueta(estado, restantes, retraso, s.getFechaCompromisoAjustada(), s.getPausaOrigen(), diasHabilesService.hoy()));
    }

    /** El texto del chip. Siempre dice "días hábiles": "A tiempo", "Vence hoy", "Vence mañana", "Vencida · 17 días hábiles", "En pausa · esperando al
     *  candidato", "Entregada a tiempo", "Entregada con 12 días hábiles de retraso". */
    static String etiqueta(EstadoTiempo estado, int restantes, int retraso, LocalDate compromisoAjustado, OrigenPausa pausa, LocalDate hoy) {
        return switch (estado) {
            case A_TIEMPO -> "A tiempo";
            case POR_VENCER -> restantes == 0 ? "Vence hoy"
                    : compromisoAjustado != null && compromisoAjustado.equals(hoy.plusDays(1)) ? "Vence mañana" : "Vence el próximo día hábil";
            case VENCIDA -> retraso == 0 ? "Vencida" : "Vencida · " + dias(retraso);
            case EN_PAUSA -> pausa == OrigenPausa.CLIENTE ? "En pausa · esperando al cliente"
                    : pausa == OrigenPausa.REPROGRAMACION ? "En pausa · cita reprogramada" : "En pausa · esperando al candidato";
            case ENTREGADA_A_TIEMPO -> "Entregada a tiempo";
            case ENTREGADA_TARDE -> "Entregada con " + dias(Math.max(retraso, 1)) + " de retraso";
        };
    }

    private static int valor(Integer n) {
        return n == null ? 0 : n;
    }

    /** Lo que se fija al crear una solicitud: cuándo se considera recibida y a qué fecha vence. */
    public record DatosDeCreacion(LocalDate recepcionEfectiva, int plazoDiasHabiles, LocalDate compromiso) {}

    /** creada: fecha y hora de la solicitud en la zona del negocio. El día de recepción no cuenta en el plazo. */
    public DatosDeCreacion datosDeCreacion(LocalDateTime creada, int plazoDiasHabiles) {
        LocalDate recepcion = diasHabilesService.recepcionEfectiva(creada);
        return new DatosDeCreacion(recepcion, plazoDiasHabiles, diasHabilesService.sumarDiasHabiles(recepcion, plazoDiasHabiles));
    }

    private static String dias(int n) {
        return n + (n == 1 ? " día hábil" : " días hábiles");
    }
}
