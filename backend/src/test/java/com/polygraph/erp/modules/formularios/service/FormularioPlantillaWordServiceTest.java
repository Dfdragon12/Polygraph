package com.polygraph.erp.modules.formularios.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** extraerTextosDeCuadrosDeTexto no depende de nada externo — se prueba directo con fragmentos de
 *  XML, sin necesidad de armar un FormularioPlantillaConfig + archivo en disco. Corrige el bug
 *  real reportado por un usuario: "Analizar" marcaba como "sin usar" un campo que en realidad SÍ
 *  se reemplazaba bien al generar el documento — porque la API de alto nivel de POI
 *  (getParagraphs()/getTables()) nunca entra a los cuadros de texto, a diferencia del motor real
 *  de generación (ver MarcadoresEnCuadroDeTextoTest). */
@DisplayName("FormularioPlantillaWordService — detección de marcadores en cuadros de texto")
class FormularioPlantillaWordServiceTest {

    private final FormularioPlantillaWordService service = new FormularioPlantillaWordService(null, null, null);

    @Test
    @DisplayName("Detecta el texto de un cuadro de texto VML (w:pict > v:textbox)")
    void detectaTextoEnCuadroVml() {
        String xml = "<w:body><w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent>"
                + "<w:p><w:r><w:t>${campos['Resultado']}</w:t></w:r></w:p>"
                + "</w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p></w:body>";

        List<String> textos = service.extraerTextosDeCuadrosDeTexto(xml);

        assertThat(textos).containsExactly("${campos['Resultado']}");
    }

    @Test
    @DisplayName("Detecta el texto de un cuadro de texto DrawingML (wps:txbx) igual que el VML")
    void detectaTextoEnCuadroDrawingMl() {
        String xml = "<w:body><w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData>"
                + "<wps:wsp><wps:txbx><w:txbxContent>"
                + "<w:p><w:r><w:t>${campos['otro']}</w:t></w:r></w:p>"
                + "</w:txbxContent></wps:txbx></wps:wsp>"
                + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p></w:body>";

        List<String> textos = service.extraerTextosDeCuadrosDeTexto(xml);

        assertThat(textos).containsExactly("${campos['otro']}");
    }

    @Test
    @DisplayName("Reconstruye un marcador partido en varios <w:t> por el corrector ortográfico de Word")
    void reconstruyeMarcadorPartidoPorProofErr() {
        String xml = "<w:body><w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent>"
                + "<w:p><w:r><w:t>${campos['ciudad_na</w:t></w:r>"
                + "<w:proofErr w:type=\"spellStart\"/>"
                + "<w:r><w:t>cimiento</w:t></w:r>"
                + "<w:proofErr w:type=\"spellEnd\"/>"
                + "<w:r><w:t>']}</w:t></w:r></w:p>"
                + "</w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p></w:body>";

        List<String> textos = service.extraerTextosDeCuadrosDeTexto(xml);

        assertThat(textos).containsExactly("${campos['ciudad_nacimiento']}");
    }

    @Test
    @DisplayName("Sin cuadros de texto, no encuentra nada")
    void sinCuadrosDeTextoNoEncuentraNada() {
        String xml = "<w:body><w:p><w:r><w:t>${campos['normal']}</w:t></w:r></w:p></w:body>";

        assertThat(service.extraerTextosDeCuadrosDeTexto(xml)).isEmpty();
    }
}
