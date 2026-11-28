package com.polygraph.erp.modules.solicitudes.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PB-03: la portada del PDF único y la unión de PDF. */
class EstudioPdfProcesadorTest {

    private EmisionEstudioService.Portada portada(String tipo, String nivel, List<String> faltantes) {
        return new EmisionEstudioService.Portada(52, 1, tipo, nivel, "Jose Morales", "1000222333", "Gerente Comercial", "Carlos Palacios", "Avanzado",
                "El evaluado confirmó su información. Sin hallazgos relevantes.", List.of("Reporte de mora vigente"), faltantes,
                List.of(new EmisionEstudioService.Informe(1L, "Poligrafía Pre Empleo", null, true), new EmisionEstudioService.Informe(2L, "Antecedentes", null, true)), 5L);
    }

    private String texto(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) { return new PDFTextStripper().getText(d); }
    }

    @Test
    @DisplayName("La portada final lleva el nivel, la nota del gestor, las alertas y el contenido; nunca pesos, «ponderado», «Favorable» ni marcadores sin resolver")
    void portadaFinal() throws Exception {
        String t = texto(EstudioPdfProcesador.portadaPdf(portada("FINAL", "BAJO", List.of())));
        assertThat(t).contains("Informe de análisis final", "Jose Morales", "Nivel de riesgo del estudio: BAJO", "Nota del gestor",
                "El evaluado confirmó su información", "Reporte de mora vigente", "1. Poligrafía Pre Empleo", "2. Antecedentes");
        assertThat(t).doesNotContain("${", "%", "ponderado", "Favorable", "Desfavorable", "PRELIMINAR");
    }

    @Test
    @DisplayName("La portada preliminar dice «PRELIMINAR – CIERRE PENDIENTE» con lo que falta, y no muestra el nivel del estudio")
    void portadaPreliminar() throws Exception {
        String t = texto(EstudioPdfProcesador.portadaPdf(portada("PRELIMINAR", null, List.of("Visita domiciliaria", "Validación laboral"))));
        assertThat(t).contains("PRELIMINAR – CIERRE PENDIENTE: Visita domiciliaria, Validación laboral");
        assertThat(t).doesNotContain("Nivel de riesgo del estudio");
    }

    @Test
    @DisplayName("Una nota muy larga sigue en otra página en vez de cortarse")
    void notaLargaPaginaNueva() throws Exception {
        var p = new EmisionEstudioService.Portada(52, 1, "FINAL", "BAJO", "A", "1", "C", "E", "Avanzado", "palabra ".repeat(1500), List.of(), List.of(), List.of(), 5L);
        try (PDDocument d = Loader.loadPDF(EstudioPdfProcesador.portadaPdf(p))) { assertThat(d.getNumberOfPages()).isGreaterThan(1); }
    }

    @Test
    @DisplayName("Unir: la portada va primero y después los informes, en su orden")
    void unirEnOrden() throws Exception {
        byte[] a = EstudioPdfProcesador.portadaPdf(portada("FINAL", "BAJO", List.of()));
        byte[] b = EstudioPdfProcesador.portadaPdf(new EmisionEstudioService.Portada(53, 1, "FINAL", "MEDIO", "Segundo", "2", "C", "E", "Antecedentes", "N", List.of(), List.of(), List.of(), 5L));
        byte[] unido = EstudioPdfProcesador.unir(List.of(a, b));
        try (PDDocument d = Loader.loadPDF(unido)) { assertThat(d.getNumberOfPages()).isEqualTo(2); }
        String t = texto(unido);
        assertThat(t.indexOf("Jose Morales")).isLessThan(t.indexOf("Segundo"));
    }
}
