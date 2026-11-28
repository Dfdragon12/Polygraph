package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.service.EmailService;
import com.polygraph.erp.modules.servicios.entity.EstadoLink;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PB-04: recordatorio automático. Cada 15 minutos busca los enlaces que Polygraph envió hace 24 h o más y que el candidato no ha terminado (no los abrió o no
 * los envió) y le manda UN recordatorio por correo. Idempotente: el enlace se «reclama» con un UPDATE condicional (ultimo_recordatorio_en IS NULL), así que
 * dos corridas, o dos nodos, nunca mandan dos. No avisa enlaces vencidos, revocados, bloqueados, de servicios cerrados ni ya enviados por el candidato.
 * WhatsApp no se automatiza (no hay proveedor): el recordatorio sale por correo y queda en el seguimiento como «Recordatorio automático».
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordatorioEnlaceJob {

    static final Duration ESPERA = Duration.ofHours(24);

    private final LinkCandidatoRepository linkRepository;
    private final EnvioEnlaceService envioService;
    private final EmailService emailService;
    private final Clock reloj;

    @Scheduled(fixedDelayString = "${app.enlaces.recordatorio-cada-ms:900000}", initialDelay = 120_000)
    public void correr() {
        try {
            int n = recordar(LocalDateTime.now(reloj.withZone(com.polygraph.erp.modules.servicios.service.RegistroCambiosService.ZONA)));
            if (n > 0) log.info("Recordatorios de enlace enviados: {}", n);
        } catch (RuntimeException e) {
            log.error("Falló el recordatorio automático de enlaces: {}", e.getMessage(), e);
        }
    }

    /** Devuelve cuántos recordatorios envió. Separado de @Scheduled para poder probarlo con una hora fija. */
    @Transactional
    public int recordar(LocalDateTime ahora) {
        List<LinkCandidato> candidatos = linkRepository.pendientesDeRecordatorio(ahora.minus(ESPERA), EstadoLink.MAX_INTENTOS, EstadoLink.SERVICIO_CERRADO);
        int enviados = 0;
        for (LinkCandidato l : candidatos) {
            if (linkRepository.reclamarRecordatorio(l.getId(), ahora) == 0) continue;   // otro proceso ya lo tomó
            var servicio = l.getServicio();
            var c = servicio == null ? null : servicio.getCandidato();
            if (c == null || c.getEmailPrincipal() == null || c.getEmailPrincipal().isBlank()) {
                envioService.registrar(l.getId(), "RECORDATORIO", null, "No se pudo enviar: el candidato no tiene correo", null, true);
                continue;
            }
            emailService.enviarRecordatorioLinkEvaluado(c.getEmailPrincipal(), (c.getNombres() + (c.getApellidos() != null ? " " + c.getApellidos() : "")).trim(),
                    l.getToken(), servicio.getProceso() == null ? null : servicio.getProceso().getNombreProceso());
            envioService.registrar(l.getId(), "RECORDATORIO", c.getEmailPrincipal(), "Recordatorio automático a las 24 h", null, true);
            enviados++;
        }
        return enviados;
    }
}
