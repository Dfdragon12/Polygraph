package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Solicitudes — un gestor solo opera las de SUS clientes")
class SolicitudServiceAccesoGestorTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private SolicitudService service;

    private Usuario gestorDueno;
    private Usuario otroGestor;

    @BeforeEach
    void setUp() {
        gestorDueno = Usuario.builder().idUsuario(1L).email("gestor1@test.com").rol(Rol.GESTOR).build();
        otroGestor = Usuario.builder().idUsuario(2L).email("gestor2@test.com").rol(Rol.GESTOR).build();
        when(usuarioRepository.findByEmail("gestor1@test.com")).thenReturn(Optional.of(gestorDueno));
        when(usuarioRepository.findByEmail("gestor2@test.com")).thenReturn(Optional.of(otroGestor));
        // Sin candidato a propósito: si el acceso pasa, el flujo se detiene ahí con un 400 limpio.
        Servicio servicio = Servicio.builder().idServicio(77).estado(EstadoServicio.PENDIENTE)
                .cliente(Cliente.builder().idCliente(5).idGestor(1L).build()).build();
        when(servicioRepository.findById(77)).thenReturn(Optional.of(servicio));
    }

    private static void esForbidden(Throwable t) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Otro gestor no puede ver el detalle de la solicitud (403)")
    void otroGestorNoVeDetalle() {
        assertThatThrownBy(() -> service.obtenerDetalle(77, otroGestor)).satisfies(SolicitudServiceAccesoGestorTest::esForbidden);
    }

    @Test
    @DisplayName("Otro gestor no puede cambiar el estado de la solicitud (403)")
    void otroGestorNoCambiaEstado() {
        assertThatThrownBy(() -> service.cambiarEstado(77, new CambioEstadoRequest("CANCELADO", "x"), otroGestor))
                .satisfies(SolicitudServiceAccesoGestorTest::esForbidden);
    }

    @Test
    @DisplayName("Otro gestor no puede pedir el informe de la solicitud (403)")
    void otroGestorNoPideInforme() {
        assertThatThrownBy(() -> service.validarInforme(77, 1L, otroGestor)).satisfies(SolicitudServiceAccesoGestorTest::esForbidden);
    }

    @Test
    @DisplayName("Otro gestor no puede corregir la ciudad del candidato (403)")
    void otroGestorNoCorrigeCiudad() {
        assertThatThrownBy(() -> service.corregirCiudadCandidato(77, 3, otroGestor)).satisfies(SolicitudServiceAccesoGestorTest::esForbidden);
    }

    @Test
    @DisplayName("El gestor del cliente sí pasa el control de acceso")
    void gestorDuenoPasa() {
        // Pasa el acceso y llega a la regla de negocio siguiente (no hay candidato → 400, no 403).
        assertThatThrownBy(() -> service.corregirCiudadCandidato(77, 3, gestorDueno)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
