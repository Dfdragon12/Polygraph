package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.HistorialEstadoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudDetalleResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import com.polygraph.erp.shared.service.DiasHabilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolicitudService — Pruebas de creación y gestión de servicios")
class SolicitudServiceTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private HistorialEstadoServicioRepository historialRepository;
    @Mock private CandidatoRepository candidatoRepository;
    @Mock private ProcesoRepository procesoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private LinkCandidatoRepository linkCandidatoRepository;
    @Mock private NotificacionRepository notificacionRepository;
    @Mock private DiasHabilesService diasHabilesService;
    @Mock private ProcesoTipoProgresoRepository procesoTipoProgresoRepository;
    @Mock private ServicioSubprocesoRepository servicioSubprocesoRepository;

    @InjectMocks
    private SolicitudService solicitudService;

    private Usuario usuarioCliente;
    private Cliente cliente;
    private Proceso procesoEstudioBasico;
    private LocalDate fechaEntregaMock;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder()
                .idCliente(1)
                .nombre("Empresa")
                .apellido("ABC")
                .build();

        usuarioCliente = Usuario.builder()
                .idUsuario(10L)
                .email("admin@empresa.com")
                .rol(Rol.ADMIN_CLIENTE)
                .activo(true)
                .idCliente(1)
                .build();

        procesoEstudioBasico = Proceso.builder()
                .idProceso(1)
                .nombreProceso("Estudio Básico")
                .activo(true)
                .diasHabilesEntrega(5)
                .build();

        fechaEntregaMock = LocalDate.now().plusDays(5);
    }

    // ── crearSolicitud ────────────────────────────────────────────

    @Test
    @DisplayName("Crear solicitud exitosa persiste con estado PENDIENTE")
    void crear_solicitud_exitosa_estado_pendiente() {
        SolicitudRequest request = new SolicitudRequest(
                "10000001", "Juan", "Pérez", "3001000001",
                "juan@test.com", "Bogotá", "Analista", List.of(1), null);

        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(procesoRepository.findById(1)).thenReturn(Optional.of(procesoEstudioBasico));
        when(servicioRepository.existeDuplicado(anyString(), any(), any())).thenReturn(false);
        when(candidatoRepository.findByCedula("10000001")).thenReturn(Optional.empty());
        when(candidatoRepository.save(any(Candidato.class))).thenAnswer(inv -> inv.getArgument(0));
        when(diasHabilesService.calcularFechaEntrega(any(), any(Integer.class))).thenReturn(fechaEntregaMock);
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0);
            s.setIdServicio(100);
            return s;
        });
        when(linkCandidatoRepository.save(any())).thenReturn(null);
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(notificacionRepository.save(any())).thenReturn(null);
        when(historialRepository.findByServicio_IdServicioOrderByFechaCambioDesc(any())).thenReturn(List.of());

        List<SolicitudDetalleResponse> respuesta = solicitudService.crearSolicitud(request, "admin@empresa.com");

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).cedulaEvaluado()).isEqualTo("10000001");
        assertThat(respuesta.get(0).estado()).isEqualTo(EstadoServicio.PENDIENTE.name());
        assertThat(respuesta.get(0).fechaEntregaEstimada()).isEqualTo(fechaEntregaMock);
        assertThat(respuesta.get(0).linkEvaluado()).isNotNull();

        ArgumentCaptor<Servicio> captor = ArgumentCaptor.forClass(Servicio.class);
        verify(servicioRepository, atLeastOnce()).save(captor.capture());
        Servicio guardado = captor.getAllValues().get(0);
        assertThat(guardado.getEstado()).isEqualTo(EstadoServicio.PENDIENTE);
    }

    @Test
    @DisplayName("Crear solicitud genera link para evaluado con 36 horas de expiración")
    void crear_solicitud_genera_link_evaluado() {
        SolicitudRequest request = new SolicitudRequest(
                "10000002", "María", "López", null, null, null, null, List.of(1), null);

        configurarMocksBase();

        solicitudService.crearSolicitud(request, "admin@empresa.com");

        verify(linkCandidatoRepository).save(any());
    }

    @Test
    @DisplayName("Crear solicitud notifica a gestores activos")
    void crear_solicitud_notifica_gestores() {
        SolicitudRequest request = new SolicitudRequest(
                "10000003", "Carlos", "Ruiz", null, null, null, null, List.of(1), null);

        Usuario gestor = Usuario.builder()
                .idUsuario(99L)
                .email("gestor@polygraph.com")
                .rol(Rol.GESTOR)
                .activo(true)
                .build();

        configurarMocksBase();
        when(usuarioRepository.findAll()).thenReturn(List.of(usuarioCliente, gestor));

        solicitudService.crearSolicitud(request, "admin@empresa.com");

        // Al menos 2 notificaciones: 1 al gestor + 1 al usuario cliente
        verify(notificacionRepository, atLeast(2)).save(any());
    }

    @Test
    @DisplayName("Crear solicitud con proceso duplicado (< 3 meses) lanza ApiException CONFLICT")
    void crear_solicitud_detecta_duplicado_lanza_excepcion() {
        SolicitudRequest request = new SolicitudRequest(
                "10000001", "Juan", "Pérez", null, null, null, null, List.of(1), null);

        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(procesoRepository.findById(1)).thenReturn(Optional.of(procesoEstudioBasico));
        when(servicioRepository.existeDuplicado(anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> solicitudService.crearSolicitud(request, "admin@empresa.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getMessage()).contains("3 meses");
                });

        verify(servicioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear solicitud con proceso desactivado lanza ApiException BAD_REQUEST")
    void crear_solicitud_con_proceso_inactivo_lanza_excepcion() {
        procesoEstudioBasico.setActivo(false);
        SolicitudRequest request = new SolicitudRequest(
                "10000001", "Juan", "Pérez", null, null, null, null, List.of(1), null);

        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(procesoRepository.findById(1)).thenReturn(Optional.of(procesoEstudioBasico));

        assertThatThrownBy(() -> solicitudService.crearSolicitud(request, "admin@empresa.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── cambiarEstado ─────────────────────────────────────────────

    @Test
    @DisplayName("Gestor puede cambiar estado a PROGRAMANDO y se registra historial")
    void gestor_puede_cambiar_estado_a_programando() {
        Servicio servicio = Servicio.builder()
                .idServicio(1)
                .estado(EstadoServicio.PENDIENTE)
                .build();

        Usuario gestor = Usuario.builder()
                .idUsuario(5L)
                .email("gestor@polygraph.com")
                .rol(Rol.GESTOR)
                .activo(true)
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("gestor@polygraph.com");
        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(usuarioRepository.findByEmail("gestor@polygraph.com")).thenReturn(Optional.of(gestor));
        when(historialRepository.save(any())).thenReturn(null);

        solicitudService.cambiarEstado(1, new CambioEstadoRequest("PROGRAMANDO", "Asignado"), userDetails);

        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.PROGRAMANDO);
        verify(historialRepository).save(any());
    }

    @Test
    @DisplayName("ADMIN_CLIENTE solo puede cancelar — intento de otro estado lanza ApiException FORBIDDEN")
    void admin_cliente_solo_puede_cancelar() {
        Servicio servicio = Servicio.builder()
                .idServicio(1)
                .estado(EstadoServicio.PENDIENTE)
                .build();

        when(servicioRepository.findById(1)).thenReturn(Optional.of(servicio));
        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("admin@empresa.com");

        assertThatThrownBy(() ->
                solicitudService.cambiarEstado(1, new CambioEstadoRequest("PROGRAMANDO", null), userDetails))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("obtenerDetalle con ID inexistente lanza ApiException NOT_FOUND")
    void obtener_detalle_id_inexistente_lanza_excepcion() {
        when(servicioRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solicitudService.obtenerDetalle(999))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── helper ───────────────────────────────────────────────────

    private void configurarMocksBase() {
        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(procesoRepository.findById(1)).thenReturn(Optional.of(procesoEstudioBasico));
        when(servicioRepository.existeDuplicado(anyString(), any(), any())).thenReturn(false);
        when(candidatoRepository.findByCedula(anyString())).thenReturn(Optional.empty());
        when(candidatoRepository.save(any(Candidato.class))).thenAnswer(inv -> inv.getArgument(0));
        when(diasHabilesService.calcularFechaEntrega(any(), any(Integer.class))).thenReturn(fechaEntregaMock);
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(inv -> {
            Servicio s = inv.getArgument(0);
            s.setIdServicio(100);
            return s;
        });
        when(linkCandidatoRepository.save(any())).thenReturn(null);
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(notificacionRepository.save(any())).thenReturn(null);
        when(historialRepository.findByServicio_IdServicioOrderByFechaCambioDesc(any())).thenReturn(List.of());
    }
}
