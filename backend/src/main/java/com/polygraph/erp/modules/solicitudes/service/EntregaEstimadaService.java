package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.EntregaEstimadaResponse;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.DiasHabilesService;
import com.polygraph.erp.shared.service.TiempoServicioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * NS-06: la fecha estimada de entrega que ve el cliente ANTES de enviar. Es el MISMO cálculo con el que se crea la solicitud
 * ({@link TiempoServicioService#datosDeCreacion}: días hábiles de Colombia, festivos, hora de corte, hora de Bogotá), no uno aparte en el navegador.
 */
@Service
@RequiredArgsConstructor
public class EntregaEstimadaService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-CO"));
    private static final int PLAZO_POR_DEFECTO = 5;

    private final ProcesoRepository procesoRepository;
    private final TiempoServicioService tiempoServicioService;
    private final DiasHabilesService diasHabilesService;

    @Transactional(readOnly = true)
    public EntregaEstimadaResponse estimar(List<Integer> idProcesos) {
        if (idProcesos == null || idProcesos.isEmpty()) throw new ApiException("Elige al menos un servicio", HttpStatus.BAD_REQUEST);
        List<Proceso> procesos = procesoRepository.findAllById(idProcesos);
        if (procesos.size() != idProcesos.stream().distinct().count()) throw new ApiException("Algún servicio no existe", HttpStatus.NOT_FOUND);
        return estimarDe(procesos);
    }

    EntregaEstimadaResponse estimarDe(List<Proceso> procesos) {
        int plazo = procesos.stream().mapToInt(p -> p.getDiasHabilesEntrega() != null ? p.getDiasHabilesEntrega() : PLAZO_POR_DEFECTO).max().orElse(PLAZO_POR_DEFECTO);
        var ahora = diasHabilesService.ahora();
        var tiempo = tiempoServicioService.datosDeCreacion(ahora, plazo);
        // Misma regla con la que se abre la pausa al crear la solicitud: solo los servicios que necesitan la hoja de vida del candidato.
        boolean pausa = procesos.stream().anyMatch(p -> !Boolean.FALSE.equals(p.getRequiereHojaVida()));
        boolean despuesDelCorte = !tiempo.recepcionEfectiva().equals(ahora.toLocalDate());
        return new EntregaEstimadaResponse(tiempo.compromiso(), texto(tiempo.compromiso()), plazo, tiempo.recepcionEfectiva(), despuesDelCorte, pausa,
                nota(pausa, despuesDelCorte, tiempo.recepcionEfectiva()));
    }

    static String texto(LocalDate fecha) {
        return DIA.format(fecha);
    }

    private static String nota(boolean pausa, boolean despuesDelCorte, LocalDate recepcion) {
        StringBuilder n = new StringBuilder();
        if (despuesDelCorte) n.append("Tu solicitud cuenta como recibida el ").append(texto(recepcion)).append(". ");
        if (pausa) n.append("El tiempo de entrega corre desde que el evaluado envíe su hoja de vida: cada día hábil que tarde en enviarla mueve la fecha un día hábil.");
        return n.toString().trim();
    }
}
