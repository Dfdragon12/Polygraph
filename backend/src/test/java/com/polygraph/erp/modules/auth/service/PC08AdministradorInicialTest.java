package com.polygraph.erp.modules.auth.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.shared.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PC-08: una base nueva no tiene con quién entrar. El primer administrador sale de variables de entorno, sin contraseña por defecto, y solo si no hay ninguno. */
@DisplayName("PC-08 — administrador inicial")
class PC08AdministradorInicialTest {

    private UsuarioRepository usuarios;
    private PasswordEncoder codificador;
    private AdministradorInicial inicial;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        codificador = mock(PasswordEncoder.class);
        when(codificador.encode(any())).thenAnswer(i -> "{hash}" + i.getArgument(0));
        inicial = new AdministradorInicial(usuarios, codificador);
        configurar("admin@polygraph.co", "ClaveInicial#2026", "Administrador");
    }

    private void configurar(String email, String password, String nombre) {
        ReflectionTestUtils.setField(inicial, "email", email);
        ReflectionTestUtils.setField(inicial, "password", password);
        ReflectionTestUtils.setField(inicial, "nombre", nombre);
    }

    @Test
    @DisplayName("Base sin administrador y con las variables: lo crea activo, con la contraseña cifrada y el cambio obligatorio en el primer ingreso")
    void creaElPrimero() {
        assertThat(inicial.crearSiHaceFalta()).isTrue();
        ArgumentCaptor<Usuario> c = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(c.capture());
        Usuario u = c.getValue();
        assertThat(u.getRol()).isEqualTo(Rol.ADMIN_POLYGRAPH);
        assertThat(u.getEmail()).isEqualTo("admin@polygraph.co");
        assertThat(u.getActivo()).isTrue();
        assertThat(u.getRequiereCambioPassword()).isTrue();
        assertThat(u.getPassword()).isEqualTo("{hash}ClaveInicial#2026").isNotEqualTo("ClaveInicial#2026");
    }

    @Test
    @DisplayName("Si ya hay un administrador no toca nada, aunque las variables sigan puestas")
    void conAdministradorNoHaceNada() {
        when(usuarios.existsByRol(Rol.ADMIN_POLYGRAPH)).thenReturn(true);
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Sin ADMIN_EMAIL o sin ADMIN_PASSWORD no se crea nada (no hay contraseña por defecto)")
    void sinVariablesNoCrea() {
        configurar("", "", "Administrador");
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        configurar("admin@polygraph.co", "", "Administrador");
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        configurar("", "ClaveInicial#2026", "Administrador");
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Contraseña de menos de 8 caracteres, correo inválido o un correo que ya es de otro usuario: no crea")
    void datosInvalidos() {
        configurar("admin@polygraph.co", "corta", "Administrador");
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        configurar("no-es-un-correo", "ClaveInicial#2026", "Administrador");
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        configurar("admin@polygraph.co", "ClaveInicial#2026", "Administrador");
        when(usuarios.existsByEmail("admin@polygraph.co")).thenReturn(true);
        assertThat(inicial.crearSiHaceFalta()).isFalse();
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Un fallo al revisar no impide que el servidor arranque")
    void fallarNoRompeElArranque() {
        when(usuarios.existsByRol(Rol.ADMIN_POLYGRAPH)).thenThrow(new IllegalStateException("sin base"));
        inicial.alArrancar();   // no lanza
    }
}
