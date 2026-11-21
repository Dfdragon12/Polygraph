package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.formularios.service.DocumentoPdfConverter;
import com.polygraph.erp.modules.formularios.service.FormularioDocumentoService;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ProcesadorTrabajo;
import com.polygraph.erp.modules.trabajos.service.ResultadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PB-03: arma el PDF único del estudio completo: primero el «Informe de análisis final» (portada con la nota del gestor, las alertas y el contenido)
 * y después los informes de los subprocesos publicados, en el orden del catálogo. Se guarda con su versión; la descarga entrega ese archivo.
 *
 * La portada la dibuja el sistema con PDFBox: la plantilla Word del informe final (1_Formato_Informe_Analisis_Final.docx) todavía no está en el repo.
 * Nunca imprime pesos, «ponderado» ni «Favorable/Desfavorable»: el registro interno solo se ve en la pantalla del gestor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstudioPdfProcesador implements ProcesadorTrabajo {

    private static final String TIPO_PDF = "application/pdf";

    private final EmisionEstudioService emision;
    private final FormularioDocumentoService documentoService;
    private final DocumentoPdfConverter pdfConverter;
    private final AlmacenTrabajos almacen;

    @Override
    public TipoTrabajo tipo() {
        return TipoTrabajo.ESTUDIO_PDF;
    }

    @Override
    public ResultadoTrabajo procesar(TrabajoAsincrono trabajo, Map<String, Object> parametros, ContextoTrabajo contexto) throws Exception {
        Long idDocumento = ((Number) parametros.get("idDocumento")).longValue();
        try {
            EmisionEstudioService.Portada portada = emision.portada(idDocumento);
            Usuario usuario = trabajo.getUsuario();
            List<byte[]> partes = new ArrayList<>();
            partes.add(portadaPdf(portada));
            contexto.progreso(10);
            int hechos = 0;
            for (var informe : portada.informes()) {
                if (informe.conDocumento()) partes.add(informePdf(informe, usuario));
                hechos++;
                contexto.progreso(10 + 80 * hechos / Math.max(1, portada.informes().size()));
            }
            byte[] unido = unir(partes);
            int paginas;
            try (PDDocument d = Loader.loadPDF(unido)) { paginas = d.getNumberOfPages(); }
            String ruta = "estudios/" + portada.idServicio() + "-v" + portada.version() + ".pdf";
            almacen.escribirAtomico(ruta, unido);
            emision.marcarListo(idDocumento, ruta, paginas);
            log.info("PDF del estudio listo: servicio={}, v{}, {} páginas", portada.idServicio(), portada.version(), paginas);
            return ResultadoTrabajo.archivo(ruta, "Estudio_" + portada.idServicio() + "_v" + portada.version() + ".pdf", TIPO_PDF);
        } catch (Exception e) {
            emision.marcarError(idDocumento, "No se pudo armar el PDF: " + e.getMessage());
            throw e;
        }
    }

    /** El informe de un subproceso: el mismo PDF que descarga el cliente (caché compartida con INFORME_PDF). */
    private byte[] informePdf(EmisionEstudioService.Informe informe, Usuario usuario) throws Exception {
        String ruta = AlmacenTrabajos.rutaCacheInforme(informe.idSubproceso(), informe.fechaPublicado());
        ReentrantLock lock = almacen.bloqueo(ruta);
        lock.lock();
        try {
            if (!almacen.existe(ruta)) {
                almacen.escribirAtomico(ruta, pdfConverter.convertir(documentoService.generar(informe.idSubproceso(), usuario)));
            } else {
                almacen.tocar(ruta);
            }
        } finally {
            lock.unlock();
        }
        return Files.readAllBytes(almacen.resolver(ruta));
    }

    static byte[] unir(List<byte[]> partes) throws Exception {
        PDFMergerUtility fusion = new PDFMergerUtility();
        try (PDDocument destino = new PDDocument()) {
            List<PDDocument> abiertos = new ArrayList<>();
            try {
                for (byte[] p : partes) {
                    PDDocument d = Loader.loadPDF(p);
                    abiertos.add(d);
                    fusion.appendDocument(destino, d);
                }
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                destino.save(salida);
                return salida.toByteArray();
            } finally {
                for (PDDocument d : abiertos) d.close();
            }
        }
    }

    /** Portada «Informe de análisis final». Texto simple con Helvetica; si se desborda la página, sigue en la siguiente. */
    static byte[] portadaPdf(EmisionEstudioService.Portada p) throws Exception {
        PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        try (PDDocument doc = new PDDocument()) {
            Escritor w = new Escritor(doc, normal, negrita);
            w.linea("Informe de análisis final", negrita, 20);
            if (!"FINAL".equals(p.tipo())) {
                w.linea("PRELIMINAR – CIERRE PENDIENTE: " + (p.faltantes().isEmpty() ? "sin pendientes" : String.join(", ", p.faltantes())), negrita, 11);
            }
            w.espacio(8);
            w.par("Evaluado", p.evaluado());
            w.par("Documento", p.cedula());
            w.par("Cargo", p.cargo());
            w.par("Empresa", p.cliente());
            w.par("Estudio", p.estudio());
            if ("FINAL".equals(p.tipo()) && p.nivel() != null) w.par("Nivel de riesgo del estudio", p.nivel());
            w.espacio(8);
            w.linea("Nota del gestor", negrita, 12);
            w.parrafo(p.concepto() == null ? "" : p.concepto());
            if (!p.alertas().isEmpty()) {
                w.espacio(8);
                w.linea("Alertas", negrita, 12);
                for (String a : p.alertas()) w.parrafo("- " + a);
            }
            w.espacio(8);
            w.linea("Contenido de este documento", negrita, 12);
            int n = 1;
            for (var i : p.informes()) w.parrafo((n++) + ". " + i.nombre());
            w.cerrar();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.save(salida);
            return salida.toByteArray();
        }
    }

    /** Escritor mínimo de texto con salto de línea y de página. Reemplaza lo que Helvetica no codifica por «?» en vez de fallar. */
    private static final class Escritor {
        private static final float MARGEN = 56, ANCHO = PDRectangle.A4.getWidth() - 2 * MARGEN;
        private final PDDocument doc;
        private final PDType1Font normal, negrita;
        private PDPageContentStream cs;
        private float y;

        Escritor(PDDocument doc, PDType1Font normal, PDType1Font negrita) throws Exception {
            this.doc = doc; this.normal = normal; this.negrita = negrita;
            nuevaPagina();
        }

        private void nuevaPagina() throws Exception {
            if (cs != null) cs.close();
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            cs = new PDPageContentStream(doc, pagina);
            y = PDRectangle.A4.getHeight() - MARGEN;
        }

        void espacio(float h) { y -= h; }

        void linea(String texto, PDType1Font f, float tam) throws Exception {
            for (String l : partir(limpiar(texto), f, tam)) escribir(l, f, tam);
        }

        void par(String etiqueta, String valor) throws Exception {
            escribir(etiqueta + ": " + limpiar(valor == null ? "" : valor), normal, 11);
        }

        void parrafo(String texto) throws Exception {
            for (String bloque : limpiar(texto).split("\\R")) for (String l : partir(bloque, normal, 11)) escribir(l, normal, 11);
        }

        private void escribir(String l, PDType1Font f, float tam) throws Exception {
            if (y < MARGEN + tam) nuevaPagina();
            cs.beginText(); cs.setFont(f, tam); cs.newLineAtOffset(MARGEN, y); cs.showText(l); cs.endText();
            y -= tam * 1.5f;
        }

        private List<String> partir(String texto, PDType1Font f, float tam) throws Exception {
            List<String> r = new ArrayList<>();
            StringBuilder actual = new StringBuilder();
            for (String palabra : texto.split(" ")) {
                String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;
                if (f.getStringWidth(prueba) / 1000 * tam > ANCHO && actual.length() > 0) { r.add(actual.toString()); actual = new StringBuilder(palabra); }
                else actual = new StringBuilder(prueba);
            }
            r.add(actual.toString());
            return r;
        }

        private String limpiar(String t) {
            StringBuilder b = new StringBuilder();
            for (char c : t.toCharArray()) b.append(codificable(c) ? c : (Character.isWhitespace(c) ? ' ' : '?'));
            return b.toString().replace("\t", " ");
        }

        /** Lo que Helvetica (WinAnsi) dibuja: Latin-1 imprimible más el guion largo, las comillas tipográficas y la viñeta. */
        private static boolean codificable(char c) {
            return (c >= 32 && c < 256 && c != 127) || c == '\n' || c == '–' || c == '—' || c == '‘' || c == '’'
                    || c == '“' || c == '”' || c == '•';
        }

        void cerrar() throws Exception { cs.close(); }
    }
}
