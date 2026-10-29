package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.dto.HojaVidaRequest;
import com.polygraph.erp.modules.evaluados.dto.LinkValidacionResponse;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
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
import java.util.Map;
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
    @Mock private HojaVidaEvaluadoRepository hojaVidaRepository;
    @Mock private CandidatoRepository candidatoRepository;
    @Mock private HistorialLaboralService historialLaboralService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionRepository notificacionRepository;
    @Mock private com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository documentoRequeridoProcesoRepository;
    @Mock private ValidadorArchivoCandidato validadorArchivo;
    @Mock private com.polygraph.erp.modules.catalogo.repository.ItemHojaVidaRepository itemHojaVidaRepository;
    @Mock private com.polygraph.erp.modules.auth.service.AceptacionLegalService aceptacionLegalService;

    @Mock private com.polygraph.erp.modules.servicios.service.SemaforoTiempoService semaforoTiempoService;

    @InjectMocks
    private EvaluadoService evaluadoService;

    private LinkCandidato linkActivo;
    private Servicio servicioBase;
    private Candidato candidatoBase;

    @BeforeEach
    void setUp() {
        candidatoBase = Candidato.builder()
                .idCandidato(20L)
                .cedula("10000001")
                .nombres("Pedro")
                .apellidos("Álvarez")
                .emailPrincipal("pedro@test.com")
                .celular("3001234567")
                .build();

        Proceso proceso = Proceso.builder()
                .idProceso(1)
                .nombreProceso("Estudio Básico")
                .activo(true)
                .build();

        servicioBase = Servicio.builder()
                .idServicio(10)
                .candidato(candidatoBase)
                .proceso(proceso)
                .cargo("Analista")
                .estado(EstadoServicio.PENDIENTE)
                .build();

        linkActivo = LinkCandidato.builder()
                .id(1L)
                .token("token-valido-abc123")
                .servicio(servicioBase)
                .fechaCreacion(LocalDateTime.now().minusHours(1))
                .fechaExpiracion(LocalDateTime.now().plusHours(35))
                .usado(false)
                .build();
    }

    // ── validarLink ───────────────────────────────────────────────

    @Test
    @DisplayName("validarLink con token activo retorna datos del evaluado y proceso")
    void validar_link_activo_retorna_datos() {
        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.empty());

        LinkValidacionResponse respuesta = evaluadoService.validarLink("token-valido-abc123");

        assertThat(respuesta.cedulaEvaluado()).isEqualTo("10000001");
        assertThat(respuesta.nombresEvaluado()).isEqualTo("Pedro");
        assertThat(respuesta.apellidosEvaluado()).isEqualTo("Álvarez");
        assertThat(respuesta.proceso()).isEqualTo("Estudio Básico");
        assertThat(respuesta.hojaVidaCompletada()).isFalse();
    }

    @Test
    @DisplayName("V197: un link revocado por el gestor ya no abre (GONE)")
    void validar_link_revocado_lanza_excepcion() {
        linkActivo.setRevocado(true);
        when(linkCandidatoRepository.findByToken("token-revocado")).thenReturn(Optional.of(linkActivo));

        assertThatThrownBy(() -> evaluadoService.servicioConAcceso("token-revocado"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(apiEx.getMessage()).containsIgnoringCase("desactivado");
                });
    }

    @Test
    @DisplayName("V197: con el servicio cerrado (publicado/finalizado/cancelado) el link ya no abre")
    void validar_link_servicio_cerrado_lanza_excepcion() {
        servicioBase.setEstado(EstadoServicio.PUBLICADO);
        when(linkCandidatoRepository.findByToken("token-cerrado")).thenReturn(Optional.of(linkActivo));

        assertThatThrownBy(() -> evaluadoService.servicioConAcceso("token-cerrado"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(apiEx.getMessage()).containsIgnoringCase("finalizó");
                });
    }

    @Test
    @DisplayName("V197: enviado (usado) y pasadas las 36 h, el link SIGUE abriendo mientras el servicio esté activo")
    void validar_link_usado_y_vencido_sigue_abriendo() {
        linkActivo.setUsado(true);
        linkActivo.setFechaExpiracion(LocalDateTime.now().minusDays(5));
        when(linkCandidatoRepository.findByToken("token-enviado")).thenReturn(Optional.of(linkActivo));

        assertThat(evaluadoService.servicioConAcceso("token-enviado")).isSameAs(servicioBase);
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
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.of(hojaVida));

        LinkValidacionResponse respuesta = evaluadoService.validarLink("token-valido-abc123");

        assertThat(respuesta.hojaVidaCompletada()).isTrue();
        assertThat(respuesta.autorizacionDatos()).isTrue();
    }

    // ── Autorización de tratamiento de datos (primera pantalla del portal) ──

    @Test
    @DisplayName("Primer ingreso: validarLink avisa que falta la autorización de datos")
    void validar_link_sin_autorizacion() {
        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.empty());

        assertThat(evaluadoService.validarLink("token-valido-abc123").autorizacionDatos()).isFalse();
    }

    @Test
    @DisplayName("Aceptar la autorización la registra con fecha, aunque aún no exista hoja de vida")
    void autorizar_registra_fecha() {
        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.empty());
        when(hojaVidaRepository.save(any(HojaVidaEvaluado.class))).thenAnswer(inv -> inv.getArgument(0));

        evaluadoService.autorizarTratamientoDatos("token-valido-abc123", "127.0.0.1");

        ArgumentCaptor<HojaVidaEvaluado> captor = ArgumentCaptor.forClass(HojaVidaEvaluado.class);
        verify(hojaVidaRepository, atLeastOnce()).save(captor.capture());
        HojaVidaEvaluado guardada = captor.getValue();
        assertThat(guardada.getAutorizacionDatos()).isTrue();
        assertThat(guardada.getFechaAutorizacion()).isNotNull();
    }

    @Test
    @DisplayName("Sin autorización no se pueden cargar documentos (403)")
    void sin_autorizacion_no_carga_documentos() {
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.of(
                HojaVidaEvaluado.builder().candidato(candidatoBase).autorizacionDatos(false).build()));

        assertThatThrownBy(() -> evaluadoService.exigirAutorizacion(servicioBase))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── enviarFormulario ──────────────────────────────────────────

    @Test
    @DisplayName("enviarFormulario marca el link como usado después de guardar")
    void enviar_formulario_marca_link_como_usado() {
        HojaVidaRequest request = new HojaVidaRequest(
                true, null, null, null, null, null, null, null, null, null,
                "pedro@test.com", "3001234567",
                null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), Map.of(), null, null, null, null,
                null, null, null, null, null, null, null, null);

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(hojaVidaRepository.findByCandidato_IdCandidato(anyLong())).thenReturn(Optional.empty());
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
                false, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), Map.of(), null, null, null, null,
                null, null, null, null, null, null, null, null);

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
                false, null, "Bogotá", null, null, "Soltero", "Universitario",
                "Calle 123", "Chapinero", "3", "pedro@test.com", "3001234567",
                null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), Map.of(), null, null, null, null,
                null, null, null, null, null, null, null, null);

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.empty());
        when(hojaVidaRepository.save(any(HojaVidaEvaluado.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historialLaboralService.calcularInactividades(any(), any())).thenReturn(List.of());

        evaluadoService.guardarProgreso("token-valido-abc123", request);

        verify(hojaVidaRepository, atLeastOnce()).save(any(HojaVidaEvaluado.class));
    }

    @Test
    @DisplayName("guardarProgreso actualiza hoja de vida existente")
    void guardar_progreso_actualiza_hoja_vida_existente() {
        HojaVidaEvaluado hojaExistente = HojaVidaEvaluado.builder()
                .candidato(candidatoBase)
                .autorizacionDatos(false)
                .completado(false)
                .progresoPorcentaje(20)
                .educacion(new ArrayList<>())
                .experienciaLaboral(new ArrayList<>())
                .inactividades(new ArrayList<>())
                .referencias(new ArrayList<>())
                .build();

        HojaVidaRequest request = new HojaVidaRequest(
                true, null, null, null, null, "Casado", null, null, null, null,
                "pedro@test.com", "3001234567",
                null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), Map.of(), null, null, null, null,
                null, null, null, null, null, null, null, null);

        when(linkCandidatoRepository.findByToken("token-valido-abc123")).thenReturn(Optional.of(linkActivo));
        when(hojaVidaRepository.findByCandidato_IdCandidato(20L)).thenReturn(Optional.of(hojaExistente));
        when(hojaVidaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialLaboralService.calcularInactividades(any(), any())).thenReturn(List.of());

        evaluadoService.guardarProgreso("token-valido-abc123", request);

        assertThat(hojaExistente.getAutorizacionDatos()).isTrue();
        assertThat(hojaExistente.getEstadoCivil()).isEqualTo("Casado");
        // No debe marcarse como completado al guardar progreso
        assertThat(hojaExistente.getCompletado()).isFalse();
    }
}
