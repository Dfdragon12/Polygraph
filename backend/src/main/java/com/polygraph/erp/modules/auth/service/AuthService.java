package com.polygraph.erp.modules.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.auth.config.GoogleOAuthProperties;
import com.polygraph.erp.modules.auth.dto.*;
import com.polygraph.erp.modules.auth.entity.TokenRefresh;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.TokenRefreshRepository;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final SaldoServicioClienteRepository saldoServicioClienteRepository;
    private final TokenRefreshRepository tokenRefreshRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final NotificacionService notificacionService;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final ObjectMapper objectMapper;

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

        String nombreCompleto = usuario.getNombre()
                + (usuario.getApellido() != null && !usuario.getApellido().isBlank()
                   ? " " + usuario.getApellido() : "");
        String etiquetaRol = switch (usuario.getRol()) {
            case ADMIN_POLYGRAPH  -> "Administrador";
            case GESTOR           -> "Gestor";
            case ANALISTA_INTERNO -> "Analista interno";
            case PROGRAMADOR      -> "Programador";
            case POLIGRAFISTA     -> "Poligrafista";
            case VISITADOR        -> "Visitador";
            case ADMIN_CLIENTE    -> "Admin cliente";
            case ANALISTA_CLIENTE -> "Analista cliente";
            case EVALUADO         -> "Evaluado";
            default               -> usuario.getRol().name();
        };
        notificacionService.crearParaSesion(
                "Inicio de sesión",
                nombreCompleto + " (" + etiquetaRol + ") ha ingresado al sistema",
                usuario.getIdUsuario()
        );

        return new LoginResponse(token, refreshToken,
                new LoginResponse.UsuarioInfo(
                        usuario.getIdUsuario(), usuario.getEmail(),
                        usuario.getRol().name(), usuario.getNombre(), cambioRequerido,
                        resolverTipoCliente(usuario), resolverPuedeSolicitar(usuario)));
    }

    /** "Continuar con Google" — sirve tanto para registrar (si el email no existe, crea un
     *  cliente natural PREPAGO, igual que registrarNatural pero sin contraseña ni activación por
     *  correo, porque Google ya verificó el email) como para iniciar sesión (si ya existe). */
    public LoginResponse continuarConGoogle(GoogleAuthRequest request) {
        GoogleTokenInfo info = verificarTokenGoogle(request.idToken());

        Usuario usuario = usuarioRepository.findByEmail(info.email()).orElse(null);
        boolean esNuevo = usuario == null;

        if (esNuevo) {
            String nombre = !info.givenName().isBlank() ? info.givenName() : info.email();
            Cliente cliente = Cliente.builder()
                    .tipoCliente(TipoCliente.PREPAGO)
                    .tipoPersona(TipoPersona.NATURAL)
                    .nombre(nombre)
                    .apellido(info.familyName())
                    .emailPrincipal(info.email())
                    .estado("ACTIVO")
                    .idGestor(resolverGestorMenosCargado())
                    .fechaRegistro(LocalDateTime.now())
                    .build();
            clienteRepository.save(cliente);

            usuario = Usuario.builder()
                    .nombre(nombre)
                    .apellido(info.familyName())
                    .email(info.email())
                    // Nunca se usa para iniciar sesión (solo Google) — password NOT NULL en BD,
                    // se puede definir una real después desde "Olvidé mi contraseña" si se quiere.
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .rol(Rol.ADMIN_CLIENTE)
                    .activo(true)
                    .requiere2fa(false)
                    .emailVerificado(true)
                    .idCliente(cliente.getIdCliente())
                    .fechaCreacion(LocalDateTime.now())
                    .build();
            usuarioRepository.save(usuario);

            notificacionService.crearParaAdmins("CLIENTE", "Nuevo cliente registrado",
                    "El cliente " + nombre + " " + (info.familyName() != null ? info.familyName() : "")
                    + " se registró con Google (PREPAGO)", usuario.getIdUsuario());
            log.info("Registro con Google completado: {}", info.email());
        } else if (!Boolean.TRUE.equals(usuario.getActivo())) {
            // Google ya verificó el email, así que no tiene sentido dejarlo esperando el correo
            // de activación que se mandó al registrarse manualmente.
            usuario.setActivo(true);
            usuario.setEmailVerificado(true);
        }

        String token = jwtUtil.generarToken(usuario);
        String refreshToken = crearRefreshToken(usuario);
        usuario.setUltimoAcceso(LocalDateTime.now());
        log.info("Inicio de sesión con Google: {} (nuevo={})", usuario.getEmail(), esNuevo);

        return new LoginResponse(token, refreshToken,
                new LoginResponse.UsuarioInfo(
                        usuario.getIdUsuario(), usuario.getEmail(),
                        usuario.getRol().name(), usuario.getNombre(), false,
                        resolverTipoCliente(usuario), resolverPuedeSolicitar(usuario)));
    }

    private record GoogleTokenInfo(String email, String givenName, String familyName) {}

    /** Verifica el id_token de Google Identity Services contra el endpoint público de Google
     *  (tokeninfo) — evita sumar una librería nueva solo para esto. Confirma que el token es
     *  auténtico, que es para ESTA app (aud) y que el correo está verificado. */
    private GoogleTokenInfo verificarTokenGoogle(String idToken) {
        String clientId = googleOAuthProperties.getClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException("El inicio de sesión con Google no está configurado todavía.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            HttpClient cliente = HttpClient.newHttpClient();
            HttpRequest peticion = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .GET().build();
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new ApiException("Tu sesión de Google no es válida o expiró. Intenta de nuevo.", HttpStatus.UNAUTHORIZED);
            }
            JsonNode nodo = objectMapper.readTree(respuesta.body());
            if (!clientId.equals(nodo.path("aud").asText())) {
                throw new ApiException("Ese token de Google no corresponde a esta aplicación.", HttpStatus.UNAUTHORIZED);
            }
            if (!"true".equals(nodo.path("email_verified").asText())) {
                throw new ApiException("Tu cuenta de Google no tiene el correo verificado.", HttpStatus.UNAUTHORIZED);
            }
            String email = nodo.path("email").asText();
            if (email.isBlank()) {
                throw new ApiException("No se pudo obtener el correo de tu cuenta de Google.", HttpStatus.UNAUTHORIZED);
            }
            return new GoogleTokenInfo(email, nodo.path("given_name").asText(""), nodo.path("family_name").asText(""));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error verificando token de Google: {}", e.getMessage());
            throw new ApiException("No se pudo verificar tu cuenta de Google. Intenta de nuevo.", HttpStatus.UNAUTHORIZED);
        }
    }

    /** Solo aplica a roles del portal cliente (ADMIN_CLIENTE/ANALISTA_CLIENTE); null para roles internos. */
    private String resolverTipoCliente(Usuario usuario) {
        if (usuario.getIdCliente() == null) return null;
        return clienteRepository.findById(usuario.getIdCliente())
                .map(cliente -> cliente.getTipoCliente().name())
                .orElse(null);
    }

    /** Solo aplica a roles del portal cliente; null para roles internos. Si ya puede ver/usar
     *  "Solicitudes": pospago solo una vez tiene un acuerdo de crédito vigente (fila en
     *  ClientePospago con requiereAprobacion=false — eso solo lo deja así un interno, ver
     *  ClienteAdminService.actualizarPospago), prepago solo una vez completó su primera compra
     *  (existe una fila en SaldoServicioCliente, que solo se crea tras una compra aprobada). No es
     *  una aprobación manual aparte — se deriva de datos reales, así que no hay nada que un
     *  gestor tenga que "aprobar" a mano. */
    private Boolean resolverPuedeSolicitar(Usuario usuario) {
        if (usuario.getIdCliente() == null) return null;
        Cliente cliente = clienteRepository.findById(usuario.getIdCliente()).orElse(null);
        if (cliente == null) return null;
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            ClientePospago pospago = clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).orElse(null);
            return pospago != null && !Boolean.TRUE.equals(pospago.getRequiereAprobacion());
        }
        return saldoServicioClienteRepository.existsByCliente_IdCliente(cliente.getIdCliente());
    }

    /** A qué gestor se le asigna un cliente nuevo que se auto-registra — el gestor ACTIVO con
     *  menos clientes ya asignados (reparte la carga, en vez de que todos los registros nuevos le
     *  caigan siempre al mismo). null si no hay ningún gestor activo (el cliente queda sin
     *  gestor, igual que antes — un admin se lo asigna a mano después). */
    private Long resolverGestorMenosCargado() {
        return usuarioRepository.findByRolIn(Set.of(Rol.GESTOR)).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .min(Comparator.comparingLong(g -> clienteRepository.countByIdGestor(g.getIdUsuario())))
                .map(Usuario::getIdUsuario)
                .orElse(null);
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
                .idGestor(resolverGestorMenosCargado())
                .fechaRegistro(LocalDateTime.now())
                .build();
        clienteRepository.save(cliente);

        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .rol(Rol.ADMIN_CLIENTE)
                .activo(true)
                .requiere2fa(false)
                .emailVerificado(true)
                .idCliente(cliente.getIdCliente())
                .fechaCreacion(LocalDateTime.now())
                .build();
        usuarioRepository.save(usuario);

        notificacionService.crearParaAdmins("CLIENTE", "Nuevo cliente registrado",
                "El cliente " + request.nombre() + " " + (request.apellido() != null ? request.apellido() : "")
                + " se registró como persona natural (PREPAGO)", usuario.getIdUsuario());
        log.info("Registro natural completado: {}", request.email());
    }

    public void registrarJuridica(RegistroJuridicaRequest request) {
        validarEmailDisponible(request.email());

        // Antes se podía elegir PREPAGO o POSPAGO libremente al auto-registrarse — pospago sin
        // ninguna revisión de crédito previa. Ahora toda empresa que se registra sola entra
        // siempre como PREPAGO; pasar a pospago requiere que un interno lo gestione después (ver
        // ClienteAdminService.actualizarPospago).
        Cliente cliente = Cliente.builder()
                .tipoCliente(TipoCliente.PREPAGO)
                .tipoPersona(TipoPersona.JURIDICA)
                .nit(request.nit())
                .dv(request.dv())
                .razonSocial(request.razonSocial())
                .nombreComercial(request.nombreComercial())
                .representanteLegal(request.representanteLegal())
                .emailPrincipal(request.email())
                .telefono(request.telefono())
                .estado("ACTIVO")
                .idGestor(resolverGestorMenosCargado())
                .fechaRegistro(LocalDateTime.now())
                .build();
        clienteRepository.save(cliente);

        Usuario usuario = Usuario.builder()
                .nombre(request.razonSocial())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .rol(Rol.ADMIN_CLIENTE)
                .activo(true)
                .requiere2fa(false)
                .emailVerificado(true)
                .idCliente(cliente.getIdCliente())
                .fechaCreacion(LocalDateTime.now())
                .build();
        usuarioRepository.save(usuario);

        notificacionService.crearParaAdmins("CLIENTE", "Nuevo cliente registrado",
                "La empresa " + request.razonSocial() + " (NIT: " + request.nit() + ") se registró como cliente prepago",
                usuario.getIdUsuario());
        log.info("Registro jurídico completado: {}", request.email());
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
                        usuario.getRol().name(), usuario.getNombre(), false,
                        resolverTipoCliente(usuario), resolverPuedeSolicitar(usuario)));
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
