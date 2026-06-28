package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.dto.HojaVidaRequest;
import com.polygraph.erp.modules.evaluados.dto.LinkValidacionResponse;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.solicitudes.entity.Solicitud;
import com.polygraph.erp.modules.solicitudes.entity.SolicitudServicio;
import com.polygraph.erp.modules.solicitudes.repository.SolicitudRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.shared.enums.CategoriaServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluadoService — Pruebas de validación de link y formulario")
class EvaluadoServiceTest {

    @Mock private LinkCandidatoRepository linkCandidatoRepository;
    @Mock private SolicitudRepository solicitudRepository;
    @Mock private HojaVidaEvaluadoRepository hojaVidaRepository;
    @Mock private HistorialLaboralService historialLaboralService;

    @InjectMocks
    private EvaluadoService evaluadoService;

    private LinkCandidato linkActivo;
    private Solicitud solicitudBase;

    @BeforeEach
    void setUp() {
        linkActivo = LinkCandidato.builder()
                .id(1L)
                .token("token-valido-abc123")
                .idSolicitud(10L)
                .fechaCreacion(LocalDateTime.now().minusHours(1))
                .fechaExpiracion(LocalDateTime.now().plusHours(35))
                .usado(false)
                .build();

        CatalogoServicio catalogo = CatalogoServicio.builder()
                .idCatalogo(1)
                .nombre("Estudio Básico")
                .categoria(CategoriaServicio.ESTUDIOS_SEGURIDAD)
                .activo(true)
                .build();

        SolicitudServicio servItem = SolicitudServicio.builder()
                .catalogoServicio(catalogo)
                .estado(EstadoServicio.PENDIENTE)
                .build();

        solicitudBase = Solicitud.builder()
                .idSolicitud(10L)
                .cedulaEvaluado("10000001")
                .nombresEvaluado("Pedro")
                .apellidosEvaluado("Álvarez")
                .cargo("Analista")
                .emailEvaluado("pedro@test.com")
                .celularEvaluado("3001234567")
                .estado(EstadoServicio.PENDIENTE)
                .servicios(new ArrayList<>(List.of(servItem)))
                .build();
    }

    // ── validarLink ───────────────────────────────────────────────

    @Test
    @DisplayName("validarLink con token activo retorna datos del evaluado y servicio")
    void validar_link_activo_retorna_datos() {
        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitudBase));
        when(hojaVidaRepository.findBySolicitud_IdSolicitud(10L)).thenReturn(Optional.empty());

        LinkValidacionResponse respuesta = evaluadoService.validarLink("token-valido-abc123");

        assertThat(respuesta.cedulaEvaluado()).isEqualTo("10000001");
        assertThat(respuesta.nombresEvaluado()).isEqualTo("Pedro");
        assertThat(respuesta.apellidosEvaluado()).isEqualTo("Álvarez");
        assertThat(respuesta.servicios()).containsExactly("Estudio Básico");
        assertThat(respuesta.hojaVidaCompletada()).isFalse();
    }

    @Test
    @DisplayName("validarLink con link ya utilizado lanza ApiException GONE")
    void validar_link_ya_usado_lanza_excepcion() {
        linkActivo.setUsado(true);
        when(linkCandidatoRepository.findByToken("token-usado")).thenReturn(Optional.of(linkActivo));

        assertThatThrownBy(() -> evaluadoService.validarLink("token-usado"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(apiEx.getMessage()).containsIgnoringCase("utilizado");
                });
    }

    @Test
    @DisplayName("validarLink con link expirado lanza ApiException GONE")
    void validar_link_expirado_lanza_excepcion() {
        linkActivo.setFechaExpiracion(LocalDateTime.now().minusHours(1));
        when(linkCandidatoRepository.findByToken("token-expirado")).thenReturn(Optional.of(linkActivo));

        assertThatThrownBy(() -> evaluadoService.validarLink("token-expirado"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(apiEx.getMessage()).containsIgnoringCase("expirado");
                });
    }

    @Test
    @DisplayName("validarLink con token inexistente lanza ApiException NOT_FOUND")
    void validar_link_no_existe_lanza_excepcion() {
        when(linkCandidatoRepository.findByToken("token-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluadoService.validarLink("token-inexistente"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("validarLink cuando ya hay hoja de vida completada devuelve formularioCompletado=true")
    void validar_link_con_formulario_completado() {
        HojaVidaEvaluado hojaVida = HojaVidaEvaluado.builder()
                .completado(true)
                .autorizacionDatos(true)
                .build();
        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitudBase));
        when(hojaVidaRepository.findBySolicitud_IdSolicitud(10L)).thenReturn(Optional.of(hojaVida));

        LinkValidacionResponse respuesta = evaluadoService.validarLink("token-valido-abc123");

        assertThat(respuesta.hojaVidaCompletada()).isTrue();
    }

    // ── enviarFormulario ──────────────────────────────────────────

    @Test
    @DisplayName("enviarFormulario marca el link como usado después de guardar")
    void enviar_formulario_marca_link_como_usado() {
        HojaVidaRequest request = new HojaVidaRequest(
                true, null, null, null, null, null, null, null,
                "pedro@test.com", "3001234567",
                List.of(), List.of(), List.of());

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitudBase));
        when(hojaVidaRepository.findBySolicitud_IdSolicitud(anyLong())).thenReturn(Optional.empty());
        when(hojaVidaRepository.save(any(HojaVidaEvaluado.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historialLaboralService.calcularInactividades(any(), any())).thenReturn(List.of());

        evaluadoService.enviarFormulario("token-valido-abc123", request);

        ArgumentCaptor<LinkCandidato> captor = ArgumentCaptor.forClass(LinkCandidato.class);
        verify(linkCandidatoRepository).save(captor.capture());
        assertThat(captor.getValue().getUsado()).isTrue();
    }

    @Test
    @DisplayName("enviarFormulario sin autorización de datos lanza ApiException")
    void enviar_formulario_sin_autorizacion_datos_lanza_excepcion() {
        HojaVidaRequest request = new HojaVidaRequest(
                false, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), List.of());

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));

        assertThatThrownBy(() -> evaluadoService.enviarFormulario("token-valido-abc123", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("autorización");
    }

    // ── guardarProgreso ───────────────────────────────────────────

    @Test
    @DisplayName("guardarProgreso crea nueva hoja de vida si no existe")
    void guardar_progreso_crea_hoja_vida_nueva() {
        HojaVidaRequest request = new HojaVidaRequest(
                false, null, "Bogotá", "Soltero", "Universitario",
                "Calle 123", "Chapinero", "3", "pedro@test.com", "3001234567",
                List.of(), List.of(), List.of());

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitudBase));
        when(hojaVidaRepository.findBySolicitud_IdSolicitud(10L)).thenReturn(Optional.empty());
        when(hojaVidaRepository.save(any(HojaVidaEvaluado.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historialLaboralService.calcularInactividades(any(), any())).thenReturn(List.of());

        evaluadoService.guardarProgreso("token-valido-abc123", request);

        verify(hojaVidaRepository, atLeastOnce()).save(any(HojaVidaEvaluado.class));
    }

    @Test
    @DisplayName("guardarProgreso actualiza hoja de vida existente")
    void guardar_progreso_actualiza_hoja_vida_existente() {
        HojaVidaEvaluado hojaExistente = HojaVidaEvaluado.builder()
                .solicitud(solicitudBase)
                .autorizacionDatos(false)
                .completado(false)
                .progresoPorcentaje(20)
                .educacion(new ArrayList<>())
                .experienciaLaboral(new ArrayList<>())
                .inactividades(new ArrayList<>())
                .referencias(new ArrayList<>())
                .build();

        HojaVidaRequest request = new HojaVidaRequest(
                true, null, null, "Casado", null, null, null, null,
                "pedro@test.com", "3001234567",
                List.of(), List.of(), List.of());

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(solicitudBase));
        when(hojaVidaRepository.findBySolicitud_IdSolicitud(10L)).thenReturn(Optional.of(hojaExistente));
        when(hojaVidaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialLaboralService.calcularInactividades(any(), any())).thenReturn(List.of());

        evaluadoService.guardarProgreso("token-valido-abc123", request);

        assertThat(hojaExistente.getAutorizacionDatos()).isTrue();
        assertThat(hojaExistente.getEstadoCivil()).isEqualTo("Casado");
        // No debe marcarse como completado al guardar progreso
        assertThat(hojaExistente.getCompletado()).isFalse();
    }
}
