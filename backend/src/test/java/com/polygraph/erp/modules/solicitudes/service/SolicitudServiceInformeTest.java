package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SolicitudService — el cliente pide el PDF de cada subproceso aprobado (se valida antes de encolar)")
class SolicitudServiceInformeTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ServicioSubprocesoRepository servicioSubprocesoRepository;

    @InjectMocks private SolicitudService service;

    private Servicio servicio;
    private ServicioSubproceso poligrafia;
    private final UserDetails sesion = User.withUsername("cliente@test.com").password("x").roles("ADMIN_CLIENTE").build();

    @BeforeEach
    void setUp() {
        Cliente cliente = Cliente.builder().idCliente(7).build();
        servicio = Servicio.builder().idServicio(40).cliente(cliente).estado(EstadoServicio.PUBLICADO)
                .candidato(Candidato.builder().cedula("1000222333").build()).build();
        poligrafia = ServicioSubproceso.builder().id(900L).servicio(servicio).estado(EstadoSubproceso.PUBLICADO)
                .tipoProgreso(TipoProgreso.builder().nombreProgreso("Poligrafía").requiereDocumento(true).build()).build();
        when(servicioRepository.findById(40)).thenReturn(Optional.of(servicio));
        when(servicioSubprocesoRepository.findById(900L)).thenReturn(Optional.of(poligrafia));
        when(usuarioRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(
                Usuario.builder().idUsuario(3L).email("cliente@test.com").rol(Rol.ADMIN_CLIENTE).idCliente(7).build()));
    }

    @Test
    @DisplayName("Solicitud publicada y subproceso aprobado: autoriza con un nombre legible y la fecha de publicación")
    void publicado_autoriza() {
        poligrafia.setFechaPublicado(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        var informe = service.validarInforme(40, 900L, sesion);
        assertThat(informe.idServicioSubproceso()).isEqualTo(900L);
        assertThat(informe.nombreArchivo()).isEqualTo("Informe_Poligrafía_1000222333.pdf");
        assertThat(informe.fechaPublicado()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    @DisplayName("Mientras la solicitud no está publicada, no hay informe (409)")
    void noPublicado_409() {
        servicio.setEstado(EstadoServicio.FINALIZADO);
        assertThatThrownBy(() -> service.validarInforme(40, 900L, sesion))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("La solicitud de otro cliente: 403")
    void otroCliente_403() {
        servicio.setCliente(Cliente.builder().idCliente(99).build());
        assertThatThrownBy(() -> service.validarInforme(40, 900L, sesion))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Un subproceso de otra solicitud no se puede pedir con este id de servicio: 404")
    void subprocesoDeOtraSolicitud_404() {
        poligrafia.setServicio(Servicio.builder().idServicio(41).build());
        assertThatThrownBy(() -> service.validarInforme(40, 900L, sesion))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
