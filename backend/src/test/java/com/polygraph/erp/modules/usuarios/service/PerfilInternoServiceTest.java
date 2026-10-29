package com.polygraph.erp.modules.usuarios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.usuarios.dto.ActualizarPerfilInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.PerfilInternoResponse;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
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
@DisplayName("Mi perfil de usuarios internos — cada quien edita lo suyo")
class PerfilInternoServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuariosIntenosRepository empleadoRepository;
    @Mock private CiudadRepository ciudadRepository;

    @InjectMocks
    private PerfilInternoService service;

    private Usuario usuario;
    private UsuariosInternos empleado;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().idUsuario(1L).nombre("Ana").apellido("Ruiz").email("ana@polygraph.com")
                .rol(Rol.POLIGRAFISTA).idEmpleado(10).build();
        empleado = UsuariosInternos.builder().idEmpleado(10).nombre("Ana").apellido("Ruiz").email("ana@polygraph.com")
                .telefono("3001112233").documento("123").tipoDocumento("CC").build();
        when(usuarioRepository.findByEmail("ana@polygraph.com")).thenReturn(Optional.of(usuario));
        when(empleadoRepository.findById(10)).thenReturn(Optional.of(empleado));
        when(ciudadRepository.existsById(5)).thenReturn(true);
        when(ciudadRepository.findById(5)).thenReturn(Optional.of(Ciudad.builder().idCiudad(5).nombreCiudad("Bogotá").build()));
    }

    @Test
    @DisplayName("Actualizar cambia nombre, apellido, teléfono y ciudad en usuarios y empleados, y no toca correo, rol ni documento")
    void actualizaEnAmbasTablas() {
        PerfilInternoResponse r = service.actualizar("ana@polygraph.com",
                new ActualizarPerfilInternoRequest("  Ana María ", "Ruiz Pérez", "310 555 1234", 5));

        assertThat(usuario.getNombre()).isEqualTo("Ana María");
        assertThat(usuario.getApellido()).isEqualTo("Ruiz Pérez");
        assertThat(empleado.getNombre()).isEqualTo("Ana María");
        assertThat(empleado.getTelefono()).isEqualTo("310 555 1234");
        assertThat(empleado.getIdCiudadResidencia()).isEqualTo(5);
        assertThat(usuario.getEmail()).isEqualTo("ana@polygraph.com");
        assertThat(usuario.getRol()).isEqualTo(Rol.POLIGRAFISTA);
        assertThat(empleado.getDocumento()).isEqualTo("123");
        assertThat(r.nombreCiudad()).isEqualTo("Bogotá");
    }

    @Test
    @DisplayName("Teléfono y apellido vacíos se guardan como nulos")
    void vaciosQuedanNulos() {
        service.actualizar("ana@polygraph.com", new ActualizarPerfilInternoRequest("Ana", "  ", "", null));

        assertThat(usuario.getApellido()).isNull();
        assertThat(empleado.getTelefono()).isNull();
        assertThat(empleado.getIdCiudadResidencia()).isNull();
    }

    @Test
    @DisplayName("Una ciudad que no existe se rechaza (400) y no cambia nada")
    void ciudadInexistente() {
        assertThatThrownBy(() -> service.actualizar("ana@polygraph.com", new ActualizarPerfilInternoRequest("Otro", null, null, 999)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(usuario.getNombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Obtener devuelve los datos del propio usuario, con su ciudad")
    void obtiene() {
        empleado.setIdCiudadResidencia(5);

        PerfilInternoResponse r = service.obtener("ana@polygraph.com");

        assertThat(r.email()).isEqualTo("ana@polygraph.com");
        assertThat(r.documento()).isEqualTo("123");
        assertThat(r.nombreCiudad()).isEqualTo("Bogotá");
    }

    @Test
    @DisplayName("Días para cambiar la contraseña: 30 desde el último cambio, 0 si el admin la marcó, vacío si no hay fecha")
    void diasParaCambiarPassword() {
        java.time.LocalDate hoy = java.time.LocalDate.of(2026, 10, 15);
        usuario.setRequiereCambioPassword(false);

        usuario.setFechaUltimoCambioPassword(hoy.minusDays(10).atTime(9, 0));
        assertThat(PerfilInternoService.diasParaCambiarPassword(usuario, hoy)).isEqualTo(20);

        usuario.setFechaUltimoCambioPassword(hoy.atTime(8, 0));
        assertThat(PerfilInternoService.diasParaCambiarPassword(usuario, hoy)).isEqualTo(30);

        usuario.setFechaUltimoCambioPassword(hoy.minusDays(35).atTime(9, 0));
        assertThat(PerfilInternoService.diasParaCambiarPassword(usuario, hoy)).isEqualTo(-5);

        usuario.setFechaUltimoCambioPassword(null);
        assertThat(PerfilInternoService.diasParaCambiarPassword(usuario, hoy)).isNull();

        usuario.setRequiereCambioPassword(true);
        assertThat(PerfilInternoService.diasParaCambiarPassword(usuario, hoy)).isZero();
    }

    @Test
    @DisplayName("Un usuario sin registro de empleado igual puede ver su perfil (sin teléfono ni ciudad)")
    void sinEmpleado() {
        usuario.setIdEmpleado(null);

        PerfilInternoResponse r = service.obtener("ana@polygraph.com");

        assertThat(r.nombre()).isEqualTo("Ana");
        assertThat(r.telefono()).isNull();
        assertThat(r.ciudadesAsignadas()).isEmpty();
    }
}
