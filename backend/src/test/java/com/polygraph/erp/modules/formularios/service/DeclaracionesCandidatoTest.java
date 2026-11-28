package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.entity.RespuestaHojaVida;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Las declaraciones del candidato (preguntas de su hoja de vida) salen en la ficha del formulario
 *  y como tabla de Word tablas['declaraciones'] con pregunta, respuesta y detalle. */
class DeclaracionesCandidatoTest {

    private final HojaVidaEvaluadoRepository hojaVidaRepository = mock(HojaVidaEvaluadoRepository.class);
    private final CandidatoInfoService service = new CandidatoInfoService(null, hojaVidaRepository);

    @Test
    @DisplayName("tablas['declaraciones'] trae una fila por pregunta respondida, con Sí/No y la explicación")
    void declaracionesComoTablaDeWord() {
        Candidato candidato = Candidato.builder().idCandidato(7L).build();
        HojaVidaEvaluado hv = HojaVidaEvaluado.builder().candidato(candidato).build();
        hv.getRespuestas().add(RespuestaHojaVida.builder().id(1L).pregunta("¿Tiene procesos judiciales?")
                .respuesta(true).detalle("Demandante en un proceso civil").build());
        hv.getRespuestas().add(RespuestaHojaVida.builder().id(2L).pregunta("¿Tiene sanciones?")
                .respuesta(false).build());
        hv.getRespuestas().add(RespuestaHojaVida.builder().id(3L).pregunta("Sin responder").build());
        when(hojaVidaRepository.findByCandidato_IdCandidato(7L)).thenReturn(Optional.of(hv));

        List<Map<String, String>> filas = service.resolverTablasFijas(candidato).get("declaraciones");

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("pregunta", "¿Tiene procesos judiciales?")
                .containsEntry("respuesta", "Sí").containsEntry("detalle", "Demandante en un proceso civil");
        assertThat(filas.get(1)).containsEntry("respuesta", "No").containsEntry("detalle", "");
        assertThat(filas.get(0).keySet()).containsAll(CandidatoInfoService.COLUMNAS_TABLAS_FIJAS.get("declaraciones"));
    }

    @Test
    @DisplayName("Sin hoja de vida la tabla existe y va vacía (el Word no se rompe)")
    void sinHojaDeVidaVaVacia() {
        Candidato candidato = Candidato.builder().idCandidato(8L).build();
        when(hojaVidaRepository.findByCandidato_IdCandidato(8L)).thenReturn(Optional.empty());

        assertThat(service.resolverTablasFijas(candidato).get("declaraciones")).isEmpty();
        assertThat(service.declaraciones(candidato)).isEmpty();
    }
}
