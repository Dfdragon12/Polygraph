package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.evaluados.dto.EducacionRequest;
import com.polygraph.erp.modules.evaluados.dto.ExperienciaLaboralRequest;
import com.polygraph.erp.modules.evaluados.dto.HojaVidaRequest;
import com.polygraph.erp.modules.evaluados.dto.ReferenciaPersonalRequest;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Revisión del módulo del candidato (V213): lo que se exige al ENVIAR la hoja de vida. */
@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluadoService — validaciones de la revisión del candidato")
class EvaluadoRevisionCandidatoTest {

    @Mock private LinkCandidatoRepository linkCandidatoRepository;
    @Mock private HojaVidaEvaluadoRepository hojaVidaRepository;
    @Mock private CandidatoRepository candidatoRepository;
    @Mock private HistorialLaboralService historialLaboralService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionRepository notificacionRepository;
    @Mock private com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository documentoRequeridoProcesoRepository;
    @Mock private ValidadorArchivoCandidato validadorArchivo;
    @Mock private com.polygraph.erp.modules.catalogo.repository.ItemHojaVidaRepository itemHojaVidaRepository;

    @InjectMocks
    private EvaluadoService evaluadoService;

    @BeforeEach
    void setUp() {
        LinkCandidato link = LinkCandidato.builder().id(1L).token("tk").servicio(Servicio.builder().idServicio(10).estado(com.polygraph.erp.shared.enums.EstadoServicio.PENDIENTE).build())
                .fechaCreacion(LocalDateTime.now()).fechaExpiracion(LocalDateTime.now().plusHours(1)).usado(false).build();
        when(linkCandidatoRepository.findByToken("tk")).thenReturn(Optional.of(link));
    }

    private static EducacionRequest estudio(String estado, Boolean exterior, String pais) {
        return new EducacionRequest("Profesional", "U", null, LocalDate.of(2015, 1, 1), null, null, "Bogotá", estado, exterior, pais);
    }

    private static ExperienciaLaboralRequest empleo(Boolean temporal, String nombre) {
        return new ExperienciaLaboralRequest("Acme", "Auxiliar", LocalDate.of(2022, 1, 1), null, true, "Bogotá",
                null, null, null, null, "Término fijo", 1_500_000L, "Ninguno", temporal, nombre);
    }

    private static ReferenciaPersonalRequest ref(String tel, String tiempo, boolean c1, boolean c2) {
        return new ReferenciaPersonalRequest("Ana", "Amigo(a)", tel, tiempo, c1, c2);
    }

    private HojaVidaRequest solicitud(List<EducacionRequest> edu, List<ExperienciaLaboralRequest> exp,
                                      List<ReferenciaPersonalRequest> refs) {
        return new HojaVidaRequest(true, null, null, null, null, null, null, null, null, null,
                "a@b.com", "3001234567", null, null, null, null, null, null, null,
                edu, exp, refs, Map.of(), null, null, null, null,
                null, null, null, null, null, null, false, List.of());
    }

    @Test
    @DisplayName("un estudio sin estado no se puede enviar")
    void estudio_sin_estado() {
        var r = solicitud(List.of(estudio(null, false, null)), List.of(), List.of());
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("estado del estudio");
    }

    @Test
    @DisplayName("un estudio en el exterior exige el país")
    void estudio_exterior_sin_pais() {
        var r = solicitud(List.of(estudio("Graduado", true, " ")), List.of(), List.of());
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("país");
    }

    @Test
    @DisplayName("contratado por temporal exige el nombre de la temporal")
    void temporal_sin_nombre() {
        var r = solicitud(List.of(), List.of(empleo(true, "")), List.of());
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("temporales");
    }

    @Test
    @DisplayName("la referencia con el mismo celular del candidato se rechaza")
    void referencia_celular_del_candidato() {
        var r = solicitud(List.of(), List.of(), List.of(ref("300 123 4567", "2 años", true, true)));
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("no puede ser el suyo");
    }

    @Test
    @DisplayName("dos referencias con el mismo celular se rechazan")
    void referencias_repetidas() {
        var r = solicitud(List.of(), List.of(),
                List.of(ref("3101112233", "2 años", true, true), ref("3101112233", "3 años", true, true)));
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("repetido");
    }

    @Test
    @DisplayName("una referencia con menos de 1 año no cumple")
    void referencia_menos_de_un_anio() {
        var r = solicitud(List.of(), List.of(), List.of(ref("3101112233", "Menos de 1 año", true, true)));
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("mínimo un (1) año");
    }

    @Test
    @DisplayName("las dos confirmaciones de la referencia son obligatorias")
    void referencia_sin_confirmaciones() {
        var r = solicitud(List.of(), List.of(), List.of(ref("3101112233", "2 años", true, false)));
        assertThatThrownBy(() -> evaluadoService.enviarFormulario("tk", r))
                .isInstanceOf(ApiException.class).hasMessageContaining("confirmaciones");
    }
}
