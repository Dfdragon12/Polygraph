package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.service.ParametrosTiempoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Las tareas programadas del semáforo de tiempo: el estado cambia solo con el paso de los días, así que todos los días a las 00:05 y a la hora
 *  de corte (la que diga el parámetro hora_corte) se encola un trabajo RECALCULO_TIEMPO. La cola lo ejecuta, lo reintenta y deja el error visible. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemaforoTiempoTareas {

    private final ColaTrabajosService cola;
    private final TrabajoAsincronoRepository trabajoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalendarioHabilService calendario;
    private final ParametrosTiempoService parametros;

    private volatile LocalDate diaDelCorteEjecutado;

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Bogota")
    public void recalculoDiario() {
        encolar("diario 00:05");
    }

    /** Se revisa cada minuto (la hora de corte es un parámetro que el administrador puede cambiar) y corre una vez al día. */
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void recalculoALaHoraDeCorte() {
        LocalTime ahora = calendario.ahora().toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        LocalDate hoy = calendario.hoy();
        if (ahora.equals(parametros.actuales().horaCorte().truncatedTo(ChronoUnit.MINUTES)) && !hoy.equals(diaDelCorteEjecutado)) {
            diaDelCorteEjecutado = hoy;
            encolar("hora de corte " + ahora);
        }
    }

    /** Encola el recálculo, salvo que ya haya uno esperando o en marcha (no tiene sentido apilarlos). Devuelve si lo encoló. */
    public boolean encolar(String motivo) {
        try {
            if (trabajoRepository.existsByTipoAndEstadoIn(TipoTrabajo.RECALCULO_TIEMPO, List.of(EstadoTrabajo.EN_COLA, EstadoTrabajo.PROCESANDO))) {
                log.info("Recálculo de tiempo ({}): ya hay uno en la cola, no se encola otro", motivo);
                return false;
            }
            Usuario sistema = usuarioRepository.findByRolIn(List.of(Rol.ADMIN_POLYGRAPH)).stream().findFirst().orElse(null);
            if (sistema == null) {
                log.warn("Recálculo de tiempo ({}): no hay un administrador al que atribuir el trabajo", motivo);
                return false;
            }
            cola.encolar(TipoTrabajo.RECALCULO_TIEMPO, sistema, Map.of("motivo", motivo));
            return true;
        } catch (RuntimeException e) {
            log.error("No se pudo encolar el recálculo de tiempo ({}): {}", motivo, e.getMessage(), e);
            return false;
        }
    }
}
