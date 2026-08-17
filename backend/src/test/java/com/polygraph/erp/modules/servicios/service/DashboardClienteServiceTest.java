package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.dto.DashboardClienteResponse;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardClienteService — Pruebas de KPIs del cliente")
class DashboardClienteServiceTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private DashboardClienteService dashboardService;

    private static final String EMAIL_CLIENTE = "admin@empresa-abc.com";
    private static final Integer ID_CLIENTE = 1;

    private Usuario usuarioCliente;

    @BeforeEach
    void setUp() {
        // Simular usuario autenticado en el SecurityContext
        var auth = new UsernamePasswordAuthenticationToken(EMAIL_CLIENTE, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        usuarioCliente = Usuario.builder()
                .idUsuario(10L)
                .email(EMAIL_CLIENTE)
                .rol(Rol.ADMIN_CLIENTE)
                .activo(true)
                .idCliente(ID_CLIENTE)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── obtenerDashboard ──────────────────────────────────────────

    @Test
    @DisplayName("Dashboard retorna conteos correctos con servicios mixtos")
    void dashboard_retorna_conteos_correctos() {
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(usuarioCliente));
        when(servicioRepository.countByCliente_IdClienteAndEstadoIn(eq(ID_CLIENTE), any()))
                .thenReturn(5L);
        when(servicioRepository.countByCliente_IdClienteAndEstado(ID_CLIENTE, EstadoServicio.PENDIENTE))
                .thenReturn(3L);
        when(servicioRepository.countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
                eq(ID_CLIENTE), any(), any(), any()))
                .thenReturn(2L);
        when(servicioRepository.findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(eq(ID_CLIENTE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DashboardClienteResponse respuesta = dashboardService.obtenerDashboard();

        assertThat(respuesta.serviciosActivos()).isEqualTo(5L);
        assertThat(respuesta.serviciosPendientes()).isEqualTo(3L);
        assertThat(respuesta.serviciosFinalizadosMes()).isEqualTo(2L);
        assertThat(respuesta.ultimasSolicitudes()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Dashboard cliente sin servicios retorna todos los conteos en cero")
    void dashboard_cliente_sin_solicitudes_retorna_ceros() {
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(usuarioCliente));
        when(servicioRepository.countByCliente_IdClienteAndEstadoIn(any(), any())).thenReturn(0L);
        when(servicioRepository.countByCliente_IdClienteAndEstado(any(), any())).thenReturn(0L);
        when(servicioRepository.countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
                any(), any(), any(), any())).thenReturn(0L);
        when(servicioRepository.findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DashboardClienteResponse respuesta = dashboardService.obtenerDashboard();

        assertThat(respuesta.serviciosActivos()).isZero();
        assertThat(respuesta.serviciosPendientes()).isZero();
        assertThat(respuesta.serviciosFinalizadosMes()).isZero();
        assertThat(respuesta.serviciosDisponibles()).isZero();
        assertThat(respuesta.ultimasSolicitudes()).isNotNull();
    }

    @Test
    @DisplayName("Dashboard incluye los últimos 5 servicios del cliente")
    void dashboard_incluye_ultimas_5_solicitudes() {
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(usuarioCliente));
        when(servicioRepository.countByCliente_IdClienteAndEstadoIn(any(), any())).thenReturn(0L);
        when(servicioRepository.countByCliente_IdClienteAndEstado(any(), any())).thenReturn(0L);
        when(servicioRepository.countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
                any(), any(), any(), any())).thenReturn(0L);

        List<Servicio> servicios = crearServiciosDePrueba(3);
        when(servicioRepository.findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(eq(ID_CLIENTE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(servicios));

        DashboardClienteResponse respuesta = dashboardService.obtenerDashboard();

        assertThat(respuesta.ultimasSolicitudes()).hasSize(3);
        assertThat(respuesta.ultimasSolicitudes().get(0).cedulaEvaluado()).isEqualTo("10000001");
    }

    @Test
    @DisplayName("Dashboard lanza ApiException FORBIDDEN si el usuario no tiene idCliente")
    void dashboard_sin_cliente_asociado_lanza_excepcion() {
        Usuario usuarioSinCliente = Usuario.builder()
                .idUsuario(99L)
                .email(EMAIL_CLIENTE)
                .rol(Rol.GESTOR)
                .activo(true)
                .idCliente(null) // sin cliente
                .build();
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(usuarioSinCliente));

        assertThatThrownBy(() -> dashboardService.obtenerDashboard())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cliente");
    }

    @Test
    @DisplayName("Dashboard serviciosActivos incluye PENDIENTE + PROGRAMANDO + EN_EJECUCION")
    void dashboard_activos_incluye_tres_estados() {
        when(usuarioRepository.findByEmail(EMAIL_CLIENTE)).thenReturn(Optional.of(usuarioCliente));
        // 3 pendientes + 2 programando + 1 en ejecución = 6 activos
        when(servicioRepository.countByCliente_IdClienteAndEstadoIn(eq(ID_CLIENTE), any()))
                .thenReturn(6L);
        when(servicioRepository.countByCliente_IdClienteAndEstado(any(), any())).thenReturn(3L);
        when(servicioRepository.countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
                any(), any(), any(), any())).thenReturn(0L);
        when(servicioRepository.findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DashboardClienteResponse respuesta = dashboardService.obtenerDashboard();

        assertThat(respuesta.serviciosActivos()).isEqualTo(6L);
        assertThat(respuesta.serviciosPendientes()).isEqualTo(3L);
    }

    // ── helper ───────────────────────────────────────────────────

    private List<Servicio> crearServiciosDePrueba(int cantidad) {
        List<Servicio> lista = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            Proceso proceso = Proceso.builder()
                    .nombreProceso("Estudio Básico")
                    .build();
            Candidato candidato = Candidato.builder()
                    .cedula("1000000" + i)
                    .nombres("Evaluado" + i)
                    .apellidos("Apellido" + i)
                    .build();
            Servicio s = Servicio.builder()
                    .idServicio(i)
                    .candidato(candidato)
                    .proceso(proceso)
                    .cargo("Cargo " + i)
                    .estado(EstadoServicio.PENDIENTE)
                    .fechaSolicitud(LocalDate.now().minusDays(i))
                    .build();
            lista.add(s);
        }
        return lista;
    }
}
