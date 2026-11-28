package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.RegistroCambioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Marca;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Escribe el registro de cambios de estado y de marca de los subprocesos (SE-03). La hora sale del {@link Clock} inyectado y se
 * guarda en America/Bogota: nunca depende de la zona del servidor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroCambiosService {

    /** La zona de negocio. */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final RegistroCambioSubprocesoRepository repository;
    private final Clock reloj;

    /**
     * @param anterior estado antes del cambio (null si no aplica)
     * @param nuevo    estado después; igual al anterior cuando solo cambia una marca (p. ej. reprogramada)
     * @param marca    marca que acompaña al cambio, o null
     * @param motivo   motivo elegido de la lista (o su descripción), o null
     * @param texto    texto adicional de quien actúa, o null
     * @param actor    quien hace el cambio
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistroCambioSubproceso registrar(ServicioSubproceso sp, EstadoSubproceso anterior, EstadoSubproceso nuevo, Marca marca,
                                              String motivo, String texto, Usuario actor) {
        return registrar(sp, sp.getUsuarioAsignado(), anterior, nuevo, marca, motivo, texto, actor);
    }

    /** Con el responsable explícito: al desasignar, el responsable que se quita ya no está en el subproceso. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistroCambioSubproceso registrar(ServicioSubproceso sp, Usuario responsable, EstadoSubproceso anterior, EstadoSubproceso nuevo,
                                              Marca marca, String motivo, String texto, Usuario actor) {
        ZonedDateTime ahora = ZonedDateTime.now(reloj.withZone(ZONA));
        RegistroCambioSubproceso fila = RegistroCambioSubproceso.builder()
                .idServicio(sp.getServicio().getIdServicio())
                .idServicioSubproceso(sp.getId())
                .idTipoProgreso(sp.getTipoProgreso().getIdTipoProgreso())
                .tipoSubproceso(sp.getTipoProgreso().getNombreProgreso())
                .estadoAnterior(anterior != null ? anterior.name() : null)
                .estadoNuevo(nuevo.name())
                .marca(marca != null ? marca.name() : null)
                .motivo(recortar(motivo, 400))
                .textoAdicional(texto != null && !texto.isBlank() ? texto.trim() : null)
                .idResponsable(responsable != null ? responsable.getIdUsuario() : null)
                .idUsuario(actor != null ? actor.getIdUsuario() : null)
                .fechaHora(ahora.toOffsetDateTime())
                .fechaBogota(ahora.toLocalDateTime())
                .origen("EN_VIVO")
                .build();
        log.info("Registro: subproceso {} {} → {}{}", sp.getId(), anterior, nuevo, marca != null ? " [" + marca + "]" : "");
        return repository.save(fila);
    }

    @Transactional(readOnly = true)
    public List<RegistroCambioSubproceso> porServicio(Integer idServicio) {
        return repository.findByIdServicioOrderByFechaHoraAscIdAsc(idServicio);
    }

    @Transactional(readOnly = true)
    public List<RegistroCambioSubproceso> porSubproceso(Long idServicioSubproceso) {
        return repository.findByIdServicioSubprocesoOrderByFechaHoraAscIdAsc(idServicioSubproceso);
    }

    /** La hora de pared de Colombia para un instante: la que se corta por día en los reportes. */
    public static LocalDateTime enBogota(OffsetDateTime instante) {
        return instante.atZoneSameInstant(ZONA).toLocalDateTime();
    }

    private static String recortar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
