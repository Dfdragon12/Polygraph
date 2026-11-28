package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoPersona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Reasignación de clientes cuando un gestor deja de atender")
class ReasignacionGestorServiceTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionService notificacionService;

    @InjectMocks
    private ReasignacionGestorService service;

    private Usuario saliente;
    private Usuario andrea;
    private Usuario francisca;

    @BeforeEach
    void setUp() {
        saliente = gestor(6L, "Carlos", false);
        andrea = gestor(5L, "Andrea", true);
        francisca = gestor(24L, "Francisca", true);
    }

    @Test
    @DisplayName("Reparte los clientes entre los gestores activos, empezando por el menos cargado")
    void reparteAlMenosCargado() {
        List<Cliente> clientes = List.of(cliente(1), cliente(2), cliente(3));
        when(clienteRepository.findByIdGestor(6L)).thenReturn(clientes);
        when(usuarioRepository.findByRolIn(anyCollection())).thenReturn(List.of(saliente, andrea, francisca));
        when(clienteRepository.countByIdGestor(5L)).thenReturn(2L);
        when(clienteRepository.countByIdGestor(24L)).thenReturn(0L);

        int movidos = service.reasignarClientesDe(saliente);

        // Francisca (0) recibe los dos primeros; al empatar en 2, el tercero va a Andrea.
        assertThat(movidos).isEqualTo(3);
        assertThat(clientes).extracting(Cliente::getIdGestor).containsExactly(24L, 24L, 5L);
        verify(clienteRepository).saveAll(clientes);
    }

    @Test
    @DisplayName("Avisa a cada cliente, a cada gestor que recibe y a los administradores")
    void notificaATodos() {
        List<Cliente> clientes = List.of(cliente(1), cliente(2));
        when(clienteRepository.findByIdGestor(6L)).thenReturn(clientes);
        when(usuarioRepository.findByRolIn(anyCollection())).thenReturn(List.of(saliente, andrea));

        service.reasignarClientesDe(saliente);

        verify(notificacionService).crearParaCliente(eq(1), eq("GESTOR_ASIGNADO"), anyString(), contains("Andrea"));
        verify(notificacionService).crearParaCliente(eq(2), eq("GESTOR_ASIGNADO"), anyString(), contains("Andrea"));
        verify(notificacionService).crearParaUsuario(eq(5L), eq("CLIENTE_ASIGNADO"), contains("2 clientes"), anyString());
        verify(notificacionService).crearParaAdmins(eq("USUARIO"), eq("Clientes reasignados"), contains("Andrea (2)"));
    }

    @Test
    @DisplayName("Nunca le asigna clientes a un gestor inactivo ni al que sale")
    void ignoraInactivos() {
        Usuario inactivo = gestor(9L, "Inactivo", false);
        List<Cliente> clientes = List.of(cliente(1));
        when(clienteRepository.findByIdGestor(6L)).thenReturn(clientes);
        when(usuarioRepository.findByRolIn(anyCollection())).thenReturn(List.of(saliente, inactivo, andrea));
        when(clienteRepository.countByIdGestor(5L)).thenReturn(50L);

        service.reasignarClientesDe(saliente);

        assertThat(clientes.get(0).getIdGestor()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Sin otro gestor activo no mueve nada y avisa a los administradores")
    void sinGestorDisponible() {
        List<Cliente> clientes = List.of(cliente(1));
        when(clienteRepository.findByIdGestor(6L)).thenReturn(clientes);
        when(usuarioRepository.findByRolIn(anyCollection())).thenReturn(List.of(saliente));

        int movidos = service.reasignarClientesDe(saliente);

        assertThat(movidos).isZero();
        assertThat(clientes.get(0).getIdGestor()).isEqualTo(6L);
        verify(notificacionService).crearParaAdmins(eq("USUARIO"), eq("Clientes sin gestor activo"), anyString());
        verify(notificacionService, never()).crearParaCliente(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Un gestor sin clientes no genera ningún aviso")
    void sinClientes() {
        when(clienteRepository.findByIdGestor(6L)).thenReturn(List.of());

        assertThat(service.reasignarClientesDe(saliente)).isZero();
        verify(notificacionService, never()).crearParaAdmins(anyString(), anyString(), anyString());
    }

    private Usuario gestor(Long id, String nombre, boolean activo) {
        return Usuario.builder().idUsuario(id).nombre(nombre).rol(Rol.GESTOR).activo(activo)
                .email(nombre.toLowerCase() + "@test.com").build();
    }

    private Cliente cliente(int id) {
        return Cliente.builder().idCliente(id).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Empresa " + id).idGestor(6L).build();
    }
}
