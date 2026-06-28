package com.polygraph.erp.security;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil — Pruebas de generación y validación de tokens")
class JwtUtilTest {

    private static final String SECRET =
            "test-secret-key-for-unit-tests-minimum-256-bits-long-pad-here";
    private static final long EXPIRACION_MS = 86_400_000L;   // 1 día

    private JwtUtil jwtUtil;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secreto", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiracionMs", EXPIRACION_MS);

        usuario = Usuario.builder()
                .idUsuario(42L)
                .nombre("Laura")
                .email("laura@polygraph.com")
                .password("hashed")
                .rol(Rol.GESTOR)
                .activo(true)
                .emailVerificado(true)
                .build();
    }

    // ── Generación ───────────────────────────────────────────────

    @Test
    @DisplayName("generarToken retorna un token no nulo ni vacío")
    void generar_token_no_es_nulo() {
        String token = jwtUtil.generarToken(usuario);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Token generado contiene el email del usuario como subject")
    void token_contiene_email_correcto() {
        String token = jwtUtil.generarToken(usuario);
        String emailExtraido = jwtUtil.extraerEmail(token);
        assertThat(emailExtraido).isEqualTo("laura@polygraph.com");
    }

    @Test
    @DisplayName("Token recién generado pasa la validación")
    void token_recien_generado_es_valido() {
        String token = jwtUtil.generarToken(usuario);
        boolean valido = jwtUtil.validarToken(token, usuario);
        assertThat(valido).isTrue();
    }

    @Test
    @DisplayName("Token generado para un usuario no valida contra otro usuario")
    void token_de_otro_usuario_no_valida() {
        String token = jwtUtil.generarToken(usuario);

        Usuario otroUsuario = Usuario.builder()
                .idUsuario(99L)
                .email("impostor@test.com")
                .password("hashed")
                .rol(Rol.ADMIN_CLIENTE)
                .activo(true)
                .emailVerificado(true)
                .build();

        boolean valido = jwtUtil.validarToken(token, otroUsuario);
        assertThat(valido).isFalse();
    }

    // ── Expiración ───────────────────────────────────────────────

    @Test
    @DisplayName("Token con expiración de 0ms se rechaza inmediatamente")
    void token_expirado_no_es_valido() {
        ReflectionTestUtils.setField(jwtUtil, "expiracionMs", 0L);
        String token = jwtUtil.generarToken(usuario);

        // Con expiración = 0, el token expira de inmediato
        boolean valido = jwtUtil.validarToken(token, usuario);
        assertThat(valido).isFalse();
    }

    // ── Token manipulado ─────────────────────────────────────────

    @Test
    @DisplayName("Token con firma modificada no supera la validación")
    void token_con_firma_modificada_no_valida() {
        String tokenOriginal = jwtUtil.generarToken(usuario);
        // Corromper la firma reemplazando los últimos caracteres
        String tokenManipulado = tokenOriginal.substring(0, tokenOriginal.length() - 5) + "XXXXX";

        boolean valido = jwtUtil.validarToken(tokenManipulado, usuario);
        assertThat(valido).isFalse();
    }

    @Test
    @DisplayName("Token con formato inválido (sin puntos) no supera la validación")
    void token_formato_invalido_no_valida() {
        String tokenMalformado = "esto-no-es-un-jwt-valido";

        boolean valido = jwtUtil.validarToken(tokenMalformado, usuario);
        assertThat(valido).isFalse();
    }

    @Test
    @DisplayName("Token nulo no supera la validación")
    void token_nulo_no_valida() {
        boolean valido = jwtUtil.validarToken(null, usuario);
        assertThat(valido).isFalse();
    }

    // ── Consistencia de claims ───────────────────────────────────

    @Test
    @DisplayName("Dos tokens generados para el mismo usuario tienen el mismo subject")
    void dos_tokens_mismo_usuario_mismo_subject() throws InterruptedException {
        String token1 = jwtUtil.generarToken(usuario);
        Thread.sleep(10); // Asegura distinto iat
        String token2 = jwtUtil.generarToken(usuario);

        assertThat(jwtUtil.extraerEmail(token1)).isEqualTo(jwtUtil.extraerEmail(token2));
    }
}
