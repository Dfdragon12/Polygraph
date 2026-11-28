package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.OrigenPausa;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Al arrancar completa los datos de tiempo de las solicitudes creadas antes de existir esas columnas (las que no tienen recepción efectiva):
 * recepción efectiva, plazo (deducido de la distancia al compromiso), compromiso original, la pausa de las que siguen en Pendiente esperando la
 * hoja de vida (abierta desde su creación), la entrega real de las publicadas (del historial) y el estado de tiempo. Idempotente: solo toca las
 * que no tienen recepción efectiva. Deja un CSV con lo que cambió en {uploads}/reportes/ y un resumen en el log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TiempoHistoricoInicializador {

    private final ServicioRepository servicioRepository;
    private final HojaVidaEvaluadoRepository hojaVidaRepository;
    private final SemaforoTiempoService semaforoTiempoService;
    private final CalendarioHabilService calendario;
    private final PlatformTransactionManager transacciones;

    @Value("${app.storage.uploads-dir:./uploads}")
    private String directorioUploads;

    /** Va con una transacción explícita: llamar a completar() desde aquí es una llamada dentro de la misma clase y su @Transactional no se aplicaría
     *  (los cambios de las solicitudes se perderían y solo quedarían las pausas, que se guardan por su cuenta). */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> completar());
        } catch (RuntimeException e) {
            log.warn("No se pudieron completar los datos de tiempo de las solicitudes existentes: {}", e.getMessage(), e);
        }
        try {
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> reajustarPorReglasVigentes());
        } catch (RuntimeException e) {
            log.warn("No se pudo reajustar el tiempo de las solicitudes existentes: {}", e.getMessage(), e);
        }
    }

    /**
     * SE-05, cálculo único para lo anterior: aplica las reglas vigentes (la pausa por candidato solo donde la necesitan, la entrega al quedar el
     * estudio completo publicado con la fecha real en que se publicó) a las solicitudes abiertas y a las entregadas sin resultado, y deja en
     * {uploads}/reportes/ un CSV con lo que CAMBIÓ. Idempotente: si no cambia nada, no escribe nada. Devuelve las líneas del reporte
     * (la primera es el encabezado), vacío si nada cambió.
     */
    @Transactional
    public List<String> reajustarPorReglasVigentes() {
        List<Servicio> candidatas = new ArrayList<>(servicioRepository.findAllById(servicioRepository.idsAbiertos(EstadoServicio.CERRADOS)));
        candidatas.addAll(servicioRepository.findByEstadoAndCumplimientoIsNull(EstadoServicio.PUBLICADO));
        List<String> reporte = new ArrayList<>();
        reporte.add("id_servicio;proceso;estado_tiempo_antes;estado_tiempo_despues;compromiso_ajustado_antes;compromiso_ajustado_despues;"
                + "dias_pausa_antes;dias_pausa_despues;dias_retraso_antes;dias_retraso_despues;entrega_real_despues;cumplimiento_despues");
        for (Servicio s : candidatas) {
            var antes = new Object[]{s.getEstadoTiempo(), s.getFechaCompromisoAjustada(), s.getDiasPausaHabiles(), s.getDiasRetrasoHabiles()};
            semaforoTiempoService.recalcular(s);
            var despues = new Object[]{s.getEstadoTiempo(), s.getFechaCompromisoAjustada(), s.getDiasPausaHabiles(), s.getDiasRetrasoHabiles()};
            if (java.util.Arrays.equals(antes, despues)) continue;
            reporte.add(String.join(";", String.valueOf(s.getIdServicio()), s.getProceso() != null ? s.getProceso().getNombreProceso() : "",
                    String.valueOf(antes[0]), String.valueOf(despues[0]), String.valueOf(antes[1]), String.valueOf(despues[1]),
                    String.valueOf(antes[2]), String.valueOf(despues[2]), String.valueOf(antes[3]), String.valueOf(despues[3]),
                    String.valueOf(s.getFechaEntregaReal()), String.valueOf(s.getCumplimiento())));
        }
        if (reporte.size() == 1) return List.of();
        log.info("Reajuste de tiempo (SE-05): {} de {} solicitudes cambiaron", reporte.size() - 1, candidatas.size());
        guardarReporte(reporte, "tiempo-reajuste-");
        return reporte;
    }

    /** Devuelve las líneas del reporte (la primera es el encabezado); vacío si no había nada que completar. */
    @Transactional
    public List<String> completar() {
        List<Servicio> pendientes = servicioRepository.findByFechaRecepcionEfectivaIsNull();
        if (pendientes.isEmpty()) return List.of();
        LocalDate hoy = calendario.hoy();
        List<String> reporte = new ArrayList<>();
        reporte.add("id_servicio;etapa;fecha_compromiso;retraso_calendario_antes;pausa_abierta_creada;fecha_recepcion_efectiva;plazo_dias_habiles;"
                + "estado_tiempo;dias_retraso_habiles;dias_restantes_habiles;dias_pausa_habiles;fecha_compromiso_ajustada;fecha_entrega_real;cumplimiento");
        int pausasCreadas = 0;
        for (Servicio s : pendientes) {
            long retrasoCalendario = s.getEstado() != null && !s.getEstado().cerrado() && s.getFechaEntregaEstimada() != null
                    ? Math.max(0, ChronoUnit.DAYS.between(s.getFechaEntregaEstimada(), hoy)) : 0;
            completarDatosIniciales(s);
            boolean pausa = false;
            if (s.getEstado() == EstadoServicio.PENDIENTE && !hojaDeVidaCompletada(s) && (s.getProceso() == null || !Boolean.FALSE.equals(s.getProceso().getRequiereHojaVida()))) {
                LocalDateTime creada = s.getFechaSolicitud().atTime(s.getHoraSolicitud() != null ? s.getHoraSolicitud() : LocalTime.NOON);
                semaforoTiempoService.abrirPausa(s, OrigenPausa.CANDIDATO, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA, "sistema", creada);
                pausa = true;
                pausasCreadas++;
            }
            if (s.getEstado() == EstadoServicio.PUBLICADO && s.getFechaEntregaReal() == null) s.setFechaEntregaReal(s.getFechaEntregaEstimada());
            semaforoTiempoService.recalcular(s);
            reporte.add(String.join(";", String.valueOf(s.getIdServicio()), String.valueOf(s.getEstado()), String.valueOf(s.getFechaEntregaEstimada()),
                    String.valueOf(retrasoCalendario), String.valueOf(pausa), String.valueOf(s.getFechaRecepcionEfectiva()),
                    String.valueOf(s.getPlazoDiasHabiles()), String.valueOf(s.getEstadoTiempo()), String.valueOf(s.getDiasRetrasoHabiles()),
                    String.valueOf(s.getDiasRestantesHabiles()), String.valueOf(s.getDiasPausaHabiles()), String.valueOf(s.getFechaCompromisoAjustada()),
                    String.valueOf(s.getFechaEntregaReal()), String.valueOf(s.getCumplimiento())));
        }
        log.info("Datos de tiempo completados en {} solicitudes existentes ({} con pausa abierta por espera de hoja de vida)", pendientes.size(), pausasCreadas);
        guardarReporte(reporte, "tiempo-historico-");
        return reporte;
    }

    private void completarDatosIniciales(Servicio s) {
        if (s.getFechaCompromisoOriginal() == null) s.setFechaCompromisoOriginal(s.getFechaEntregaEstimada());
        if (s.getFechaRecepcionEfectiva() == null && s.getFechaSolicitud() != null) {
            LocalDateTime creada = s.getFechaSolicitud().atTime(s.getHoraSolicitud() != null ? s.getHoraSolicitud() : LocalTime.NOON);
            s.setFechaRecepcionEfectiva(calendario.fechaRecepcionEfectiva(creada));
        }
        if (s.getPlazoDiasHabiles() == null && s.getFechaRecepcionEfectiva() != null && s.getFechaCompromisoOriginal() != null) {
            s.setPlazoDiasHabiles(Math.max(0, calendario.diasHabilesEntre(s.getFechaRecepcionEfectiva(), s.getFechaCompromisoOriginal())));
        }
        if (s.getDiasPausaHabiles() == null) s.setDiasPausaHabiles(0);
    }

    private boolean hojaDeVidaCompletada(Servicio s) {
        if (s.getCandidato() == null) return true;       // sin candidato no hay nada que esperar
        return hojaVidaRepository.findByCandidato_IdCandidato(s.getCandidato().getIdCandidato())
                .map(h -> Boolean.TRUE.equals(h.getCompletado())).orElse(false);
    }

    private void guardarReporte(List<String> lineas, String prefijo) {
        try {
            Path dir = Paths.get(directorioUploads, "reportes").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path archivo = dir.resolve(prefijo + calendario.ahora().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv");
            Files.write(archivo, lineas, StandardCharsets.UTF_8);
            log.info("Reporte de lo que cambió: {}", archivo);
        } catch (IOException e) {
            log.warn("No se pudo guardar el reporte CSV del histórico de tiempo: {}", e.getMessage());
        }
    }
}
