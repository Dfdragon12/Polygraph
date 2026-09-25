package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.EducacionEvaluado;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioFila;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Prueba directa (sin BD, sin .docx real) de que el documento Word arma cada fila de una tabla
 *  fija con SU PROPIO valor de validación, no uno compartido/mezclado entre filas — el reclamo
 *  puntual reportado: "el documento no me está saliendo relacionado". construirContexto() es lo
 *  único que decide qué termina viendo office-stamper en cada fila, así que si esto pasa acá con
 *  valores distintos correctamente separados, el documento generado también lo estará. */
@DisplayName("FormularioPlantillaWordService — columnas de validación en el documento Word")
class FormularioPlantillaWordServiceValidacionTest {

    private final HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository = Mockito.mock(HojaVidaEvaluadoRepository.class);
    private final CandidatoInfoService candidatoInfoService =
            new CandidatoInfoService(Mockito.mock(CiudadRepository.class), hojaVidaEvaluadoRepository);
    private final FormularioPlantillaWordService service =
            new FormularioPlantillaWordService(candidatoInfoService, null);

    @Test
    @DisplayName("Cada fila de la tabla fija lleva SU propio valor de validación, no el de otra fila")
    void filasConValoresDeValidacionDistintosNoSeMezclan() {
        Candidato candidato = Candidato.builder().idCandidato(1L).cedula("111").nombres("Diego").apellidos("Prueba").build();

        HojaVidaEvaluado hojaVida = HojaVidaEvaluado.builder().id(10L).candidato(candidato)
                .autorizacionDatos(true).completado(true).build();
        EducacionEvaluado educacionA = EducacionEvaluado.builder().id(101L).hojaVidaEvaluado(hojaVida).institucion("Universidad Nacional").build();
        EducacionEvaluado educacionB = EducacionEvaluado.builder().id(102L).hojaVidaEvaluado(hojaVida).institucion("Colegio San José").build();
        hojaVida.setEducacion(List.of(educacionA, educacionB));
        when(hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(1L)).thenReturn(Optional.of(hojaVida));

        FormularioSeccion seccionEducacion = FormularioSeccion.builder().idSeccion(50).nombre("Educación")
                .tipo(TipoSeccionFormulario.TABLA).origenFijo("educacion").clave("educacion").orden(0).build();
        FormularioColumna columnaVerificado = FormularioColumna.builder().idColumna(500).seccion(seccionEducacion)
                .nombre("Verificado").tipoDato(TipoDatoFormulario.BOOLEANO).clave("verificado").orden(0).build();
        seccionEducacion.setColumnas(List.of(columnaVerificado));

        FormularioFila filaValidacionA = FormularioFila.builder().idFila(900).idOrigenFijo(101L).etiqueta("").orden(0).build();
        FormularioFila filaValidacionB = FormularioFila.builder().idFila(901).idOrigenFijo(102L).etiqueta("").orden(1).build();

        Map<Integer, List<FormularioFila>> filasPorSeccion = Map.of(50, List.of(filaValidacionA, filaValidacionB));
        Map<Integer, Map<Integer, String>> valoresPorFila = Map.of(
                900, Map.of(500, "true"),
                901, Map.of(500, "false"));

        ContextoPlantillaWord contexto = service.construirContexto(candidato, null, List.of(seccionEducacion),
                Map.of(), filasPorSeccion, valoresPorFila);

        List<Map<String, Object>> filasEducacion = contexto.getTablas().get("educacion");
        assertThat(filasEducacion).hasSize(2);

        Map<String, Object> filaUniversidad = filasEducacion.stream()
                .filter(f -> "Universidad Nacional".equals(f.get("institucion"))).findFirst().orElseThrow();
        Map<String, Object> filaColegio = filasEducacion.stream()
                .filter(f -> "Colegio San José".equals(f.get("institucion"))).findFirst().orElseThrow();

        // BOOLEANO se formatea como "X"/"" (ver FormularioValorFormateador.textoCelda) — lo que
        // importa acá es que sean DISTINTOS entre las dos filas, no el texto exacto.
        assertThat(filaUniversidad.get("verificado")).isEqualTo("X");
        assertThat(filaColegio.get("verificado")).isEqualTo("");
    }

    @Test
    @DisplayName("Una fila de validación sin match (su fila original ya no existe) no rompe el documento")
    void filaDeValidacionHuerfanaNoRompe() {
        Candidato candidato = Candidato.builder().idCandidato(2L).cedula("222").nombres("Otro").apellidos("Candidato").build();
        HojaVidaEvaluado hojaVida = HojaVidaEvaluado.builder().id(20L).candidato(candidato)
                .autorizacionDatos(true).completado(true).build();
        hojaVida.setEducacion(List.of());
        when(hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(2L)).thenReturn(Optional.of(hojaVida));

        FormularioSeccion seccionEducacion = FormularioSeccion.builder().idSeccion(51).nombre("Educación")
                .tipo(TipoSeccionFormulario.TABLA).origenFijo("educacion").clave("educacion").orden(0).build();
        FormularioColumna columnaVerificado = FormularioColumna.builder().idColumna(501).seccion(seccionEducacion)
                .nombre("Verificado").tipoDato(TipoDatoFormulario.BOOLEANO).clave("verificado").orden(0).build();
        seccionEducacion.setColumnas(List.of(columnaVerificado));

        FormularioFila filaHuerfana = FormularioFila.builder().idFila(910).idOrigenFijo(999L).etiqueta("").orden(0).build();
        Map<Integer, List<FormularioFila>> filasPorSeccion = Map.of(51, List.of(filaHuerfana));
        Map<Integer, Map<Integer, String>> valoresPorFila = Map.of(910, Map.of(501, "true"));

        ContextoPlantillaWord contexto = service.construirContexto(candidato, null, List.of(seccionEducacion),
                Map.of(), filasPorSeccion, valoresPorFila);

        assertThat(contexto.getTablas().get("educacion")).isEmpty();
    }
}
