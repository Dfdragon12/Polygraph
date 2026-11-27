package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.CategoriaNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.TipoNovedadRepository;
import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PC-04 b): «Solicitar corrección» crea una novedad del subproceso publicado para el gestor; el cliente no la ve. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PC-04 — la corrección pedida queda como novedad")
class PC04CorreccionNovedadTest {

    @Mock private NovedadSubprocesoRepository novedadRepository;
    @Mock private TipoNovedadRepository tipoNovedadRepository;

    @InjectMocks
    private NovedadSubprocesoService servicio;

    private final Usuario johana = Usuario.builder().idUsuario(7L).rol(Rol.ANALISTA_INTERNO).nombre("Johana").build();
    private ServicioSubproceso sp;
    private TipoNovedad tipo;

    @BeforeEach
    void setUp() {
        sp = ServicioSubproceso.builder().id(9L).estado(EstadoSubproceso.PUBLICADO).usuarioAsignado(johana)
                .tipoProgreso(TipoProgreso.builder().idTipoProgreso(1).nombreProgreso("Antecedentes").rolResponsable(Rol.ANALISTA_INTERNO).build()).build();
        tipo = TipoNovedad.builder().clave("CORRECCION_DATO_ERRADO").nombre("Corrección: dato errado").activo(true).visibleCliente(false)
                .categoria(CategoriaNovedad.builder().clave("CORRECCION").activo(true).build()).roles(Set.of(Rol.ANALISTA_INTERNO)).build();
        lenient().when(tipoNovedadRepository.findByClave("CORRECCION_DATO_ERRADO")).thenReturn(Optional.of(tipo));
        lenient().when(novedadRepository.save(any(NovedadSubproceso.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Un informe PUBLICADO admite la solicitud (registrar() no lo admitía); la novedad queda del tipo elegido y el cliente no la ve")
    void creaLaNovedadDeUnInformePublicado() {
        NovedadSubproceso n = servicio.registrarCorreccion(sp, "CORRECCION_DATO_ERRADO", "Dato errado: la fecha de expedición quedó mal.", johana);
        assertThat(n.getTipo().getClave()).isEqualTo("CORRECCION_DATO_ERRADO");
        assertThat(n.getTipo().getVisibleCliente()).isFalse();
        assertThat(n.getDescripcion()).isEqualTo("Dato errado: la fecha de expedición quedó mal.");
        assertThat(n.getRegistradoPor()).isSameAs(johana);
        assertThat(n.getServicioSubproceso()).isSameAs(sp);
    }

    @Test
    @DisplayName("Solo quien ejecutó el informe puede pedirla, y solo si está publicado")
    void soloElEjecutorYSoloPublicado() {
        Usuario otro = Usuario.builder().idUsuario(8L).rol(Rol.ANALISTA_INTERNO).build();
        assertThatThrownBy(() -> servicio.registrarCorreccion(sp, "CORRECCION_DATO_ERRADO", "x", otro))
                .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        Usuario gestor = Usuario.builder().idUsuario(5L).rol(Rol.GESTOR).build();
        assertThatThrownBy(() -> servicio.registrarCorreccion(sp, "CORRECCION_DATO_ERRADO", "x", gestor)).isInstanceOf(ApiException.class);
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        assertThatThrownBy(() -> servicio.registrarCorreccion(sp, "CORRECCION_DATO_ERRADO", "x", johana))
                .isInstanceOf(ApiException.class).hasMessageContaining("publicado");
        verify(novedadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un tipo inactivo o que no aplica a este rol no se puede usar: se pide elegir otro")
    void tipoInactivoONoAplica() {
        tipo.setActivo(false);
        assertThatThrownBy(() -> servicio.registrarCorreccion(sp, "CORRECCION_DATO_ERRADO", "x", johana)).isInstanceOf(ApiException.class).hasMessageContaining("no está disponible");
        tipo.setActivo(true);
        tipo.setRoles(Set.of(Rol.POLIGRAFISTA));
        assertThatThrownBy(() -> servicio.registrarCorreccion(sp, "CORRECCION_DATO_ERRADO", "x", johana)).isInstanceOf(ApiException.class).hasMessageContaining("no está disponible");
        when(tipoNovedadRepository.findByClave("CORRECCION_INEXISTENTE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrarCorreccion(sp, "CORRECCION_INEXISTENTE", "x", johana)).isInstanceOf(ApiException.class);
    }
}
