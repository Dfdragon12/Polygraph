package com.polygraph.erp.modules.auth.service;

import com.polygraph.erp.modules.auth.dto.*;
import com.polygraph.erp.modules.auth.entity.TokenRefresh;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.TokenRefreshRepository;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.security.JwtUtil;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TokenRefreshRepository tokenRefreshRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final NotificacionService notificacionService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpiracionMs;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        // Requiere cambio si el admin lo marcó, o si ya pasaron 30 días desde el último cambio
        boolean cambioRequerido = Boolean.TRUE.equals(usuario.getRequiereCambioPassword());
        if (!cambioRequerido && usuario.getFechaUltimoCambioPassword() != null) {
            cambioRequerido = usuario.getFechaUltimoCambioPassword()
                    .isBefore(LocalDateTime.now().minusDays(30));
        }

        String token = jwtUtil.generarToken(usuario);
        String refreshToken = crearRefreshToken(usuario);

        usuario.setUltimoAcceso(LocalDateTime.now());
        log.info("Login exitoso: {} — cambio de contraseña requerido: {}", usuario.getEmail(), cambioRequerido);

        return new LoginResponse(token, refreshToken,
                new LoginResponse.UsuarioInfo(
                        usuario.getIdUsuario(), usuario.getEmail(),
                        usuario.getRol().name(), usuario.getNombre(), cambioRequerido));
    }

    public void cambiarPassword(String email, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        usuario.setPassword(passwordEncoder.encode(request.nuevaPassword()));
        usuario.setRequiereCambioPassword(false);
        usuario.setFechaUltimoCambioPassword(LocalDateTime.now());
        tokenRefreshRepository.revocarTodosDeUsuario(usuario);
        log.info("Contraseña actualizada para: {}", email);
    }

    public void registrarNatural(RegistroNaturalRequest request) {
        validarEmailDisponible(request.email());

        Cliente cliente = Cliente.builder()
                .tipoCliente(TipoCliente.PREPAGO)
                .tipoPersona(TipoPersona.NATURAL)
                .nombre(request.nombre())
                .apellido(request.apellido())
                .emailPrincipal(request.email())
                .telefono(request.telefono())
                .estado("ACTIVO")
                .fechaRegistro(LocalDateTime.now())
                .build();
        clienteRepository.save(cliente);

        String tokenActivacion = generarToken();
        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .rol(Rol.ADMIN_CLIENTE)
                .activo(false)
                .requiere2fa(false)
                .emailVerificado(false)
                .tokenActivacion(tokenActivacion)
                .tokenActivacionExpira(LocalDateTime.now().plusHours(24))
                .idCliente(cliente.getIdCliente())
                .fechaCreacion(LocalDateTime.now())
                .build();
        usuarioRepository.save(usuario);

        emailService.enviarActivacion(request.email(), request.nombre(), tokenActivacion);
        notificacionService.crearParaAdmins("CLIENTE", "Nuevo cliente registrado",
                "El cliente " + request.nombre() + " " + (request.apellido() != null ? request.apellido() : "")
                + " se registró como persona natural (PREPAGO)", usuario.getIdUsuario());
        log.info("Registro natural completado: {}", request.email());
    }

    public void registrarJuridica(RegistroJuridicaRequest request) {
        validarEmailDisponible(request.email());

        TipoCliente tipoCliente;
        try {
            tipoCliente = TipoCliente.valueOf(request.tipoCliente().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Tipo de cliente inválido. Use PREPAGO o POSPAGO", HttpStatus.BAD_REQUEST);
        }

        Cliente cliente = Cliente.builder()
                .tipoCliente(tipoCliente)
                .tipoPersona(TipoPersona.JURIDICA)
                .nit(request.nit())
                .dv(request.dv())
                .razonSocial(request.razonSocial())
                .nombreComercial(request.nombreComercial())
                .representanteLegal(request.representanteLegal())
                .emailPrincipal(request.email())
                .telefono(request.telefono())
                .estado("ACTIVO")
                .fechaRegistro(LocalDateTime.now())
                .build();
        clienteRepository.save(cliente);

        String tokenActivacion = generarToken();
        Usuario usuario = Usuario.builder()
                .nombre(request.razonSocial())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .rol(Rol.ADMIN_CLIENTE)
                .activo(false)
                .requiere2fa(false)
                .emailVerificado(false)
                .tokenActivacion(tokenActivacion)
                .tokenActivacionExpira(LocalDateTime.now().plusHours(24))
                .idCliente(cliente.getIdCliente())
                .fechaCreacion(LocalDateTime.now())
                .build();
        usuarioRepository.save(usuario);

        emailService.enviarActivacion(request.email(), request.razonSocial(), tokenActivacion);
        notificacionService.crearParaAdmins("CLIENTE", "Nuevo cliente registrado",
                "La empresa " + request.razonSocial() + " (NIT: " + request.nit() + ") se registró como cliente "
                + tipoCliente.name().toLowerCase(), usuario.getIdUsuario());
        log.info("Registro jurídico completado: {}", request.email());
    }

    public void activarCuenta(String token) {
        Usuario usuario = usuarioRepository.findByTokenActivacion(token)
                .orElseThrow(() -> new ApiException("Token de activación inválido", HttpStatus.BAD_REQUEST));

        if (LocalDateTime.now().isAfter(usuario.getTokenActivacionExpira())) {
            throw new ApiException("El token de activación ha expirado", HttpStatus.BAD_REQUEST);
        }

        usuario.setActivo(true);
        usuario.setEmailVerificado(true);
        usuario.setTokenActivacion(null);
        usuario.setTokenActivacionExpira(null);
        log.info("Cuenta activada: {}", usuario.getEmail());
    }

    public LoginResponse refresh(RefreshRequest request) {
        TokenRefresh tokenRefresh = tokenRefreshRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new ApiException("Refresh token inválido", HttpStatus.UNAUTHORIZED));

        if (tokenRefresh.isRevocado() || LocalDateTime.now().isAfter(tokenRefresh.getFechaExpiracion())) {
            throw new ApiException("Refresh token expirado o revocado", HttpStatus.UNAUTHORIZED);
        }

        tokenRefresh.setRevocado(true);

        Usuario usuario = tokenRefresh.getUsuario();
        String nuevoToken = jwtUtil.generarToken(usuario);
        String nuevoRefresh = crearRefreshToken(usuario);

        return new LoginResponse(nuevoToken, nuevoRefresh,
                new LoginResponse.UsuarioInfo(
                        usuario.getIdUsuario(), usuario.getEmail(),
                        usuario.getRol().name(), usuario.getNombre(), false));
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        usuarioRepository.findByEmail(request.email()).ifPresent(usuario -> {
            String token = generarToken();
            usuario.setTokenResetPassword(token);
            usuario.setTokenResetExpira(LocalDateTime.now().plusHours(2));
            emailService.enviarResetPassword(request.email(), usuario.getNombre(), token);
            log.info("Token de reset enviado a: {}", request.email());
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByTokenResetPassword(request.token())
                .orElseThrow(() -> new ApiException("Token inválido o expirado", HttpStatus.BAD_REQUEST));

        if (LocalDateTime.now().isAfter(usuario.getTokenResetExpira())) {
            throw new ApiException("El token ha expirado", HttpStatus.BAD_REQUEST);
        }

        usuario.setPassword(passwordEncoder.encode(request.nuevaPassword()));
        usuario.setTokenResetPassword(null);
        usuario.setTokenResetExpira(null);
        tokenRefreshRepository.revocarTodosDeUsuario(usuario);
        log.info("Password actualizado: {}", usuario.getEmail());
    }

    private String crearRefreshToken(Usuario usuario) {
        String token = generarToken();
        tokenRefreshRepository.save(TokenRefresh.builder()
                .usuario(usuario)
                .token(token)
                .fechaExpiracion(LocalDateTime.now().plusNanos(refreshExpiracionMs * 1_000_000L))
                .revocado(false)
                .fechaCreacion(LocalDateTime.now())
                .build());
        return token;
    }

    private void validarEmailDisponible(String email) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new ApiException("El email ya está registrado", HttpStatus.CONFLICT);
        }
    }

    private String generarToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
