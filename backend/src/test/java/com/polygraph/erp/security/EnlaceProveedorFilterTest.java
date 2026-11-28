package com.polygraph.erp.security;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService.Sesion;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** PF-09 · El filtro del proveedor externo: sin cabecera no toca nada; con un enlace malo corta; con uno bueno solo deja pasar la evaluación. */
class EnlaceProveedorFilterTest {

    private static final String TOKEN = "t".repeat(43);
    private final EnlaceProveedorService enlaces = mock(EnlaceProveedorService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FilterChain cadena = mock(FilterChain.class);
    private EnlaceProveedorFilter filtro;

    @BeforeEach
    void preparar() {
        filtro = new EnlaceProveedorFilter(enlaces, usuarios);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest pedido(String metodo, String ruta, boolean conEnlace) {
        MockHttpServletRequest r = new MockHttpServletRequest(metodo, ruta);
        r.setRemoteAddr("10.0.0.1");
        if (conEnlace) r.addHeader(EnlaceProveedorFilter.CABECERA, TOKEN);
        return r;
    }

    private void enlaceValido(long evaluacion) {
        when(enlaces.resolver(TOKEN)).thenReturn(Optional.of(new Sesion(5L, evaluacion, 7L, OffsetDateTime.now().plusHours(10))));
        Usuario poligrafista = Usuario.builder().idUsuario(7L).email("p@x.co").rol(Rol.POLIGRAFISTA).activo(true).build();
        when(usuarios.findById(7L)).thenReturn(Optional.of(poligrafista));
    }

    @Test
    void sinCabeceraNoHaceNada() throws Exception {
        filtro.doFilter(pedido("GET", "/api/v1/gestor/asignaciones", false), new MockHttpServletResponse(), cadena);
        verify(cadena).doFilter(any(), any());
        verifyNoInteractions(enlaces);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void enlaceMaloEs401ConSuMotivoYNoSigue() throws Exception {
        when(enlaces.resolver(TOKEN)).thenReturn(Optional.empty());
        when(enlaces.motivoInvalido(TOKEN)).thenReturn("ENLACE_ENVIADO");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filtro.doFilter(pedido("GET", "/api/v1/proveedor/sesion", true), resp, cadena);
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("ENLACE_ENVIADO"));
        verify(cadena, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void enlaceBuenoTrabajaComoElPoligrafistaDeEsaEvaluacion() throws Exception {
        enlaceValido(44);
        MockHttpServletRequest req = pedido("PUT", "/api/v1/gestor/formularios/subproceso/44", true);
        filtro.doFilter(req, new MockHttpServletResponse(), cadena);
        verify(cadena).doFilter(any(), any());
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("p@x.co", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_POLIGRAFISTA")));
        assertNotNull(req.getAttribute(EnlaceProveedorFilter.ATRIBUTO));
        verify(enlaces).registrarUso(5L);
    }

    @Test
    void enlaceBuenoNoAbreOtraEvaluacionNiOtrasPantallas() throws Exception {
        enlaceValido(44);
        for (String ruta : new String[]{"/api/v1/gestor/formularios/subproceso/45", "/api/v1/gestor/asignaciones", "/api/v1/auth/me"}) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filtro.doFilter(pedido("GET", ruta, true), resp, cadena);
            assertEquals(403, resp.getStatus(), ruta);
        }
        verify(cadena, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void siElPoligrafistaEstaInactivoElEnlaceNoSirve() throws Exception {
        when(enlaces.resolver(TOKEN)).thenReturn(Optional.of(new Sesion(5L, 44L, 7L, OffsetDateTime.now().plusHours(10))));
        when(usuarios.findById(7L)).thenReturn(Optional.of(Usuario.builder().idUsuario(7L).email("p@x.co").rol(Rol.POLIGRAFISTA).activo(false).build()));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filtro.doFilter(pedido("GET", "/api/v1/gestor/formularios/subproceso/44", true), resp, cadena);
        assertEquals(401, resp.getStatus());
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    void demasiadosEnlacesMalosDeUnaMismaIpSeFrenan() throws Exception {
        when(enlaces.resolver(TOKEN)).thenReturn(Optional.empty());
        when(enlaces.motivoInvalido(TOKEN)).thenReturn("ENLACE_DESCONOCIDO");
        int ultimo = 0;
        for (int i = 0; i < 25; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filtro.doFilter(pedido("GET", "/api/v1/proveedor/sesion", true), resp, cadena);
            ultimo = resp.getStatus();
        }
        assertEquals(429, ultimo);
    }
}
