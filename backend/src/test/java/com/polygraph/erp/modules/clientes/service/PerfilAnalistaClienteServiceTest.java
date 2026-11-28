package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.ActualizarMiPerfilAnalistaClienteRequest;
import com.polygraph.erp.modules.clientes.dto.MiPerfilAnalistaClienteResponse;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoPersona;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Mi perfil del analista de un cliente")
class PerfilAnalistaClienteServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClienteRepository clienteRepository;

    @InjectMocks
    private PerfilAnalistaClienteService service;

    private Usuario analista;

    @BeforeEach
    void setUp() {
        analista = Usuario.builder().idUsuario(7L).nombre("Luis").apellido("Paz").email("luis@empresa.com")
                .rol(Rol.ANALISTA_CLIENTE).idCliente(3).fechaCreacion(LocalDateTime.of(2026, 1, 5, 9, 0))
                .fechaUltimoCambioPassword(LocalDateTime.now().minusDays(10)).build();
        Cliente cliente = Cliente.builder().idCliente(3).tipoPersona(TipoPersona.JURIDICA).razonSocial("Seguridad Andina SAS").idGestor(2L).build();
        Usuario gestor = Usuario.builder().idUsuario(2L).nombre("Marta").apellido("Gil").email("marta@polygraph.com").rol(Rol.GESTOR).build();
        when(usuarioRepository.findByEmail("luis@empresa.com")).thenReturn(Optional.of(analista));
        when(clienteRepository.findById(3)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(gestor));
    }

    @Test
    @DisplayName("Obtener trae su empresa, su gestor y los días que le quedan a la contraseña")
    void obtiene() {
        MiPerfilAnalistaClienteResponse r = service.obtener("luis@empresa.com");

        assertThat(r.nombreEmpresa()).isEqualTo("Seguridad Andina SAS");
        assertThat(r.nombreGestor()).isEqualTo("Marta Gil");
        assertThat(r.emailGestor()).isEqualTo("marta@polygraph.com");
        assertThat(r.diasParaCambiarPassword()).isEqualTo(20);
        assertThat(r.diasVigenciaPassword()).isEqualTo(30);
    }

    @Test
    @DisplayName("Actualizar cambia solo nombre y apellido (sin espacios sobrantes) y no toca correo ni empresa")
    void actualiza() {
        service.actualizar("luis@empresa.com", new ActualizarMiPerfilAnalistaClienteRequest("  Luis Carlos ", "  "));

        assertThat(analista.getNombre()).isEqualTo("Luis Carlos");
        assertThat(analista.getApellido()).isNull();
        assertThat(analista.getEmail()).isEqualTo("luis@empresa.com");
        assertThat(analista.getIdCliente()).isEqualTo(3);
    }

    @Test
    @DisplayName("Quien no es analista de cliente (ej. el administrador de la empresa) recibe 403")
    void otroRolNoPuede() {
        analista.setRol(Rol.ADMIN_CLIENTE);

        assertThatThrownBy(() -> service.obtener("luis@empresa.com"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
