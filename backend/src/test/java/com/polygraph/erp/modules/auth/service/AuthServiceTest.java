package com.polygraph.erp.modules.auth.service;

import com.polygraph.erp.modules.auth.dto.LoginRequest;
import com.polygraph.erp.modules.auth.dto.LoginResponse;
import com.polygraph.erp.modules.auth.dto.RegistroNaturalRequest;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.TokenRefreshRepository;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.security.JwtUtil;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Pruebas unitarias")
class AuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private TokenRefreshRepository tokenRefreshRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;
    @Mock private NotificacionService notificacionService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiracionMs", 604800000L);

        usuarioActivo = Usuario.builder()
                .idUsuario(1L)
                .nombre("Juan")
                .apellido("Pérez")
                .email("juan@test.com")
                .password("hashed-password")
                .rol(Rol.ADMIN_CLIENTE)
                .activo(true)
                .emailVerificado(true)
                .requiereCambioPassword(false)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    // ── LOGIN ────────────────────────────────────────────────────

    @Test
    @DisplayName("Login exitoso retorna token y datos del usuario")
    void login_exitoso_retorna_token() {
        LoginRequest request = new LoginRequest("juan@test.com", "Password1!");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(usuarioActivo));
        when(jwtUtil.generarToken(usuarioActivo)).thenReturn("fake-access-token");
        when(tokenRefreshRepository.save(any())).thenReturn(null);

        LoginResponse respuesta = authService.login(request);

        assertThat(respuesta.token()).isNotNull().isEqualTo("fake-access-token");
        assertThat(respuesta.usuario().email()).isEqualTo("juan@test.com");
        assertThat(respuesta.usuario().rol()).isEqualTo(Rol.ADMIN_CLIENTE.name());
        assertThat(respuesta.refreshToken()).isNotNull();
    }

    @Test
    @DisplayName("Login falla cuando el AuthenticationManager lanza BadCredentialsException")
    void login_password_incorrecta_lanza_excepcion() {
        LoginRequest request = new LoginRequest("juan@test.com", "WrongPassword");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Credenciales inválidas"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Login falla cuando la cuenta está desactivada (DisabledException)")
    void login_usuario_inactivo_lanza_excepcion() {
        LoginRequest request = new LoginRequest("inactivo@test.com", "Password1!");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("Cuenta desactivada"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("Login falla cuando el usuario no existe en la BD tras autenticación")
    void login_usuario_no_encontrado_en_bd_lanza_excepcion() {
        LoginRequest request = new LoginRequest("fantasma@test.com", "Password1!");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(usuarioRepository.findByEmail("fantasma@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no encontrado");
    }

    @Test
    @DisplayName("Login con cambio de password requerido por antigüedad lo refleja en la respuesta")
    void login_requiere_cambio_password_por_antiguedad() {
        usuarioActivo.setFechaUltimoCambioPassword(LocalDateTime.now().minusDays(35));
        LoginRequest request = new LoginRequest("juan@test.com", "Password1!");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(usuarioActivo));
        when(jwtUtil.generarToken(any())).thenReturn("fake-token");
        when(tokenRefreshRepository.save(any())).thenReturn(null);

        LoginResponse respuesta = authService.login(request);

        assertThat(respuesta.usuario().requiereCambioPassword()).isTrue();
    }

    // ── REGISTRO NATURAL ─────────────────────────────────────────

    @Test
    @DisplayName("Registro persona natural crea usuario con rol ADMIN_CLIENTE inactivo")
    void registro_natural_crea_usuario_correctamente() {
        RegistroNaturalRequest request = new RegistroNaturalRequest(
                "María", "García", "maria@test.com", "Password1!", "3001234567");
        when(usuarioRepository.existsByEmail("maria@test.com")).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenReturn(new Cliente());
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed-pass");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioActivo);
        doNothing().when(emailService).enviarActivacion(anyString(), anyString(), anyString());

        authService.registrarNatural(request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertThat(guardado.getRol()).isEqualTo(Rol.ADMIN_CLIENTE);
        assertThat(guardado.getActivo()).isFalse();
        assertThat(guardado.getEmailVerificado()).isFalse();
        assertThat(guardado.getEmail()).isEqualTo("maria@test.com");
        verify(passwordEncoder).encode("Password1!");
        verify(emailService).enviarActivacion(eq("maria@test.com"), eq("María"), anyString());
    }

    @Test
    @DisplayName("Registro con email duplicado lanza ApiException 409 CONFLICT")
    void registro_email_duplicado_lanza_excepcion() {
        RegistroNaturalRequest request = new RegistroNaturalRequest(
                "Carlos", "López", "duplicado@test.com", "Password1!", null);
        when(usuarioRepository.existsByEmail("duplicado@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrarNatural(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getMessage()).contains("ya está registrado");
                });

        verify(clienteRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registro natural envía email de activación con token no nulo")
    void registro_natural_envia_email_de_activacion() {
        RegistroNaturalRequest request = new RegistroNaturalRequest(
                "Ana", "Torres", "ana@test.com", "Password1!", "3009876543");
        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(clienteRepository.save(any())).thenReturn(new Cliente());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(usuarioRepository.save(any())).thenReturn(usuarioActivo);

        authService.registrarNatural(request);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarActivacion(anyString(), anyString(), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isNotNull().isNotBlank();
    }

    // ── ACTIVACIÓN ───────────────────────────────────────────────

    @Test
    @DisplayName("Activar cuenta con token válido activa el usuario")
    void activar_cuenta_con_token_valido() {
        String token = "valid-token-123";
        usuarioActivo.setActivo(false);
        usuarioActivo.setEmailVerificado(false);
        usuarioActivo.setTokenActivacion(token);
        usuarioActivo.setTokenActivacionExpira(LocalDateTime.now().plusHours(1));
        when(usuarioRepository.findByTokenActivacion(token)).thenReturn(Optional.of(usuarioActivo));

        authService.activarCuenta(token);

        assertThat(usuarioActivo.getActivo()).isTrue();
        assertThat(usuarioActivo.getEmailVerificado()).isTrue();
        assertThat(usuarioActivo.getTokenActivacion()).isNull();
    }

    @Test
    @DisplayName("Activar cuenta con token expirado lanza ApiException")
    void activar_cuenta_token_expirado_lanza_excepcion() {
        String token = "expired-token";
        usuarioActivo.setTokenActivacion(token);
        usuarioActivo.setTokenActivacionExpira(LocalDateTime.now().minusHours(1));
        when(usuarioRepository.findByTokenActivacion(token)).thenReturn(Optional.of(usuarioActivo));

        assertThatThrownBy(() -> authService.activarCuenta(token))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("Activar cuenta con token inválido lanza ApiException NOT_FOUND")
    void activar_cuenta_token_invalido_lanza_excepcion() {
        when(usuarioRepository.findByTokenActivacion("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.activarCuenta("bad-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
