package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsignacionService — analista/poligrafista/visitador solo operan lo asignado a ellos")
class AsignacionServicePropiedadTest {

    @Mock private ServicioSubprocesoRepository asignacionRepository;
    @Mock private HistorialAsignacionService historialAsignacionService;

    @InjectMocks
    private AsignacionService service;

    private Usuario poligrafistaAsignado;
    private Usuario otroPoligrafista;
    private ServicioSubproceso subproceso;

    @BeforeEach
    void setUp() {
        poligrafistaAsignado = Usuario.builder().idUsuario(10L).rol(Rol.POLIGRAFISTA).nombre("Asignado").build();
        otroPoligrafista = Usuario.builder().idUsuario(11L).rol(Rol.POLIGRAFISTA).nombre("Otro").build();
        TipoProgreso tipo = TipoProgreso.builder().idTipoProgreso(5).nombreProgreso("Poligrafía").rolResponsable(Rol.POLIGRAFISTA).build();
        subproceso = ServicioSubproceso.builder().id(500L).tipoProgreso(tipo)
                .usuarioAsignado(poligrafistaAsignado).estado(EstadoSubproceso.EN_EJECUCION).build();
        when(asignacionRepository.findById(500L)).thenReturn(Optional.of(subproceso));
    }

    @Test
    @DisplayName("Completar el subproceso de otro poligrafista devuelve 403 y no cambia su estado")
    void completar_subprocesoAjeno_403() {
        assertThatThrownBy(() -> service.completar(500L, otroPoligrafista, Set.of(Rol.POLIGRAFISTA)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(subproceso.getEstado()).isEqualTo(EstadoSubproceso.EN_EJECUCION);
    }

    @Test
    @DisplayName("Ver el historial de un subproceso ajeno devuelve 403")
    void historial_subprocesoAjeno_403() {
        assertThatThrownBy(() -> service.historial(500L, otroPoligrafista, Set.of(Rol.POLIGRAFISTA)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("El poligrafista asignado sí puede ver su historial")
    void historial_propio_ok() {
        when(historialAsignacionService.listar(500L)).thenReturn(java.util.List.of());
        assertThat(service.historial(500L, poligrafistaAsignado, Set.of(Rol.POLIGRAFISTA))).isEmpty();
    }

    @Test
    @DisplayName("Un gestor no puede aprobar, devolver ni cancelar el subproceso de un cliente de otro gestor")
    void gestorDeOtroCliente_403() {
        subproceso.setServicio(Servicio.builder().idServicio(77).cliente(Cliente.builder().idGestor(1L).build()).build());
        subproceso.setEstado(EstadoSubproceso.FINALIZADO);
        Usuario otroGestor = Usuario.builder().idUsuario(2L).rol(Rol.GESTOR).nombre("Otro").build();
        Set<Rol> todos = Set.of(Rol.POLIGRAFISTA, Rol.ANALISTA_INTERNO);

        for (org.junit.jupiter.api.function.Executable accion : java.util.List.<org.junit.jupiter.api.function.Executable>of(
                () -> service.aprobar(500L, otroGestor, todos),
                () -> service.cancelar(500L, new com.polygraph.erp.modules.gestor.dto.CancelarSubprocesoRequest(1, null), otroGestor, todos),
                () -> service.historial(500L, otroGestor, todos))) {
            assertThatThrownBy(accion::execute)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(subproceso.getEstado()).isEqualTo(EstadoSubproceso.FINALIZADO);
    }

    @Test
    @DisplayName("Una cita ya agendada no se edita a mano: asignar y desasignar se rechazan; solo se reprograma o reasigna con motivo (SE-07)")
    void citaAgendada_noSeEditaLibremente() {
        Usuario programador = Usuario.builder().idUsuario(20L).rol(Rol.PROGRAMADOR).build();
        Set<Rol> roles = Set.of(Rol.POLIGRAFISTA, Rol.VISITADOR);
        assertThatThrownBy(() -> service.asignar(500L,
                new com.polygraph.erp.modules.gestor.dto.AsignarRequest(11L, java.time.LocalDateTime.now().plusDays(1), null, null, null), programador, roles))
                .isInstanceOf(ApiException.class).hasMessageContaining("no se edita")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.desasignar(500L, programador, roles))
                .isInstanceOf(ApiException.class).hasMessageContaining("no se edita")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        subproceso.setEstado(EstadoSubproceso.REPROGRAMADO);
        assertThatThrownBy(() -> service.desasignar(500L, programador, roles)).isInstanceOf(ApiException.class).hasMessageContaining("no se edita");
        assertThat(subproceso.getUsuarioAsignado()).isSameAs(poligrafistaAsignado);
    }
}
