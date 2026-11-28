package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.modules.solicitudes.entity.SolicitudIdempotencia;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudIdempotenciaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** NS-03: la llave de idempotencia evita cobrar dos veces. Si el envío llegara a crear algo, el servicio (sin cliente simulado) fallaría con NullPointer. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SolicitudService — llave de idempotencia")
class SolicitudIdempotenciaTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private com.polygraph.erp.modules.servicios.repository.ServicioRepository servicioRepository;
    @Mock private SolicitudIdempotenciaRepository idempotenciaRepository;
    @Mock private com.polygraph.erp.shared.service.DiasHabilesService diasHabilesService;
    @Mock private com.polygraph.erp.modules.catalogo.repository.CiudadRepository ciudadRepository;
    @Mock private com.polygraph.erp.modules.servicios.repository.ProcesoRepository procesoRepository;
    @InjectMocks private SolicitudService service;

    private final SolicitudRequest peticion = new SolicitudRequest("1020304050", "Ana", "Pérez", "3001234567", "ana@correo.com", 1, "Analista", "ESTANDAR", null,
            java.util.List.of(1), null, null, null);   // completa (NS-07): el envío se corta antes de usarla

    @org.junit.jupiter.api.BeforeEach
    void ciudadConfirmada() {
        when(ciudadRepository.findById(1)).thenReturn(Optional.of(com.polygraph.erp.modules.catalogo.entity.Ciudad.builder().idCiudad(1).pendienteConfirmacion(false).build()));
    }

    private void usuario() {
        when(usuarioRepository.findByEmail("c@test.com")).thenReturn(Optional.of(Usuario.builder().idUsuario(5L).build()));
    }

    @Test
    @DisplayName("Dos envíos simultáneos con la misma llave: el que pierde recibe 409 y no descuenta nada")
    void envioSimultaneo() {
        usuario();
        when(idempotenciaRepository.findByIdUsuarioAndClave(5L, "k1")).thenReturn(Optional.empty());
        when(idempotenciaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_solicitudes_idempotencia"));
        assertThatThrownBy(() -> service.crearSolicitud(peticion, "c@test.com", "k1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Misma llave con un envío anterior que aún no terminó: 409, no se crea otra")
    void envioEnCurso() {
        usuario();
        when(idempotenciaRepository.findByIdUsuarioAndClave(5L, "k1")).thenReturn(Optional.of(SolicitudIdempotencia.builder().idUsuario(5L).clave("k1").build()));
        assertThatThrownBy(() -> service.crearSolicitud(peticion, "c@test.com", "k1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Misma llave con un envío ya terminado: devuelve lo creado, sin volver a crear ni cobrar")
    void envioRepetido() {
        usuario();
        when(idempotenciaRepository.findByIdUsuarioAndClave(5L, "k1")).thenReturn(Optional.of(
                SolicitudIdempotencia.builder().idUsuario(5L).clave("k1").idsServicios("77").build()));
        // el servicio 77 ya no existe en el repositorio simulado -> lista vacía, pero lo importante: no pasó por la creación (habría NullPointer)
        assertThat(service.crearSolicitud(peticion, "c@test.com", "k1")).isEmpty();
    }

    @Test
    @DisplayName("Una llave de más de 80 caracteres se rechaza")
    void llaveLarga() {
        assertThatThrownBy(() -> service.crearSolicitud(peticion, "c@test.com", "x".repeat(81)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("NS-08: una ciudad pendiente de confirmación se rechaza antes de crear nada")
    void ciudadSinConfirmar() {
        when(ciudadRepository.findById(1)).thenReturn(Optional.of(com.polygraph.erp.modules.catalogo.entity.Ciudad.builder().idCiudad(1).pendienteConfirmacion(true).build()));
        assertThatThrownBy(() -> service.crearSolicitud(peticion, "c@test.com", "k1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ciudades confirmadas");
    }
}
