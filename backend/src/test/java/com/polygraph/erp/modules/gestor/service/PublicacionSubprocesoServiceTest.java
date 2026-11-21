package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.PublicarSubprocesoRequest;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PB-02: publicar un subproceso con conclusión y calificación, siempre con historial. */
class PublicacionSubprocesoServiceTest {

    private AsignacionService asignacion;
    private CalificacionRiesgoService riesgo;
    private JdbcTemplate jdbc;
    private PublicacionSubprocesoService servicio;
    private final Usuario gestor = Usuario.builder().idUsuario(5L).nombre("Andrea").rol(Rol.GESTOR).build();
    private ServicioSubproceso sp;

    @BeforeEach
    void setUp() {
        asignacion = mock(AsignacionService.class);
        riesgo = mock(CalificacionRiesgoService.class);
        jdbc = mock(JdbcTemplate.class);
        servicio = new PublicacionSubprocesoService(asignacion, riesgo, jdbc, mock(NotificacionRepository.class), mock(UsuarioRepository.class));
        sp = ServicioSubproceso.builder().id(9L).estado(EstadoSubproceso.FINALIZADO).conclusion("Se confirmaron 2 de 2 experiencias.").build();
        when(asignacion.obtenerOFallar(9L)).thenReturn(sp);
        when(riesgo.resumenSubproceso(eq(9L), any())).thenReturn(new CalificacionRiesgoService.ResumenSubproceso(
                true, "REALIZADO", "BAJO", new BigDecimal("15"), false, null, true, null));
    }

    @Test
    @DisplayName("Sin marcar «revisé el informe» no se publica")
    void exigeLaCasillaDeRevision() {
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, false), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Confirma que revisaste");
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    @Test
    @DisplayName("Publica con la conclusión del ejecutor y el nivel sugerido; queda el historial de la publicación")
    void publicaConLoSugerido() {
        servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        verify(asignacion).aprobar(eq(9L), eq(gestor), any());
        assertThat(sp.getConclusionPublicada()).isEqualTo("Se confirmaron 2 de 2 experiencias.");
        assertThat(sp.getNivelPublicado()).isEqualTo("BAJO");
        assertThat(sp.getPuntajePublicado()).isEqualByComparingTo("15");
        assertThat(sp.getPublicadoPor()).isEqualTo(5L);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("PUBLICAR"), any(), eq("BAJO"), any(), eq(5L), any());
    }

    @Test
    @DisplayName("Si el gestor cambia la conclusión, el texto original queda en el historial")
    void cambiarLaConclusionGuardaLaOriginal() {
        servicio.publicar(9L, new PublicarSubprocesoRequest("Texto ajustado por el gestor.", null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        assertThat(sp.getConclusionPublicada()).isEqualTo("Texto ajustado por el gestor.");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("CAMBIO_CONCLUSION"),
                eq("Se confirmaron 2 de 2 experiencias."), eq("Texto ajustado por el gestor."), any(), eq(5L), any());
    }

    @Test
    @DisplayName("Cambiar la calificación sugerida exige motivo; con motivo queda anterior, nuevo y por qué")
    void cambiarElNivelExigeMotivo() {
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, "MEDIO", " ", true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("motivo");
        verify(asignacion, never()).aprobar(any(), any(), any());

        servicio.publicar(9L, new PublicarSubprocesoRequest(null, "MEDIO", "Hubo una inconsistencia menor", true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        assertThat(sp.getNivelPublicado()).isEqualTo("MEDIO");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("CAMBIO_NIVEL"),
                eq("BAJO"), eq("MEDIO"), eq("Hubo una inconsistencia menor"), eq(5L), any());
    }

    @Test
    @DisplayName("Solo se publica lo que está en Finalizado; sin conclusión tampoco")
    void soloDesdeFinalizadoYConConclusion() {
        sp.setEstado(EstadoSubproceso.EN_EJECUCION);
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Finalizado");
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setConclusion(null);
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("conclusión");
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    @Test
    @DisplayName("«Solicitar corrección» solo en un subproceso publicado y con motivo")
    void solicitarCorreccion() {
        Usuario ejecutor = Usuario.builder().idUsuario(7L).nombre("Johana").rol(Rol.ANALISTA_INTERNO).build();
        assertThatThrownBy(() -> servicio.solicitarCorreccion(9L, "Falta un dato", ejecutor)).isInstanceOf(ApiException.class).hasMessageContaining("publicado");
        sp.setEstado(EstadoSubproceso.PUBLICADO);
        assertThatThrownBy(() -> servicio.solicitarCorreccion(9L, " ", ejecutor)).isInstanceOf(ApiException.class).hasMessageContaining("corregir");
        servicio.solicitarCorreccion(9L, "Falta un dato", ejecutor);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("SOLICITUD_CORRECCION"),
                any(), any(), eq("Falta un dato"), eq(7L), any());
    }
}
