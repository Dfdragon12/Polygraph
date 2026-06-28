package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.modules.servicios.repository.CatalogoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudDetalleResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.modules.solicitudes.repository.HistorialSolicitudRepository;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudRepository;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudServicioRepository;
import com.polygraph.erp.shared.enums.CategoriaServicio;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolicitudService — Pruebas de creación y gestión de solicitudes")
class SolicitudServiceTest {

    @Mock private SolicitudRepository solicitudRepository;
    @Mock private SolicitudServicioRepository solicitudServicioRepository;
    @Mock private HistorialSolicitudRepository historialRepository;
    @Mock private CandidatoRepository candidatoRepository;
    @Mock private CatalogoServicioRepository catalogoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private LinkCandidatoRepository linkCandidatoRepository;
    @Mock private NotificacionRepository notificacionRepository;
    @Mock private DiasHabilesService diasHabilesService;

    @InjectMocks
    private SolicitudService solicitudService;

    private Usuario usuarioCliente;
    private Cliente cliente;
    private CatalogoServicio catalogoEstudioBasico;
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

        catalogoEstudioBasico = CatalogoServicio.builder()
                .idCatalogo(1)
                .nombre("Estudio Básico")
                .categoria(CategoriaServicio.ESTUDIOS_SEGURIDAD)
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
        when(catalogoRepository.findById(1)).thenReturn(Optional.of(catalogoEstudioBasico));
        when(solicitudServicioRepository.existeDuplicado(anyString(), any(), any())).thenReturn(false);
        when(candidatoRepository.findByCedula("10000001")).thenReturn(Optional.empty());
        when(diasHabilesService.calcularFechaEntrega(any(), any(Integer.class))).thenReturn(fechaEntregaMock);
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> {
            Solicitud s = inv.getArgument(0);
            s.setIdSolicitud(100L);
            return s;
        });
        when(linkCandidatoRepository.save(any())).thenReturn(null);
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(notificacionRepository.save(any())).thenReturn(null);
        when(historialRepository.findBySolicitud_IdSolicitudOrderByFechaCambioDesc(any())).thenReturn(List.of());

        SolicitudDetalleResponse respuesta = solicitudService.crearSolicitud(request, "admin@empresa.com");

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.cedulaEvaluado()).isEqualTo("10000001");
        assertThat(respuesta.estado()).isEqualTo(EstadoServicio.PENDIENTE.name());
        assertThat(respuesta.fechaEntregaEstimada()).isEqualTo(fechaEntregaMock);

        ArgumentCaptor<Solicitud> captor = ArgumentCaptor.forClass(Solicitud.class);
        verify(solicitudRepository, atLeastOnce()).save(captor.capture());
        Solicitud guardada = captor.getAllValues().get(0);
        assertThat(guardada.getEstado()).isEqualTo(EstadoServicio.PENDIENTE);
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
    @DisplayName("Crear solicitud con servicio duplicado (< 3 meses) lanza ApiException CONFLICT")
    void crear_solicitud_detecta_duplicado_lanza_excepcion() {
        SolicitudRequest request = new SolicitudRequest(
                "10000001", "Juan", "Pérez", null, null, null, null, List.of(1), null);

        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(catalogoRepository.findById(1)).thenReturn(Optional.of(catalogoEstudioBasico));
        when(solicitudServicioRepository.existeDuplicado(anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> solicitudService.crearSolicitud(request, "admin@empresa.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getMessage()).contains("3 meses");
                });

        verify(solicitudRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear solicitud con servicio desactivado lanza ApiException BAD_REQUEST")
    void crear_solicitud_con_servicio_inactivo_lanza_excepcion() {
        catalogoEstudioBasico.setActivo(false);
        SolicitudRequest request = new SolicitudRequest(
                "10000001", "Juan", "Pérez", null, null, null, null, List.of(1), null);

        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(catalogoRepository.findById(1)).thenReturn(Optional.of(catalogoEstudioBasico));

        assertThatThrownBy(() -> solicitudService.crearSolicitud(request, "admin@empresa.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── cambiarEstado ─────────────────────────────────────────────

    @Test
    @DisplayName("Gestor puede cambiar estado a PROGRAMANDO y se registra historial")
    void gestor_puede_cambiar_estado_a_programando() {
        Solicitud solicitud = Solicitud.builder()
                .idSolicitud(1L)
                .estado(EstadoServicio.PENDIENTE)
                .servicios(new ArrayList<>())
                .build();

        Usuario gestor = Usuario.builder()
                .idUsuario(5L)
                .email("gestor@polygraph.com")
                .rol(Rol.GESTOR)
                .activo(true)
                .build();

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("gestor@polygraph.com");
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(usuarioRepository.findByEmail("gestor@polygraph.com")).thenReturn(Optional.of(gestor));
        when(historialRepository.save(any())).thenReturn(null);

        solicitudService.cambiarEstado(1L, new CambioEstadoRequest("PROGRAMANDO", "Asignado"), userDetails);

        assertThat(solicitud.getEstado()).isEqualTo(EstadoServicio.PROGRAMANDO);
        verify(historialRepository).save(any());
    }

    @Test
    @DisplayName("ADMIN_CLIENTE solo puede cancelar — intento de otro estado lanza ApiException FORBIDDEN")
    void admin_cliente_solo_puede_cancelar() {
        Solicitud solicitud = Solicitud.builder()
                .idSolicitud(1L)
                .estado(EstadoServicio.PENDIENTE)
                .servicios(new ArrayList<>())
                .build();

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("admin@empresa.com");

        assertThatThrownBy(() ->
                solicitudService.cambiarEstado(1L, new CambioEstadoRequest("PROGRAMANDO", null), userDetails))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("obtenerDetalle con ID inexistente lanza ApiException NOT_FOUND")
    void obtener_detalle_id_inexistente_lanza_excepcion() {
        when(solicitudRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solicitudService.obtenerDetalle(999L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── helper ───────────────────────────────────────────────────

    private void configurarMocksBase() {
        when(usuarioRepository.findByEmail("admin@empresa.com")).thenReturn(Optional.of(usuarioCliente));
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));
        when(catalogoRepository.findById(1)).thenReturn(Optional.of(catalogoEstudioBasico));
        when(solicitudServicioRepository.existeDuplicado(anyString(), any(), any())).thenReturn(false);
        when(candidatoRepository.findByCedula(anyString())).thenReturn(Optional.empty());
        when(diasHabilesService.calcularFechaEntrega(any(), any(Integer.class))).thenReturn(fechaEntregaMock);
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> {
            Solicitud s = inv.getArgument(0);
            s.setIdSolicitud(100L);
            return s;
        });
        when(linkCandidatoRepository.save(any())).thenReturn(null);
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(notificacionRepository.save(any())).thenReturn(null);
        when(historialRepository.findBySolicitud_IdSolicitudOrderByFechaCambioDesc(any())).thenReturn(List.of());
    }
}
