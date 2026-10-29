package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import com.polygraph.erp.shared.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PortalCandidatoService — estado de la cita que ve el candidato")
class PortalCandidatoServiceEstadoCitaTest {

    private static ServicioSubproceso cita(EstadoSubproceso estado, LocalDateTime fecha, EstadoConfirmacionAsistencia conf) {
        return ServicioSubproceso.builder()
                .tipoProgreso(TipoProgreso.builder().nombreProgreso("Poligrafía").rolResponsable(Rol.POLIGRAFISTA).build())
                .estado(estado).fechaProgramada(fecha).estadoConfirmacion(conf).build();
    }

    private static final LocalDateTime MANANA = LocalDateTime.now().plusDays(1);

    @Test
    @DisplayName("Sin fecha: POR_PROGRAMAR; con fecha: PROGRAMADA; confirmada: CONFIRMADA; por reprogramar")
    void estados() {
        assertThat(PortalCandidatoService.estadoCita(cita(EstadoSubproceso.PENDIENTE, null, EstadoConfirmacionAsistencia.SIN_LLAMAR)))
                .isEqualTo("POR_PROGRAMAR");
        assertThat(PortalCandidatoService.estadoCita(cita(EstadoSubproceso.EN_EJECUCION, MANANA, EstadoConfirmacionAsistencia.SIN_LLAMAR)))
                .isEqualTo("PROGRAMADA");
        assertThat(PortalCandidatoService.estadoCita(cita(EstadoSubproceso.EN_EJECUCION, MANANA, EstadoConfirmacionAsistencia.CONFIRMADO)))
                .isEqualTo("CONFIRMADA");
        assertThat(PortalCandidatoService.estadoCita(cita(EstadoSubproceso.EN_EJECUCION, MANANA, EstadoConfirmacionAsistencia.REPROGRAMAR)))
                .isEqualTo("POR_REPROGRAMAR");
    }

    @Test
    @DisplayName("Completada o publicada: REALIZADA; cancelada: CANCELADA — y ahí ya no se puede sugerir horario")
    void terminadas() {
        String realizada = PortalCandidatoService.estadoCita(cita(EstadoSubproceso.PUBLICADO, MANANA, EstadoConfirmacionAsistencia.CONFIRMADO));
        String cancelada = PortalCandidatoService.estadoCita(cita(EstadoSubproceso.CANCELADO, MANANA, EstadoConfirmacionAsistencia.CONFIRMADO));
        assertThat(realizada).isEqualTo("REALIZADA");
        assertThat(cancelada).isEqualTo("CANCELADA");
        assertThat(PortalCandidatoService.puedeSugerir(realizada)).isFalse();
        assertThat(PortalCandidatoService.puedeSugerir(cancelada)).isFalse();
    }

    @Test
    @DisplayName("Proponer horario solo sin fecha asignada o por reprogramar; programada/confirmada ya no")
    void soloSinFechaAsignada() {
        assertThat(PortalCandidatoService.puedeSugerir("POR_PROGRAMAR")).isTrue();
        assertThat(PortalCandidatoService.puedeSugerir("POR_REPROGRAMAR")).isTrue();
        assertThat(PortalCandidatoService.puedeSugerir("PROGRAMADA")).isFalse();
        assertThat(PortalCandidatoService.puedeSugerir("CONFIRMADA")).isFalse();
    }

    @Test
    @DisplayName("Solo poligrafía y visita son citas; el análisis del analista no")
    void soloPresenciales() {
        ServicioSubproceso analisis = ServicioSubproceso.builder()
                .tipoProgreso(TipoProgreso.builder().rolResponsable(Rol.ANALISTA_INTERNO).build()).build();
        assertThat(PortalCandidatoService.esCita(analisis)).isFalse();
        assertThat(PortalCandidatoService.esCita(cita(EstadoSubproceso.PENDIENTE, null, null))).isTrue();
    }
}
