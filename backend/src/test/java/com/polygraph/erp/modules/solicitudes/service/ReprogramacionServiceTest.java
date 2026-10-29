package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.HistorialEstadoServicio;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.SolicitudReprogramacion;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudReprogramacionRepository;
import com.polygraph.erp.modules.servicios.service.NotificacionesTiempoService;
import com.polygraph.erp.modules.servicios.service.SemaforoTiempoService;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.ImputableA;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.service.FestivosColombiaGenerator;
import com.polygraph.erp.shared.service.ParametrosTiempo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReprogramacionService — cambiar la fecha de compromiso")
class ReprogramacionServiceTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SolicitudReprogramacionRepository reprogramacionRepository;
    @Mock private HistorialEstadoServicioRepository historialRepository;
    @Mock private SemaforoTiempoService semaforo;
    @Mock private NotificacionesTiempoService avisos;

    private ReprogramacionService service;
    private Servicio s;
    private final Usuario gestor = Usuario.builder().idUsuario(7L).email("gestor@polygraph.co").rol(Rol.GESTOR).build();
    private final Usuario otroGestor = Usuario.builder().idUsuario(8L).email("otro@polygraph.co").rol(Rol.GESTOR).build();
    private final Usuario admin = Usuario.builder().idUsuario(1L).email("admin@polygraph.co").rol(Rol.ADMIN_POLYGRAPH).build();

    @BeforeEach
    void setUp() {
        // Hoy = lunes 19-oct-2026
        var calendario = new CalendarioHabilService(anio -> FestivosColombiaGenerator.generar(anio).keySet(), () -> ParametrosTiempo.POR_DEFECTO,
                Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("UTC")));
        service = new ReprogramacionService(servicioRepository, usuarioRepository, reprogramacionRepository, historialRepository, semaforo, avisos, calendario);
        s = Servicio.builder().idServicio(12).estado(EstadoServicio.EN_EJECUCION).fechaEntregaEstimada(LocalDate.of(2026, 10, 23))
                .fechaCompromisoOriginal(LocalDate.of(2026, 10, 23)).cliente(Cliente.builder().idCliente(5).idGestor(7L).build()).build();
        when(servicioRepository.findById(12)).thenReturn(Optional.of(s));
        when(usuarioRepository.findByEmail("gestor@polygraph.co")).thenReturn(Optional.of(gestor));
        when(usuarioRepository.findByEmail("otro@polygraph.co")).thenReturn(Optional.of(otroGestor));
        when(usuarioRepository.findByEmail("admin@polygraph.co")).thenReturn(Optional.of(admin));
        when(reprogramacionRepository.save(any(SolicitudReprogramacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void esperar400(Runnable accion, String contiene) {
        assertThatThrownBy(accion::run).isInstanceOf(ApiException.class).hasMessageContaining(contiene)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(reprogramacionRepository, never()).save(any());
        verify(avisos, never()).alReprogramar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Reprograma: guarda anterior, nueva, motivo, responsable y usuario; conserva el compromiso original; pasa por REPROGRAMADO, recalcula y avisa al cliente")
    void reprograma() {
        var r = service.reprogramar(12, LocalDate.of(2026, 10, 30), "  El cliente pidió más tiempo  ", ImputableA.CLIENTE, "gestor@polygraph.co");

        assertThat(r.getFechaAnterior()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(r.getFechaNueva()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(r.getMotivo()).isEqualTo("El cliente pidió más tiempo");
        assertThat(r.getResponsable()).isEqualTo(ImputableA.CLIENTE);
        assertThat(r.getUsuario()).isEqualTo("gestor@polygraph.co");
        assertThat(s.getFechaEntregaEstimada()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(s.getFechaCompromisoOriginal()).isEqualTo(LocalDate.of(2026, 10, 23));      // la primera promesa no se pierde
        assertThat(s.getEstado()).isEqualTo(EstadoServicio.REPROGRAMADO);

        ArgumentCaptor<HistorialEstadoServicio> h = ArgumentCaptor.forClass(HistorialEstadoServicio.class);
        verify(historialRepository).save(h.capture());
        assertThat(h.getValue().getEstadoAnterior()).isEqualTo(EstadoServicio.EN_EJECUCION);
        assertThat(h.getValue().getEstadoNuevo()).isEqualTo(EstadoServicio.REPROGRAMADO);
        assertThat(h.getValue().getObservacion()).contains("23 de octubre de 2026").contains("30 de octubre de 2026").contains("El cliente pidió más tiempo");
        verify(semaforo).recalcular(s);
        verify(avisos).alReprogramar(s, LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 30), "El cliente pidió más tiempo");
    }

    @Test
    @DisplayName("Exige motivo, responsable y una fecha")
    void exigeLosDatos() {
        esperar400(() -> service.reprogramar(12, LocalDate.of(2026, 10, 30), "  ", ImputableA.POLYGRAPH, "gestor@polygraph.co"), "motivo");
        esperar400(() -> service.reprogramar(12, LocalDate.of(2026, 10, 30), "x", null, "gestor@polygraph.co"), "causa");
        esperar400(() -> service.reprogramar(12, null, "x", ImputableA.POLYGRAPH, "gestor@polygraph.co"), "nueva fecha");
    }

    @Test
    @DisplayName("La nueva fecha debe ser un día hábil, no pasada y distinta de la actual")
    void validaLaFecha() {
        esperar400(() -> service.reprogramar(12, LocalDate.of(2026, 10, 24), "x", ImputableA.POLYGRAPH, "gestor@polygraph.co"), "no es un día hábil");   // sábado
        esperar400(() -> service.reprogramar(12, LocalDate.of(2026, 11, 2), "x", ImputableA.POLYGRAPH, "gestor@polygraph.co"), "no es un día hábil");     // festivo
        esperar400(() -> service.reprogramar(12, LocalDate.of(2026, 10, 16), "x", ImputableA.POLYGRAPH, "gestor@polygraph.co"), "anterior a hoy");
        esperar400(() -> service.reprogramar(12, LocalDate.of(2026, 10, 23), "x", ImputableA.POLYGRAPH, "gestor@polygraph.co"), "la misma");
    }

    @Test
    @DisplayName("Una solicitud entregada o cancelada no se reprograma")
    void soloAbiertas() {
        for (EstadoServicio cerrada : new EstadoServicio[]{EstadoServicio.PUBLICADO, EstadoServicio.CANCELADO}) {
            s.setEstado(cerrada);
            assertThatThrownBy(() -> service.reprogramar(12, LocalDate.of(2026, 10, 30), "x", ImputableA.POLYGRAPH, "gestor@polygraph.co"))
                    .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
        verify(reprogramacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un gestor solo reprograma las solicitudes de SUS clientes; el administrador, todas")
    void autorizacion() {
        assertThatThrownBy(() -> service.reprogramar(12, LocalDate.of(2026, 10, 30), "x", ImputableA.POLYGRAPH, "otro@polygraph.co"))
                .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(reprogramacionRepository, never()).save(any());
        assertThat(service.reprogramar(12, LocalDate.of(2026, 10, 30), "x", ImputableA.POLYGRAPH, "admin@polygraph.co").getUsuario()).isEqualTo("admin@polygraph.co");
    }
}
