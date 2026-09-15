package com.polygraph.erp.modules.formularios.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;

import java.util.List;
import java.util.stream.Collectors;

/** Cómo se ve el valor guardado de una columna como texto — usado por FormularioDocumentoService
 *  al armar el contexto que le pasa a FormularioPlantillaWordService (el generador de documento
 *  vía marcadores). Utilidad estática a propósito: si fuera un método de instancia de
 *  FormularioPlantillaWordService, FormularioDocumentoService tendría que inyectarlo solo para
 *  esto, una dependencia extra innecesaria para un formateo tan simple. */
final class FormularioValorFormateador {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FormularioValorFormateador() {
    }

    /** El .docx no incrusta el/los archivo(s) — solo referencia el/los nombre(s), igual que un
     *  anexo físico. El archivo real se ve/descarga desde el formulario en la plataforma. */
    static String textoCelda(TipoDatoFormulario tipoDato, String valor) {
        if (tipoDato == TipoDatoFormulario.BOOLEANO) {
            return "true".equalsIgnoreCase(valor) ? "X" : "";
        }
        if (tipoDato == TipoDatoFormulario.ARCHIVO) {
            List<String> rutas = parseRutasArchivo(valor);
            if (rutas.isEmpty()) return "";
            return "Anexo: " + rutas.stream().map(FormularioValorFormateador::nombreDeRuta).collect(Collectors.joining(", "));
        }
        return valor != null ? valor : "";
    }

    /** El valor de una columna ARCHIVO es un array JSON de rutas (["a.jpg","b.jpg"]) para
     *  soportar varios adjuntos por campo. Si viene una ruta suelta (formato de antes de que
     *  existieran los adjuntos múltiples), se trata como una lista de un solo elemento.
     *  Paquete-visible: FormularioPlantillaWordService la reutiliza para decidir si puede
     *  incrustar la imagen en vez de solo referenciarla por nombre. */
    static List<String> parseRutasArchivo(String valor) {
        if (valor == null || valor.isBlank()) return List.of();
        try {
            return OBJECT_MAPPER.readValue(valor, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(valor);
        }
    }

    private static String nombreDeRuta(String ruta) {
        String segmento = ruta.substring(ruta.lastIndexOf('/') + 1);
        return segmento.contains("_") ? segmento.substring(segmento.indexOf('_') + 1) : segmento;
    }
}
