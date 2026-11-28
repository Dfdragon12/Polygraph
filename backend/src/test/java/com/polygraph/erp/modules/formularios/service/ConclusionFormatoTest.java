package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.entity.Formulario;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioDato;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PC-02: qué campo es la conclusión en cada formato, de dónde se lee y que nunca diga «Favorable/Desfavorable». */
@DisplayName("PC-02 — conclusión del ejecutor por formato")
class ConclusionFormatoTest {

    private static FormularioSeccion seccion(String clave, boolean activa, FormularioColumna... columnas) {
        FormularioSeccion s = FormularioSeccion.builder().clave(clave).activo(activa).columnas(new ArrayList<>(List.of(columnas))).build();
        for (FormularioColumna c : columnas) c.setSeccion(s);
        return s;
    }

    private static FormularioColumna columna(int id, String clave, boolean activa) {
        return FormularioColumna.builder().idColumna(id).clave(clave).activo(activa).build();
    }

    @Test
    @DisplayName("Los campos de conclusión de cada formato: «Concepto del analista», «Conclusión» de poligrafía y «Conclusión» de visita; ningún otro")
    void camposPorFormato() {
        assertThat(ConclusionFormato.esCampo("concepto_analista", "concepto")).isTrue();      // antecedentes, académica, laboral, personal, financiero
        assertThat(ConclusionFormato.esCampo("firma", "pol_conclusion")).isTrue();            // poligrafía pre empleo
        assertThat(ConclusionFormato.esCampo("concepto_final", "cf_conclusion")).isTrue();    // visita (V277)
        assertThat(ConclusionFormato.esCampo("concepto_analista", "analista_fecha")).isFalse();
        assertThat(ConclusionFormato.esCampo("hallazgos", "concepto")).isFalse();
        assertThat(ConclusionFormato.esCampo("concepto_final", "cf_dinamica")).isFalse();     // una conclusión por aspecto no es LA conclusión
        assertThat(ConclusionFormato.esCampo(null, "concepto")).isFalse();
        assertThat(ConclusionFormato.esCampo("concepto_analista", null)).isFalse();
    }

    @Test
    @DisplayName("«Favorable» y «desfavorable» se detectan sin importar mayúsculas; otras palabras y «favorablemente» no")
    void palabrasProhibidas() {
        for (String t : List.of("FAVORABLE", "Se emite concepto favorable para el cargo.", "Desfavorable", "resultado DESFAVORABLE.", "no favorable", "Conceptos favorables")) {
            assertThat(ConclusionFormato.contieneFavorable(t)).as(t).isTrue();
        }
        for (String t : List.of("No registra antecedentes ni coincidencias.", "El evaluado respondió favorablemente a la entrevista.", "Se recomienda continuar.", "", "  ")) {
            assertThat(ConclusionFormato.contieneFavorable(t)).as(t).isFalse();
        }
        assertThat(ConclusionFormato.contieneFavorable(null)).isFalse();
        assertThat(ConclusionFormato.AYUDA).isEqualTo("No escribas Favorable o Desfavorable: el resultado lo da el nivel Bajo, Medio o Alto.");
    }

    @Test
    @DisplayName("La columna de conclusión de una plantilla se halla entre las secciones y columnas activas; sin ella, vale el cuadro genérico")
    void columnaDeLaPlantilla() {
        FormularioColumna concepto = columna(7, "concepto", true);
        var con = List.of(seccion("datos_evaluado", true, columna(1, "ev_nombre", true)), seccion("concepto_analista", true, columna(6, "analista_fecha", true), concepto));
        assertThat(ConclusionFormato.columnaDe(con)).containsSame(concepto);

        var archivada = List.of(seccion("concepto_analista", true, columna(7, "concepto", false)));
        assertThat(ConclusionFormato.columnaDe(archivada)).isEmpty();
        var seccionInactiva = List.of(seccion("concepto_analista", false, columna(7, "concepto", true)));
        assertThat(ConclusionFormato.columnaDe(seccionInactiva)).isEmpty();
        var sin = List.of(seccion("datos_evaluado", true, columna(1, "ev_nombre", true)));
        assertThat(ConclusionFormato.columnaDe(sin)).isEmpty();
    }

    @Nested
    @DisplayName("ConclusionFormatoService: la conclusión efectiva")
    class Servicio {
        private FormularioSeccionRepository secciones;
        private FormularioRepository formularios;
        private FormularioDatoRepository datos;
        private ConclusionFormatoService servicio;
        private ServicioSubproceso sp;
        private final FormularioColumna concepto = columna(7, "concepto", true);

        @BeforeEach
        void setUp() {
            secciones = mock(FormularioSeccionRepository.class);
            formularios = mock(FormularioRepository.class);
            datos = mock(FormularioDatoRepository.class);
            servicio = new ConclusionFormatoService(secciones, formularios, datos);
            sp = ServicioSubproceso.builder().id(9L).tipoProgreso(TipoProgreso.builder().idTipoProgreso(3).build()).build();
            when(secciones.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(3)).thenReturn(List.of(seccion("concepto_analista", true, concepto)));
            when(formularios.findByServicioSubproceso_Id(9L)).thenReturn(Optional.of(Formulario.builder().idFormulario(40).build()));
        }

        @Test
        @DisplayName("Lee lo que el analista escribió en «Concepto del analista»; la ventana Publicar ya no llega vacía")
        void leeElCampoDelFormato() {
            when(datos.findByFormulario_IdFormulario(40)).thenReturn(List.of(
                    FormularioDato.builder().columna(columna(99, "ev_nombre", true)).valor("Laura").build(),
                    FormularioDato.builder().columna(concepto).valor("  Con base en las consultas realizadas no registra antecedentes.  ").build()));
            assertThat(servicio.delFormato(sp)).isEqualTo("Con base en las consultas realizadas no registra antecedentes.");
            assertThat(servicio.efectiva(sp)).isEqualTo("Con base en las consultas realizadas no registra antecedentes.");
            assertThat(servicio.origen(sp)).isEqualTo("FORMATO");
        }

        @Test
        @DisplayName("Si el campo del formato está vacío usa el cuadro genérico; si tampoco hay, no hay conclusión")
        void sinCampoUsaElGenerico() {
            when(datos.findByFormulario_IdFormulario(40)).thenReturn(List.of(FormularioDato.builder().columna(concepto).valor("   ").build()));
            sp.setConclusion("Se confirmaron 2 de 2 experiencias.");
            assertThat(servicio.delFormato(sp)).isNull();
            assertThat(servicio.efectiva(sp)).isEqualTo("Se confirmaron 2 de 2 experiencias.");
            assertThat(servicio.origen(sp)).isEqualTo("SUBPROCESO");

            sp.setConclusion(" ");
            assertThat(servicio.efectiva(sp)).isNull();
            assertThat(servicio.origen(sp)).isNull();
        }

        @Test
        @DisplayName("Un formato sin campo de conclusión (plantilla propia) cae al cuadro genérico del último paso")
        void formatoSinCampo() {
            when(secciones.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(3)).thenReturn(List.of(seccion("datos_evaluado", true, columna(1, "ev_nombre", true))));
            sp.setConclusion("Texto del cuadro genérico.");
            assertThat(servicio.columnaDe(sp)).isEmpty();
            assertThat(servicio.efectiva(sp)).isEqualTo("Texto del cuadro genérico.");
        }
    }
}
