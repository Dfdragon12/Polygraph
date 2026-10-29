package com.polygraph.erp.security;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Filtro JWT — un usuario desactivado pierde el acceso aunque su token siga vigente")
class JwtAuthenticationFilterActivoTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void ejecutar(boolean activo) throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        CustomUserDetailsService detalles = mock(CustomUserDetailsService.class);
        Usuario usuario = Usuario.builder().idUsuario(1L).email("a@test.com").rol(Rol.GESTOR).activo(activo).build();
        when(jwtUtil.extraerEmail("tok")).thenReturn("a@test.com");
        when(detalles.loadUserByUsername("a@test.com")).thenReturn(usuario);
        when(jwtUtil.validarToken(any(), any())).thenReturn(true); // el token en sí es válido y no ha vencido
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        FilterChain cadena = mock(FilterChain.class);
        new JwtAuthenticationFilter(jwtUtil, detalles).doFilter(req, new MockHttpServletResponse(), cadena);
        verify(cadena).doFilter(any(), any());
    }

    @Test
    @DisplayName("Usuario activo con token válido: queda autenticado")
    void activoAutentica() throws Exception {
        ejecutar(true);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("Usuario desactivado con token válido: NO queda autenticado")
    void desactivadoNoAutentica() throws Exception {
        ejecutar(false);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
