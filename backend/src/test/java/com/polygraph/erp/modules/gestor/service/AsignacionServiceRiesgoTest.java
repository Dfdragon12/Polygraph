package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService.ResumenSubproceso;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Mis Asignaciones — riesgo de cada fila en Por revisar y Aprobadas")
class AsignacionServiceRiesgoTest {

    @Mock private ServicioSubprocesoRepository asignacionRepository;
    @Mock private CalificacionRiesgoService calificacionRiesgoService;
    @Mock private com.polygraph.erp.shared.service.TiempoServicioService tiempoServicioService;
    @org.mockito.Spy private java.time.Clock reloj = java.time.Clock.systemUTC();
    @Mock private com.polygraph.erp.shared.service.CalendarioHabilService calendario;

    @InjectMocks
    private AsignacionService service;

    private final Usuario analista = Usuario.builder().idUsuario(5L).rol(Rol.ANALISTA_INTERNO).build();
    private ServicioSubproceso subproceso;

    @BeforeEach
    void setUp() {
        Candidato c = Candidato.builder().idCandidato(1L).nombres("Ana").apellidos("Ruiz").cedula("111").build();
        Servicio s = Servicio.builder().idServicio(9).candidato(c).cargo("Vigilante").build();
        TipoProgreso t = TipoProgreso.builder().idTipoProgreso(2).nombreProgreso("Antecedentes")
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(true).versionPlantilla(1).build();
        subproceso = ServicioSubproceso.builder().id(77L).servicio(s).tipoProgreso(t).estado(EstadoSubproceso.FINALIZADO)
                .usuarioAsignado(analista).versionPlantilla(1).build();
        when(asignacionRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(subproceso)));
    }

    private List<AsignacionResponse> listar(EstadoSubproceso estado, Usuario ejecutor) {
        return service.listar(estado, null, 5L, null, null, null, null, null, PageRequest.of(0, 10), null, null, null, ejecutor)
                .getContent();
    }

    @Test
    @DisplayName("En Por revisar el ejecutor recibe nivel, puntaje y respuesta crítica de cada subproceso")
    void porRevisarTraeRiesgo() {
        when(calificacionRiesgoService.resumenSubproceso(eq(77L), any()))
                .thenReturn(new ResumenSubproceso(true, "REALIZADO", "MEDIO", new BigDecimal("55.0"), true, null, true, null));

        AsignacionResponse fila = listar(EstadoSubproceso.FINALIZADO, analista).get(0);

        assertThat(fila.riesgoCalifica()).isTrue();
        assertThat(fila.riesgoNivel()).isEqualTo("MEDIO");
        assertThat(fila.riesgoPuntaje()).isEqualByComparingTo("55.0");
        assertThat(fila.riesgoCritico()).isTrue();
    }

    @Test
    @DisplayName("En Aprobadas también; y si el riesgo no se puede calcular la fila sale igual, sin él")
    void aprobadasYSinRiesgoNoRompe() {
        subproceso.setEstado(EstadoSubproceso.PUBLICADO);
        when(calificacionRiesgoService.resumenSubproceso(any(), any())).thenThrow(new ApiException("sin acceso", HttpStatus.FORBIDDEN));

        AsignacionResponse fila = listar(EstadoSubproceso.PUBLICADO, analista).get(0);

        assertThat(fila.id()).isEqualTo(77L);
        assertThat(fila.riesgoCalifica()).isFalse();
        assertThat(fila.riesgoNivel()).isNull();
    }

    @Test
    @DisplayName("Quien no es ejecutor (gestor, programador) no recibe riesgo ni se calcula")
    void otrosRolesNoRecibenRiesgo() {
        AsignacionResponse fila = listar(EstadoSubproceso.FINALIZADO, null).get(0);

        assertThat(fila.riesgoNivel()).isNull();
        verify(calificacionRiesgoService, never()).resumenSubproceso(any(), any());
    }
}
