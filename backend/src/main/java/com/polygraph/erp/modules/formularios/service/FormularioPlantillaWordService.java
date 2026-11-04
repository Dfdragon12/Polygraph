package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.AnalisisPlantillaWordResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioFila;
import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaConfig;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.shared.enums.CampoCandidato;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pro.verron.officestamper.preset.Image;
import pro.verron.officestamper.preset.OfficeStampers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Genera el documento a partir de un .docx diseñado en Word con marcadores ${...} — usa
 *  office-stamper (Spring Expression Language sobre el .docx). FormularioDocumentoService reúne
 *  los datos ya guardados y se los pasa aquí; el constructor de secciones/columnas de
 *  admin/catálogo → Plantilla es lo que define qué marcadores existen. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormularioPlantillaWordService {

    private final CandidatoInfoService candidatoInfoService;
    /** Lo que el cliente pidió al solicitar (tablas preguntas_cliente y solicitud_cliente). Opcional: sin él esas tablas salen vacías. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.polygraph.erp.modules.solicitudes.service.SolicitudExtraService solicitudExtraService;
    private final AlmacenamientoDocumentosService almacenamientoService;
    /** Por campo (no por constructor) para no tocar a quien arma este servicio a mano; sin él, el marcador ev_foto sale vacío. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FotoEvaluadoService fotoEvaluadoService;
    private final com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository codigoRepository;

    /** Anexo 1 del informe sale del CATÁLOGO DE HALLAZGOS de la plataforma (pedido del usuario
     *  2026-10-06), no de un texto fijo en el Word: repeatTableRow(tablas['anexo_catalogo']) con
     *  ${['a1_ref']}, ${['a1_hallazgo']}, ${['a1_calificacion']}; las alertas aparte en
     *  tablas['anexo_alertas']. Solo los hallazgos activos que USA la plantilla (los que ofrece
     *  algún campo de hallazgo o genera alguna opción), en el orden del catálogo (pedido del usuario
     *  2026-10-06). usados null = todos los del área del servicio. */
    public static final java.util.Set<String> TABLAS_CATALOGO = java.util.Set.of("anexo_catalogo", "anexo_alertas");
    /** Tablas que arma el sistema con lo que el cliente pidió al solicitar (siempre disponibles, vacías si no pidió nada). */
    public static final java.util.Set<String> TABLAS_SOLICITUD = java.util.Set.of("preguntas_cliente", "solicitud_cliente");

    Map<String, List<Map<String, Object>>> tablasCatalogo(com.polygraph.erp.modules.catalogo.entity.TipoProgreso tipo) {
        return tablasCatalogo(tipo, null);
    }

    /** Códigos de hallazgo que usa la plantilla guardada (columnas activas). */
    static java.util.Set<String> hallazgosUsados(List<FormularioSeccion> secciones) {
        java.util.Set<String> r = new java.util.HashSet<>();
        for (FormularioSeccion s : secciones) {
            if (!Boolean.TRUE.equals(s.getActivo())) continue;
            for (FormularioColumna c : s.getColumnas()) {
                if (!Boolean.TRUE.equals(c.getActivo())) continue;
                if ("HALLAZGO".equals(c.getUsoCalificacion())) r.addAll(OpcionesLista.leer(c.getOpciones()));
                c.getHallazgos().forEach(h -> r.add(h.getCodigo()));
            }
        }
        return r;
    }

    /** Lo mismo, desde lo que está armando el admin (vista previa y análisis del Word). */
    static java.util.Set<String> hallazgosUsados(GuardarPlantillaRequest req) {
        java.util.Set<String> r = new java.util.HashSet<>();
        for (var s : req.secciones()) {
            if (s.columnas() == null) continue;
            for (var c : s.columnas()) {
                var cal = c.calificacion();
                if (cal == null) continue;
                if ("HALLAZGO".equals(cal.uso()) && c.opciones() != null) r.addAll(c.opciones());
                if (cal.porOpcion() != null) cal.porOpcion().forEach(h -> r.add(h.codigo()));
            }
        }
        return r;
    }

    Map<String, List<Map<String, Object>>> tablasCatalogo(com.polygraph.erp.modules.catalogo.entity.TipoProgreso tipo,
                                                          java.util.Set<String> usados) {
        List<Map<String, Object>> catalogo = new ArrayList<>();
        List<Map<String, Object>> alertas = new ArrayList<>();
        (codigoRepository == null ? List.<com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion>of() : codigoRepository.findAll()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .filter(c -> usados == null || usados.contains(c.getCodigo()))
                .sorted(Comparator.comparing(com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion::getOrden)
                        .thenComparing(com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion::getCodigo))
                .forEach(c -> {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("etiqueta", "");
                    fila.put("a1_ref", c.getCodigo());
                    fila.put("a1_hallazgo", c.getDescripcion());
                    boolean alerta = Boolean.TRUE.equals(c.getEsAlerta());
                    fila.put("a1_calificacion", Boolean.TRUE.equals(c.getCritico()) ? "Crítico" : alerta ? "Alerta" : String.valueOf(c.getPuntos()));
                    (alerta ? alertas : catalogo).add(fila);
                });
        Map<String, List<Map<String, Object>>> r = new HashMap<>();
        r.put("anexo_catalogo", catalogo);
        r.put("anexo_alertas", alertas);
        return r;
    }

    /** valoresCampos/filasPorSeccion/valoresPorFila ya vienen calculados por
     *  FormularioDocumentoService — se reciben en vez de recalcularlos para no repetir las mismas
     *  consultas dos veces. actorPuedeEditarPlantilla: GESTOR/ADMIN_POLYGRAPH (pueden entrar a
     *  admin/catálogo → Plantilla a corregir marcadores) reciben el mensaje técnico de siempre;
     *  ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR (no tienen acceso a esa pantalla, ni deberían tener
     *  que diagnosticar un problema de marcadores) reciben un mensaje que los manda a avisarle al
     *  administrador en vez de "revisa los marcadores". */
    public byte[] generar(FormularioPlantillaConfig config, Candidato candidato, Cliente cliente, List<FormularioSeccion> secciones,
                           Map<Integer, String> valoresCampos, Map<Integer, List<FormularioFila>> filasPorSeccion,
                           Map<Integer, Map<Integer, String>> valoresPorFila, boolean actorPuedeEditarPlantilla) {
        return generar(config, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila,
                actorPuedeEditarPlantilla, null, null);
    }

    /** riesgo/calificacion: marcadores de la calificación de riesgo (ver ContextoPlantillaWord). */
    public byte[] generar(FormularioPlantillaConfig config, Candidato candidato, Cliente cliente, List<FormularioSeccion> secciones,
                           Map<Integer, String> valoresCampos, Map<Integer, List<FormularioFila>> filasPorSeccion,
                           Map<Integer, Map<Integer, String>> valoresPorFila, boolean actorPuedeEditarPlantilla,
                           Map<String, String> riesgo, Map<String, String> calificacion) {
        return generar(config, null, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila,
                actorPuedeEditarPlantilla, riesgo, calificacion, null);
    }

    /** subproceso: para los datos del estudio (columnas campoServicio — N° de estudio, cliente,
     *  cargo, profesional). null = esos campos salen vacíos. */
    public byte[] generar(FormularioPlantillaConfig config, ServicioSubproceso subproceso, Candidato candidato, Cliente cliente,
                           List<FormularioSeccion> secciones,
                           Map<Integer, String> valoresCampos, Map<Integer, List<FormularioFila>> filasPorSeccion,
                           Map<Integer, Map<Integer, String>> valoresPorFila, boolean actorPuedeEditarPlantilla,
                           Map<String, String> riesgo, Map<String, String> calificacion, com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario calculo) {
        return generar(config, subproceso, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila,
                actorPuedeEditarPlantilla, riesgo, calificacion, calculo, Set.of());
    }

    /** seccionesOmitidas: ids de las secciones opcionales que quien diligencia quitó (V212) —
     *  salen vacías y su bloque repeatDocPart(secciones['clave']) desaparece del documento. */
    public byte[] generar(FormularioPlantillaConfig config, ServicioSubproceso subproceso, Candidato candidato, Cliente cliente,
                           List<FormularioSeccion> secciones,
                           Map<Integer, String> valoresCampos, Map<Integer, List<FormularioFila>> filasPorSeccion,
                           Map<Integer, Map<Integer, String>> valoresPorFila, boolean actorPuedeEditarPlantilla,
                           Map<String, String> riesgo, Map<String, String> calificacion,
                           com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario calculo, Set<Integer> seccionesOmitidas) {
        String ruta = config != null ? config.getPlantillaWordRuta() : null;
        if (ruta == null || ruta.isBlank()) {
            throw new ApiException("Esta plantilla todavía no tiene un documento Word con marcadores subido.",
                    HttpStatus.BAD_REQUEST);
        }

        ContextoPlantillaWord base = construirContexto(subproceso, candidato, cliente, secciones, valoresCampos, filasPorSeccion,
                valoresPorFila, calculo, seccionesOmitidas);
        ContextoPlantillaWord contexto = new ContextoPlantillaWord(base.getCandidato(), base.getCampos(), base.getTablas(),
                riesgo, calificacion).conSeccionesOmitidas(clavesDe(secciones, seccionesOmitidas));
        contexto.soportesAnexo().addAll(base.soportesAnexo());

        // Se carga el archivo ANTES del try de generación, y su propia excepción (ej. "Archivo no
        // encontrado" si se borró del disco o el entorno perdió el volumen de uploads) se deja
        // propagar tal cual — antes quedaba atrapada por el catch de abajo y se disfrazaba de
        // "revisa los marcadores", un diagnóstico completamente equivocado (el archivo ni
        // siquiera llegó a leerse, mucho menos a intentar resolver un marcador).
        InputStream plantilla;
        try {
            plantilla = almacenamientoService.cargarComoResource(ruta).getInputStream();
        } catch (ApiException e) {
            // El .docx referenciado ya no está en disco (ej. se borró de uploads, o el subproceso se
            // completó con una plantilla que después se perdió — ver ruta_word_congelada). Es un
            // problema de configuración, no pasajero: 409 para que la cola de trabajos NO lo
            // reintente (reintentar no hace aparecer el archivo). La ruta va en la causa, que el
            // admin ve completa en Trabajos en segundo plano, y en el log.
            log.error("Falta el archivo de la plantilla Word: {}", ruta);
            String mensaje = actorPuedeEditarPlantilla
                    ? "No se pudo generar el documento: falta el archivo Word de la plantilla (" + ruta
                            + "). Vuelve a subirlo en admin/catálogo → Plantilla."
                    : "Este informe no se puede generar porque falta el documento Word de su plantilla. Tu gestor debe revisarlo.";
            ApiException faltante = new ApiException(mensaje, HttpStatus.CONFLICT);
            faltante.initCause(new IllegalStateException("Archivo de plantilla inexistente: " + ruta));
            throw faltante;
        } catch (Exception e) {
            log.error("No se pudo leer el archivo de la plantilla Word ({}): {}", ruta, e.getMessage(), e);
            throw new ApiException("No se pudo generar el documento — no se pudo leer el archivo de la plantilla.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        try (plantilla; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            OfficeStampers.docxStamper().stamp(plantilla, contexto, salida);
            return SoportesAnexoWord.aplicar(limpiarComentarios(salida.toByteArray()), contexto.soportesAnexo());
        } catch (Exception e) {
            log.error("No se pudo generar el documento desde la plantilla Word ({}): {}", ruta, e.getMessage(), e);
            // El admin sí puede actuar sobre el detalle técnico (es quien entra a corregir el
            // .docx); el analista/poligrafista/visitador no tiene esa pantalla, así que para él
            // se queda el mensaje genérico de siempre — mostrarle "SpelEvaluationException:..."
            // no le sirve para nada, solo lo confunde.
            String mensaje = actorPuedeEditarPlantilla
                    ? "No se pudo generar el documento a partir de la plantilla Word: " + causaLegible(e)
                            + " — revisa que los marcadores estén bien escritos."
                    : "No se pudo generar el documento — hay un problema con los marcadores de la plantilla. Avísale al administrador para que la revise.";
            // 422 y no 500: un marcador mal escrito falla igual cada vez; la cola de trabajos no
            // reintenta los 4xx (antes el cliente esperaba ~5 min de reintentos inútiles).
            ApiException error = new ApiException(mensaje, HttpStatus.UNPROCESSABLE_ENTITY);
            error.initCause(e);
            throw error;
        }
    }

    private static final Set<String> PARTES_COMENTARIOS = Set.of(
            "word/comments.xml", "word/commentsExtended.xml", "word/commentsIds.xml", "word/commentsExtensible.xml");
    private static final Pattern PATRON_COMMENT_RANGE = Pattern.compile("<w:commentRangeStart[^>]*/>|<w:commentRangeEnd[^>]*/>");
    private static final Pattern PATRON_COMMENT_REF_RUN = Pattern.compile(
            "<w:r>(?:(?!</w:r>)[\\s\\S])*?<w:commentReference[^>]*/>(?:(?!</w:r>)[\\s\\S])*?</w:r>");
    private static final Pattern PATRON_CONTENT_TYPE_OVERRIDE = Pattern.compile(
            "<Override[^>]*PartName=\"/word/comments[^\"]*\"[^>]*/>");
    private static final Pattern PATRON_RELATIONSHIP = Pattern.compile(
            "<Relationship[^>]*Target=\"comments[^\"]*\"[^>]*/>");
    /** <w:tblpPr> = tabla "flotante" (anclada a una posición fija de la página, como si fuera un
     *  objeto con ajuste de texto — Word: Propiedades de tabla → Tabla → Posición). Cuando una
     *  tabla así se repite vía repeatDocPart, TODAS las copias quedan ancladas en las MISMAS
     *  coordenadas absolutas — se dibujan una encima de la otra en vez de una debajo de la otra,
     *  así que solo se alcanza a ver la última (con Word) o el resultado sale con contenido
     *  encimado/recortado (con la conversión a PDF vía LibreOffice, más estricta con esto que
     *  Word). Se le quita la posición fija SOLO a las tablas que quedaron duplicadas en la misma
     *  coordenada (ver quitarPosicionDeTablasDuplicadas) — el resto del documento (encabezado,
     *  pie de página, cualquier tabla que no se repitió) conserva su posición flotante tal cual
     *  la diseñó el administrador en Word. Nunca se toca la plantilla subida, solo el documento
     *  ya generado. */
    private static final Pattern PATRON_TABLA = Pattern.compile("<w:tbl>[\\s\\S]*?</w:tbl>");
    private static final Pattern PATRON_TABLA_FLOTANTE = Pattern.compile("<w:tblpPr[^/]*/>");
    private static final Pattern PATRON_TBLP_Y = Pattern.compile("<w:tblpPr\\b[^>]*\\bw:tblpY=\"(-?\\d+)\"");
    /** La posición horizontal de una tabla flotante casi nunca es "pegada al margen izquierdo":
     *  en las plantillas reales viene como w:tblpXSpec="center" (centrada dentro del margen).
     *  Quitar <w:tblpPr> sin más pierde ese centrado — la tabla cae alineada a la izquierda por
     *  defecto y se ve "corrida"/fuera de margen. Este patrón rescata ese valor para reponerlo
     *  como <w:jc> (alineación normal de tabla en línea) al quitar la posición flotante. */
    private static final Pattern PATRON_TBLP_XSPEC = Pattern.compile("<w:tblpPr\\b[^>]*\\bw:tblpXSpec=\"(left|right|center)\"");
    private static final Pattern PATRON_TBLPR_APERTURA = Pattern.compile("<w:tblPr>");
    /** repeatDocPart pega cada copia justo después de la anterior, sin ningún <w:p> entre
     *  </w:tbl> y <w:tbl> — Word tolera eso al abrir el archivo, pero LibreOffice (usado para el
     *  PDF) las dibuja pegadas, sin separación visual entre un "cuadro" repetido y el siguiente.
     *  Se inserta este párrafo SOLO entre copias consecutivas del mismo cuadro duplicado, nunca
     *  entre tablas normales del documento. A propósito NO es un párrafo vacío: un párrafo sin
     *  ningún <w:r> (sin contenido real) solo depende de "espacio después" para tener altura, y
     *  eso se demostró poco confiable en la conversión a PDF — a veces esa altura queda en 0 y
     *  las tablas vuelven a quedar pegadas. Con un espacio real (" ") y w:lineRule="exact" la
     *  altura de la línea queda fija sin importar fuente/compatibilidad, así que el espacio
     *  siempre se ve, sin depender de esa ambigüedad. */
    private static final String PARRAFO_SEPARADOR_ENTRE_COPIAS =
            "<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"240\" w:lineRule=\"exact\"/></w:pPr>"
                    + "<w:r><w:t xml:space=\"preserve\"> </w:t></w:r></w:p>";

    /** Limpieza del documento YA generado — dos arreglos separados, ambos sobre el paquete final,
     *  nunca sobre la plantilla que subió el administrador:
     *  (1) El comentario de Word que dice repeatTableRow(...)/repeatDocPart(...) es solo una
     *  instrucción para office-stamper — una vez procesada, la etiqueta que lo ancla
     *  (commentRangeStart/commentReference) desaparece del cuerpo del documento, pero el PAQUETE
     *  del .docx sigue trayendo word/comments.xml (y, si la plantilla se guardó alguna vez desde
     *  Word, commentsExtended.xml/commentsIds.xml) con esos comentarios — ahora sin ningún ancla
     *  real que los referencie. Word detecta esa referencia colgante como contenido ilegible y
     *  ofrece "reparar" al abrir. Se limpian aquí para que el .docx que se descarga nunca traiga
     *  comentarios (nunca hacían falta en el resultado final, solo durante el llenado).
     *  (2) Quita la posición flotante de las tablas repetidas por repeatDocPart — ver
     *  PATRON_TABLA_FLOTANTE. */
    private byte[] limpiarComentarios(byte[] docx) {
        try (ZipInputStream origen = new ZipInputStream(new ByteArrayInputStream(docx));
             ByteArrayOutputStream salida = new ByteArrayOutputStream();
             ZipOutputStream destino = new ZipOutputStream(salida)) {
            ZipEntry entrada;
            while ((entrada = origen.getNextEntry()) != null) {
                if (PARTES_COMENTARIOS.contains(entrada.getName())) continue;
                byte[] contenido = origen.readAllBytes();
                if (entrada.getName().equals("[Content_Types].xml")) {
                    contenido = PATRON_CONTENT_TYPE_OVERRIDE.matcher(new String(contenido, StandardCharsets.UTF_8))
                            .replaceAll("").getBytes(StandardCharsets.UTF_8);
                } else if (entrada.getName().equals("word/_rels/document.xml.rels")) {
                    contenido = PATRON_RELATIONSHIP.matcher(new String(contenido, StandardCharsets.UTF_8))
                            .replaceAll("").getBytes(StandardCharsets.UTF_8);
                } else if (entrada.getName().equals("word/document.xml")) {
                    String texto = new String(contenido, StandardCharsets.UTF_8);
                    texto = PATRON_COMMENT_RANGE.matcher(texto).replaceAll("");
                    texto = PATRON_COMMENT_REF_RUN.matcher(texto).replaceAll("");
                    texto = quitarPosicionDeTablasDuplicadas(texto);
                    contenido = texto.getBytes(StandardCharsets.UTF_8);
                }
                destino.putNextEntry(new ZipEntry(entrada.getName()));
                destino.write(contenido);
                destino.closeEntry();
            }
            destino.finish();
            return salida.toByteArray();
        } catch (Exception e) {
            log.warn("No se pudieron limpiar los comentarios del documento generado — se entrega tal cual: {}", e.getMessage());
            return docx;
        }
    }

    /** Recorre cada <w:tbl> del documento y agrupa las que son tablas flotantes por su coordenada
     *  vertical (w:tblpY). Dos o más tablas ancladas EXACTAMENTE en la misma coordenada solo
     *  pueden ser copias de la misma tabla repetidas por repeatDocPart — una tabla diseñada a
     *  mano en Word nunca comparte la posición flotante de otra a propósito. A esas (y SOLO a
     *  esas) se les quita <w:tblpPr> para que caigan en línea, una debajo de la otra, en vez de
     *  superpuestas; cualquier tabla con una posición única (encabezado, pie de página, etc.)
     *  se deja intacta, tal como la armó el administrador. */
    private String quitarPosicionDeTablasDuplicadas(String documentXml) {
        List<String> bloques = new ArrayList<>();
        List<String> claves = new ArrayList<>();
        Map<String, Integer> conteoPorClave = new HashMap<>();

        Matcher m = PATRON_TABLA.matcher(documentXml);
        while (m.find()) {
            String bloque = m.group();
            bloques.add(bloque);
            Matcher y = PATRON_TBLP_Y.matcher(bloque);
            String clave = y.find() ? y.group(1) : null;
            claves.add(clave);
            if (clave != null) conteoPorClave.merge(clave, 1, Integer::sum);
        }
        if (bloques.isEmpty()) return documentXml;

        StringBuilder resultado = new StringBuilder();
        int ultimoFin = 0;
        int indice = 0;
        String claveDuplicadaAnterior = null;
        m.reset();
        while (m.find()) {
            String espacioEntreTablas = documentXml.substring(ultimoFin, m.start());
            resultado.append(espacioEntreTablas);
            String bloque = bloques.get(indice);
            String clave = claves.get(indice);
            boolean esDuplicada = clave != null && conteoPorClave.get(clave) > 1;
            if (esDuplicada && clave.equals(claveDuplicadaAnterior) && !espacioEntreTablas.contains("<w:p")) {
                resultado.append(PARRAFO_SEPARADOR_ENTRE_COPIAS);
            }
            if (esDuplicada) {
                bloque = quitarFlotacionPreservandoAlineacion(bloque);
            }
            resultado.append(bloque);
            ultimoFin = m.end();
            claveDuplicadaAnterior = esDuplicada ? clave : null;
            indice++;
        }
        resultado.append(documentXml.substring(ultimoFin));
        return resultado.toString();
    }

    /** Quita <w:tblpPr> de una tabla duplicada, pero antes rescata su w:tblpXSpec (izquierda/
     *  centro/derecha) y lo repone como <w:jc> — la alineación equivalente para una tabla en
     *  línea (no flotante) — para que no pierda su posición horizontal dentro del margen al
     *  dejar de flotar. Sin esto, una tabla centrada (el caso típico en las plantillas reales)
     *  se ve pegada al margen izquierdo tras el arreglo. */
    private String quitarFlotacionPreservandoAlineacion(String bloqueTabla) {
        Matcher xSpec = PATRON_TBLP_XSPEC.matcher(bloqueTabla);
        String alineacion = xSpec.find() ? xSpec.group(1) : null;
        String bloqueSinFlotar = PATRON_TABLA_FLOTANTE.matcher(bloqueTabla).replaceAll("");
        if (alineacion != null) {
            bloqueSinFlotar = PATRON_TBLPR_APERTURA.matcher(bloqueSinFlotar)
                    .replaceFirst("<w:tblPr><w:jc w:val=\"" + alineacion + "\"/>");
        }
        return bloqueSinFlotar;
    }

    // Sin "private" a propósito — FormularioPlantillaWordServiceValidacionTest la prueba directo,
    // sin tener que montar un XWPFDocument real solo para verificar cómo se arma el contexto.
    ContextoPlantillaWord construirContexto(Candidato candidato, Cliente cliente, List<FormularioSeccion> secciones,
                                                      Map<Integer, String> valoresCampos,
                                                      Map<Integer, List<FormularioFila>> filasPorSeccion,
                                                      Map<Integer, Map<Integer, String>> valoresPorFila) {
        return construirContexto(null, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila, null);
    }

    ContextoPlantillaWord construirContexto(ServicioSubproceso subproceso, Candidato candidato, Cliente cliente,
                                            List<FormularioSeccion> secciones,
                                            Map<Integer, String> valoresCampos,
                                            Map<Integer, List<FormularioFila>> filasPorSeccion,
                                            Map<Integer, Map<Integer, String>> valoresPorFila,
                                            com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario calculo) {
        return construirContexto(subproceso, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila, calculo, Set.of());
    }

    /** Claves (las de secciones['clave'] en el Word) de las secciones quitadas. */
    private static Set<String> clavesDe(List<FormularioSeccion> secciones, Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<String> claves = new HashSet<>();
        for (FormularioSeccion s : secciones) {
            if (ids.contains(s.getIdSeccion()) && s.getClave() != null) claves.add(s.getClave());
        }
        return claves;
    }

    ContextoPlantillaWord construirContexto(ServicioSubproceso subproceso, Candidato candidato, Cliente cliente,
                                            List<FormularioSeccion> secciones,
                                            Map<Integer, String> valoresCampos,
                                            Map<Integer, List<FormularioFila>> filasPorSeccion,
                                            Map<Integer, Map<Integer, String>> valoresPorFila,
                                            /** Lo calculado por el subservicio (ver CalculoSubservicioService); null = nada calculado. */
                                            com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario calculo,
                                            /** Secciones que quitó quien diligencia (V212): sus campos y filas salen vacíos. */
                                            Set<Integer> seccionesOmitidas) {
        // Todas las claves de CampoCandidato, no solo las que se usan en el constructor — quien
        // diseña el .docx puede usar cualquiera de estas libremente.
        Map<String, String> mapaCandidato = new HashMap<>();
        for (CampoCandidato campo : CampoCandidato.values()) {
            mapaCandidato.put(campo.name(), candidatoInfoService.resolverUno(candidato, campo.name()));
        }

        Map<String, Object> mapaCampos = new HashMap<>();
        // Listas de la hoja de vida del candidato (referencias, educación, experiencia laboral,
        // inactividades) — fijas, siempre disponibles, no dependen de lo que arme el constructor.
        // Nunca traen imágenes (solo texto/fechas/booleanos), pero se copian a Map<String,Object>
        // para que el tipo coincida con el resto de mapaTablas.
        Map<String, List<Map<String, Object>>> mapaTablas = new HashMap<>();
        candidatoInfoService.resolverTablasFijas(candidato).forEach((clave, filas) ->
                mapaTablas.put(clave, filas.stream().<Map<String, Object>>map(HashMap::new).toList()));
        mapaTablas.put("preguntas_cliente", List.of());
        mapaTablas.put("solicitud_cliente", List.of());
        // Módulos que el Word puede encerrar en repeatDocPart/repeatTableRow y que hoy ninguna plantilla llena (Visita domiciliaria: salud y
        // pregunta 10 sindical, apagadas por decisión jurídica): vacías para que el bloque desaparezca en vez de fallar. Una sección con esa clave las pisa.
        mapaTablas.put("salud", List.of());
        mapaTablas.put("ps_sindical", List.of());
        if (solicitudExtraService != null && subproceso != null && subproceso.getServicio() != null) {
            mapaTablas.putAll(solicitudExtraService.tablasParaWord(subproceso.getServicio().getIdServicio()));
        }
        mapaTablas.putAll(tablasCatalogo(subproceso != null ? subproceso.getTipoProgreso()
                : secciones.isEmpty() ? null : secciones.get(0).getTipoProgreso(), hallazgosUsados(secciones)));

        List<SoportesAnexoWord.Soporte> soportes = new ArrayList<>();
        for (FormularioSeccion seccion : secciones) {
            List<FormularioColumna> columnas = seccion.getColumnas().stream()
                    .sorted(Comparator.comparing(FormularioColumna::getOrden)).toList();

            // Quitada por quien diligencia: nada de lo suyo sale, aunque el Word no la encierre en
            // repeatDocPart(secciones['clave']). Una tabla fija (referencias...) es de la hoja de
            // vida y sigue disponible: solo desaparece si el Word usa el bloque condicional.
            if (seccionesOmitidas != null && seccionesOmitidas.contains(seccion.getIdSeccion())) {
                if (seccion.getTipo() == TipoSeccionFormulario.CAMPOS) {
                    for (FormularioColumna columna : columnas) {
                        if (columna.getClave() != null) mapaCampos.put(columna.getClave(), "");
                    }
                } else if (seccion.getClave() != null && seccion.getOrigenFijo() == null) {
                    mapaTablas.put(seccion.getClave(), List.of());
                }
                continue;
            }

            if (seccion.getTipo() == TipoSeccionFormulario.CAMPOS) {
                for (FormularioColumna columna : columnas) {
                    if (columna.getClave() == null) continue;
                    // Un campo que diligencia una persona también puede salir con el formato del cálculo (ej. "$2.800 mil",
                    // "38 %", "No reportado" en Análisis financiero): si el cálculo trae su clave, esa es la que se imprime.
                    boolean formateadoPorElCalculo = !Boolean.TRUE.equals(columna.getCalculado()) && calculo != null
                            && columna.getTipoDato() != TipoDatoFormulario.ARCHIVO && calculo.campos().containsKey(columna.getClave());
                    mapaCampos.put(columna.getClave(), Boolean.TRUE.equals(columna.getCalculado()) || formateadoPorElCalculo
                            ? ValoresPorClave.campo(calculo, columna)
                            : valorColumnaTabla(columna, subproceso, candidato, cliente, valoresCampos.get(columna.getIdColumna()),
                                    null, soportes));
                }
            } else {
                if (seccion.getClave() == null) continue;
                // Origen automático: la tabla fija (referencias, educación...) ya está en
                // mapaTablas desde resolverTablasFijas() de arriba, leída en vivo de la hoja de
                // vida real del candidato — el analista la edita directamente ahí (ver
                // CandidatoHojaVidaController), no hay una copia aparte que sobrescribir aquí.
                // Lo único que SÍ agrega esta sección son sus columnas de VALIDACIÓN (ver
                // FormularioFila.idOrigenFijo) — se mezclan dentro del MISMO mapa de fila que ya
                // tiene los datos fijos, para que en Word se usen con el mismo
                // repeatTableRow(tablas['clave']), ej. ${nombre} (fijo) y ${verificado}
                // (validación) en la misma fila, sin un marcador de tabla aparte.
                if (seccion.getOrigenFijo() != null) {
                    Map<String, Map<String, Object>> indiceFilasFijas = new HashMap<>();
                    for (Map<String, Object> filaFija : mapaTablas.getOrDefault(seccion.getOrigenFijo(), List.of())) {
                        Object id = filaFija.get("id");
                        if (id != null) indiceFilasFijas.put(String.valueOf(id), filaFija);
                    }
                    for (FormularioFila filaValidacion : filasPorSeccion.getOrDefault(seccion.getIdSeccion(), List.of())) {
                        if (filaValidacion.getIdOrigenFijo() == null) continue;
                        Map<String, Object> filaFija = indiceFilasFijas.get(String.valueOf(filaValidacion.getIdOrigenFijo()));
                        if (filaFija == null) continue; // fila de validación huérfana (su fila original ya no existe)
                        Map<Integer, String> valoresFila = valoresPorFila.getOrDefault(filaValidacion.getIdFila(), Map.of());
                        for (FormularioColumna columna : columnas) {
                            if (columna.getClave() == null) continue;
                            filaFija.put(columna.getClave(), valorColumnaTabla(columna, subproceso, candidato, cliente,
                                    valoresFila.get(columna.getIdColumna()), null, soportes));
                        }
                    }
                    continue;
                }
                // Tabla que arma el subservicio (ej. hallazgos): sus filas salen completas del cálculo.
                if (Boolean.TRUE.equals(seccion.getCalculada())) {
                    List<Map<String, Object>> filasCalculadas = new ArrayList<>();
                    for (Map<String, String> filaCalculada : ValoresPorClave.filasCalculadas(calculo, seccion)) {
                        Map<String, Object> fila = new HashMap<>(filaCalculada);
                        fila.put("etiqueta", "");
                        for (FormularioColumna columna : columnas) {
                            if (columna.getClave() != null) fila.putIfAbsent(columna.getClave(), "");
                        }
                        filasCalculadas.add(fila);
                    }
                    mapaTablas.put(seccion.getClave(), filasCalculadas);
                    continue;
                }
                List<FormularioFila> filas = ValoresPorClave.filasOrdenadas(seccion, filasPorSeccion);
                List<Map<String, Object>> filasContexto = new ArrayList<>();
                for (int indice = 0; indice < filas.size(); indice++) {
                    FormularioFila fila = filas.get(indice);
                    Map<Integer, String> valoresFila = valoresPorFila.getOrDefault(fila.getIdFila(), Map.of());
                    Map<String, Object> filaContexto = new HashMap<>();
                    filaContexto.put("etiqueta", fila.getEtiqueta());
                    for (FormularioColumna columna : columnas) {
                        if (columna.getClave() == null) continue;
                        filaContexto.put(columna.getClave(), Boolean.TRUE.equals(columna.getCalculado())
                                ? ValoresPorClave.celda(calculo, seccion, indice, columna)
                                : valorColumnaTabla(columna, subproceso, candidato, cliente, valoresFila.get(columna.getIdColumna()),
                                        tituloFila(fila, columnas, valoresFila), soportes));
                    }
                    filasContexto.add(filaContexto);
                }
                mapaTablas.put(seccion.getClave(), filasContexto);
            }
        }

        // Foto del evaluado de la poligrafía (PF-06): la vigente, recortada 3:4, de unos 3,3 cm de ancho. Sin foto, el marcador sale vacío.
        mapaCampos.put("ev_foto", fotoParaInforme(subproceso));
        // VD-03: números que se muestran en el protocolo de seguridad de la visita (continuos; las claves ps_* no cambian).
        mapaCampos.putAll(NumeracionProtocolo.marcadores(NumeracionProtocolo.SINDICAL_ACTIVA));

        ContextoPlantillaWord contexto = new ContextoPlantillaWord(mapaCandidato, mapaCampos, mapaTablas);
        contexto.soportesAnexo().addAll(soportes);
        return contexto;
    }

    /** Tabla o campo suelto: la imagen no va dentro de la celda: la celda dice "Ver soporte N" (se vuelve un
     *  enlace a la imagen) y la imagen sale completa en el anexo del final. Solo imágenes
     *  jpg/png; cualquier otra cosa (PDF...) sigue como referencia de texto, y si un archivo no se
     *  puede leer se deja su nombre en vez de romper el documento. */
    private Object valorColumnaTabla(FormularioColumna columna, ServicioSubproceso subproceso, Candidato candidato,
                                     Cliente cliente, String valorGuardado, String etiquetaFila,
                                     List<SoportesAnexoWord.Soporte> soportes) {
        Object valor = valorColumna(columna, subproceso, candidato, cliente, valorGuardado);
        if (columna.getTipoDato() != TipoDatoFormulario.ARCHIVO || columna.getCampoCandidato() != null
                || columna.getCampoCliente() != null || columna.getCampoServicio() != null
                || Boolean.TRUE.equals(columna.getCalculado())) {
            return valor;
        }
        List<String> enlaces = new ArrayList<>();
        List<String> otros = new ArrayList<>();
        String titulo = etiquetaFila != null && !etiquetaFila.isBlank() ? etiquetaFila : columna.getNombre();
        for (String ruta : FormularioValorFormateador.parseRutasArchivo(valorGuardado)) {
            int punto = ruta.lastIndexOf('.');
            String ext = punto < 0 ? "" : ruta.substring(punto + 1).toLowerCase();
            String nombre = ruta.substring(ruta.lastIndexOf('/') + 1);
            boolean imagen = EXTENSIONES_IMAGEN.contains(ext);
            if (!imagen && !"pdf".equals(ext)) {
                otros.add(nombre);
                continue;
            }
            try (InputStream in = almacenamientoService.cargarComoResource(ruta).getInputStream()) {
                byte[] bytes = in.readAllBytes();
                if (imagen) {
                    boolean png = "png".equals(ext);
                    int numero = soportes.size() + 1;
                    soportes.add(new SoportesAnexoWord.Soporte(numero, titulo, SoportesAnexoWord.reducirSiHaceFalta(bytes, png), png));
                    enlaces.add(SoportesAnexoWord.textoEnlace(numero));
                } else {
                    // Un PDF va al anexo página por página, como imágenes.
                    List<byte[]> paginas = SoportesAnexoWord.paginasPdf(bytes);
                    for (int i = 0; i < paginas.size(); i++) {
                        int numero = soportes.size() + 1;
                        String tituloPagina = titulo + (paginas.size() > 1 ? " · página " + (i + 1) + " de " + paginas.size() : "");
                        soportes.add(new SoportesAnexoWord.Soporte(numero, tituloPagina, paginas.get(i), true));
                        enlaces.add(SoportesAnexoWord.textoEnlace(numero));
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo leer el soporte ({}), se deja su nombre: {}", ruta, e.getMessage());
                otros.add(nombre);
            }
        }
        if (enlaces.isEmpty() && otros.isEmpty()) return valor;
        List<String> partes = new ArrayList<>(enlaces);
        if (!otros.isEmpty()) partes.add("Anexo: " + String.join(", ", otros));
        return String.join(", ", partes);
    }

    /** Cómo se nombra el soporte de una fila: su etiqueta o, si no tiene, el primer valor de texto
     *  que diligenciaron (normalmente la fuente: "Procuraduría"). */
    private static String tituloFila(FormularioFila fila, List<FormularioColumna> columnas, Map<Integer, String> valoresFila) {
        if (fila.getEtiqueta() != null && !fila.getEtiqueta().isBlank()) return fila.getEtiqueta();
        for (FormularioColumna c : columnas) {
            if (c.getTipoDato() == TipoDatoFormulario.ARCHIVO || c.getTipoDato() == TipoDatoFormulario.BOOLEANO) continue;
            String v = valoresFila.get(c.getIdColumna());
            if (v != null && !v.isBlank() && !"—".equals(v.trim())) return v.trim();
        }
        return null;
    }

    /** 3,3 cm en twips (1440 = 1 pulgada). */
    private static final int ANCHO_FOTO_EVALUADO_TWIPS = 1871;

    private Object fotoParaInforme(ServicioSubproceso subproceso) {
        if (fotoEvaluadoService == null || subproceso == null || subproceso.getId() == null) return "";
        return fotoEvaluadoService.imagenParaInforme(subproceso.getId()).<Object>map(bytes -> {
            try {
                return new Image(new java.io.ByteArrayInputStream(bytes), ANCHO_FOTO_EVALUADO_TWIPS);
            } catch (Exception e) {
                log.warn("No se pudo incrustar la foto del evaluado: {}", e.getMessage());
                return "";
            }
        }).orElse("");
    }

    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "jpeg", "png");
    /** Ancho máximo de una imagen incrustada, en twips (1440 = 1 pulgada) — lo bastante grande
     *  para verse bien, sin desbordar una celda de tabla normal. */
    private static final int ANCHO_IMAGEN_TWIPS = 2880;

    /** ARCHIVO/BOOLEANO se formatean con FormularioValorFormateador (mismo formato en todo el
     *  documento) — salvo que sea columna de candidato/cliente (siempre se resuelve en vivo,
     *  nunca desde un dato guardado) o columna ARCHIVO con una sola imagen (se incrusta de verdad
     *  en el documento en vez de solo nombrarla — ver ContextoPlantillaWord). */
    private Object valorColumna(FormularioColumna columna, ServicioSubproceso subproceso, Candidato candidato, Cliente cliente,
                                String valorGuardado) {
        if (columna.getCampoCandidato() != null) {
            return candidatoInfoService.resolverUno(candidato, columna.getCampoCandidato());
        }
        if (columna.getCampoCliente() != null) {
            return candidatoInfoService.resolverUnoCliente(cliente, columna.getCampoCliente());
        }
        if (columna.getCampoServicio() != null) {
            return candidatoInfoService.resolverUnoServicio(subproceso, columna.getCampoServicio());
        }
        // Calculada: la llena el cálculo del subservicio (docs/antecedentes, Prompt 4); mientras
        // tanto sale vacía, nunca de un dato guardado.
        if (Boolean.TRUE.equals(columna.getCalculado())) return "";
        // Lo que nadie cambió vale su valor por defecto (ej. "—" en las columnas Ref.), igual que
        // en pantalla.
        if ((valorGuardado == null || valorGuardado.isBlank()) && columna.getValorDefecto() != null) {
            valorGuardado = columna.getValorDefecto();
        }
        if (columna.getTipoDato() == TipoDatoFormulario.ARCHIVO) {
            Image imagen = intentarCargarImagen(valorGuardado);
            if (imagen != null) return imagen;
        }
        return FormularioValorFormateador.textoCelda(columna.getTipoDato(), valorGuardado);
    }

    /** Solo incrusta cuando hay EXACTAMENTE un archivo y es una imagen — con varios adjuntos, o
     *  un PDF, se sigue viendo como referencia de texto ("Anexo: ..."), como antes. Si el archivo
     *  no se puede leer (borrado a mano del disco, etc.) cae de vuelta a texto en vez de romper
     *  la generación de todo el documento. */
    private Image intentarCargarImagen(String valorGuardado) {
        List<String> rutas = FormularioValorFormateador.parseRutasArchivo(valorGuardado);
        if (rutas.size() != 1) return null;
        String ruta = rutas.get(0);
        int puntoExt = ruta.lastIndexOf('.');
        if (puntoExt < 0 || !EXTENSIONES_IMAGEN.contains(ruta.substring(puntoExt + 1).toLowerCase())) return null;
        try (InputStream in = almacenamientoService.cargarComoResource(ruta).getInputStream()) {
            return new Image(in, ANCHO_IMAGEN_TWIPS);
        } catch (Exception e) {
            log.warn("No se pudo incrustar la imagen ({}), se deja como referencia de texto: {}", ruta, e.getMessage());
            return null;
        }
    }

    /** Vista previa desde la estructura que se está editando (aunque no se haya guardado) — con
     *  datos de ejemplo. No hay ningún subproceso real de fondo todavía. */
    public byte[] generarVistaPrevia(FormularioPlantillaConfig config, GuardarPlantillaRequest req) {
        try {
            return intentarGenerarVistaPrevia(config, req);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            String ruta = config != null ? config.getPlantillaWordRuta() : null;
            log.error("No se pudo generar la vista previa desde la plantilla Word ({}): {}", ruta, e.getMessage(), e);
            throw new ApiException(
                    "No se pudo generar la vista previa: " + causaLegible(e)
                            + " — revisa que los marcadores estén bien escritos.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Mismo cuerpo de generarVistaPrevia(), pero sin envolver la excepción real en un
     *  ApiException genérico — así analizarMarcadores() puede reusarlo como un "ensayo" de
     *  generación real y detectar el mismo fallo que el admin vería al pedir la vista previa,
     *  en vez de basarse solo en comparar nombres de marcadores por texto (ver comentario de
     *  analizarMarcadores: eso deliberadamente no valida marcadores sueltos ${['clave']}, que
     *  es justo el tipo de error que SÍ revienta al generar de verdad). */
    private byte[] intentarGenerarVistaPrevia(FormularioPlantillaConfig config, GuardarPlantillaRequest req) throws Exception {
        String ruta = config != null ? config.getPlantillaWordRuta() : null;
        if (ruta == null || ruta.isBlank()) {
            throw new ApiException("Sube primero un documento Word con marcadores para poder ver la vista previa.",
                    HttpStatus.BAD_REQUEST);
        }

        Map<String, String> mapaCandidato = new HashMap<>();
        for (CampoCandidato campo : CampoCandidato.values()) {
            mapaCandidato.put(campo.name(), "(ejemplo)");
        }

        Map<String, Object> mapaCampos = new HashMap<>();
        mapaCampos.putAll(NumeracionProtocolo.marcadores(NumeracionProtocolo.SINDICAL_ACTIVA));
        Map<String, List<Map<String, Object>>> mapaTablas = new HashMap<>();
        CandidatoInfoService.COLUMNAS_TABLAS_FIJAS.forEach((claveFija, columnas) -> {
            Map<String, Object> filaEjemplo = new HashMap<>();
            columnas.forEach(c -> filaEjemplo.put(c, "(ejemplo)"));
            mapaTablas.put(claveFija, List.of(filaEjemplo));
        });
        mapaTablas.put("salud", List.of());
        mapaTablas.put("ps_sindical", List.of());
        mapaTablas.putAll(tablasCatalogo(config.getTipoProgreso(), hallazgosUsados(req)));
        for (var seccion : req.secciones()) {
            List<GuardarPlantillaRequest.ColumnaRequest> columnas = seccion.columnas() == null ? List.of() : seccion.columnas();
            if ("TABLA".equals(seccion.tipo()) && seccion.origenFijo() != null) {
                // Ya tiene su fila de ejemplo puesta arriba (COLUMNAS_TABLAS_FIJAS) — nada que
                // armar desde el request.
            } else if ("TABLA".equals(seccion.tipo())) {
                String claveSeccion = claveDePreview(seccion.clave(), seccion.nombre(), "tabla");
                List<String> etiquetas = (seccion.filasSugeridas() == null || seccion.filasSugeridas().isEmpty())
                        ? List.of("Fila de ejemplo")
                        : seccion.filasSugeridas().stream().map(GuardarPlantillaRequest.FilaSugeridaRequest::etiqueta).toList();
                List<Map<String, Object>> filas = new ArrayList<>();
                for (String etiqueta : etiquetas) {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("etiqueta", etiqueta);
                    for (var c : columnas) {
                        fila.put(claveDePreview(c.clave(), c.nombre(), "campo"), valorPreview(c));
                    }
                    filas.add(fila);
                }
                mapaTablas.put(claveSeccion, filas);
            } else {
                for (var c : columnas) {
                    mapaCampos.put(claveDePreview(c.clave(), c.nombre(), "campo"), valorPreview(c));
                }
            }
        }

        // Valores de ejemplo para los marcadores de riesgo en la vista previa.
        ContextoPlantillaWord contexto = new ContextoPlantillaWord(mapaCandidato, mapaCampos, mapaTablas,
                Map.of("nivel", "MEDIO", "puntaje", "31,0"), Map.of("nivel", "BAJO", "puntaje", "10,0"));
        try (InputStream plantilla = almacenamientoService.cargarComoResource(ruta).getInputStream();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            OfficeStampers.docxStamper().stamp(plantilla, contexto, salida);
            return limpiarComentarios(salida.toByteArray());
        }
    }

    /** Primera línea del mensaje real de la excepción (SpelEvaluationException/
     *  OfficeStamperException citan ahí mismo el marcador exacto que falló, ej. "Expression
     *  ...${['etiqueta']}... could not be resolved") — mucho más útil para el admin que el
     *  mensaje genérico de siempre, y sin el resto del stack trace en texto plano. */
    private String causaLegible(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return e.getClass().getSimpleName();
        int salto = msg.indexOf('\n');
        return salto > 0 ? msg.substring(0, salto) : msg;
    }

    private String valorPreview(GuardarPlantillaRequest.ColumnaRequest c) {
        if (c.campoCandidato() != null && !c.campoCandidato().isBlank()) return "(ejemplo)";
        if (c.campoCliente() != null && !c.campoCliente().isBlank()) return "(ejemplo)";
        if (c.campoServicio() != null && !c.campoServicio().isBlank()) return "(ejemplo)";
        if (Boolean.TRUE.equals(c.calculado())) return "(calculado)";
        return switch (c.tipoDato()) {
            case "NUMERO" -> "123";
            case "FECHA" -> "01/01/2026";
            case "BOOLEANO" -> "X";
            case "ARCHIVO" -> "Anexo: ejemplo.jpg";
            default -> "Texto de ejemplo";
        };
    }

    private static final Pattern MARCADOR = Pattern.compile(
            "\\$\\{\\s*(campos|candidato|tablas|riesgo|calificacion)\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*]\\s*}");
    /** La sintaxis REAL de una tabla repetida — repeatTableRow(...)/repeatDocPart(...) nunca
     *  llevan ${...} alrededor, ni en el comentario (así los reconoce el procesador de
     *  comentarios) ni mucho menos en el cuerpo del documento. Un admin que nunca ha visto la
     *  sintaxis de Word suele escribir esto literal en un párrafo pensando que es un marcador
     *  más — así no hace nada, sale tal cual en el documento generado.
     *  repeatTableRow repite UNA fila de tabla tal cual; repeatDocPart repite un bloque de
     *  varias filas/párrafos seleccionado completo — para cuando el diseño ya armado es un
     *  "cuadro" (celdas combinadas en varias filas) por cada registro, en vez de una fila
     *  sencilla, y no se puede/quiere rehacer esa parte. En ambos casos, dentro del bloque que se
     *  repite, cada campo del registro sigue usando ${['clave']} (nunca ${campos[...]} — ese es
     *  para un valor único de todo el documento, no por registro). */
    private static final Pattern REPEAT_TABLA = Pattern.compile(
            "repeat(?:TableRow|DocPart)\\(\\s*tablas\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*]\\s*\\)");

    /** Compara los marcadores realmente escritos en el .docx subido contra los campos definidos
     *  en el constructor (la request tal cual se está editando, igual que generarVistaPrevia — así
     *  el admin puede analizar antes de guardar). No valida marcadores sueltos sin prefijo
     *  (ej. "${clave}" dentro de una fila repetida vía repeatTableRow) — desde el texto solo no se
     *  puede distinguir de forma confiable a qué sección pertenecen. */
    public AnalisisPlantillaWordResponse analizarMarcadores(FormularioPlantillaConfig config, GuardarPlantillaRequest req) {
        String ruta = config.getPlantillaWordRuta();
        if (ruta == null || ruta.isBlank()) {
            throw new ApiException("Sube primero un documento Word con marcadores para poder analizarlo.",
                    HttpStatus.BAD_REQUEST);
        }

        Set<String> camposUsados = new HashSet<>();
        Set<String> candidatoUsados = new HashSet<>();
        Set<String> riesgoUsados = new HashSet<>();
        // Separadas a propósito: "tablas['clave']" solo funciona de verdad dentro de un
        // comentario repeatTableRow(...) sobre una fila de tabla — si aparece suelto en el texto
        // del documento, office-stamper lo reemplaza por el toString() de la lista completa
        // (texto corrido tipo "[{...}, {...}]"), no arma ninguna tabla. Separar el origen deja
        // detectar justo ese error.
        Set<String> tablasEnComentarios = new HashSet<>();
        Set<String> tablasEnTexto = new HashSet<>();
        // Mismo error que tablasEnTexto, pero DENTRO de un comentario: el admin escribió
        // ${tablas['clave']} como si fuera un marcador normal, en vez de la función
        // repeatTableRow(tablas['clave']) — un comentario así no es una instrucción que el
        // procesador de comentarios reconozca, es letra muerta. Antes esto se confundía con un
        // repeatTableRow real (ambos vivían en el mismo comentario) y "Analizar" lo daba por
        // bueno; se separa aquí para que sí se marque como tabla mal usada.
        Set<String> tablasComoMarcadorEnComentario = new HashSet<>();
        Set<String> seccionesUsadas = new HashSet<>();

        try (InputStream in = almacenamientoService.cargarComoResource(ruta).getInputStream();
             XWPFDocument doc = new XWPFDocument(in)) {
            List<String> textosCuerpo = new ArrayList<>();
            doc.getParagraphs().forEach(p -> textosCuerpo.add(p.getText()));
            for (XWPFTable tabla : doc.getTables()) {
                for (XWPFTableRow fila : tabla.getRows()) {
                    fila.getTableCells().forEach(celda -> celda.getParagraphs().forEach(p -> textosCuerpo.add(p.getText())));
                }
            }
            // Un cuadro de texto (VML o DrawingML — ambos guardan su contenido dentro de
            // <w:txbxContent>) no aparece en doc.getParagraphs()/getTables(): esa API de alto
            // nivel de POI solo recorre el cuerpo principal del documento, no los dibujos
            // anclados. Sin esto, un marcador que SÍ funciona al generar el documento (office-
            // stamper — a diferencia de POI — sí entra a los cuadros de texto, ver
            // MarcadoresEnCuadroDeTextoTest) salía aquí como "campo sin usar", contradiciendo lo
            // que de verdad pasa al descargar el .docx.
            textosCuerpo.addAll(extraerTextosDeCuadrosDeTexto(doc.getDocument().xmlText()));
            for (String texto : textosCuerpo) {
                extraerMarcadores(texto, camposUsados, candidatoUsados, tablasEnTexto, riesgoUsados);
                extraerRepeatTabla(texto, tablasEnTexto);
            }

            XWPFComment[] comentarios = doc.getComments();
            if (comentarios != null) {
                for (XWPFComment comentario : comentarios) {
                    String texto = comentario.getText();
                    extraerMarcadores(texto, camposUsados, candidatoUsados, tablasComoMarcadorEnComentario, riesgoUsados);
                    extraerRepeatTabla(texto, tablasEnComentarios);
                    extraerBloquesSeccion(texto, seccionesUsadas);
                }
            }
        } catch (Exception e) {
            log.error("No se pudo analizar la plantilla Word ({}): {}", ruta, e.getMessage(), e);
            throw new ApiException("No se pudo leer la plantilla Word para analizarla.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Set<String> tablasUsados = new HashSet<>(tablasEnComentarios);
        tablasUsados.addAll(tablasEnTexto);
        tablasUsados.addAll(tablasComoMarcadorEnComentario);

        Set<String> camposDefinidos = new HashSet<>();
        Set<String> tablasDefinidas = new HashSet<>();
        for (var seccion : req.secciones()) {
            List<GuardarPlantillaRequest.ColumnaRequest> columnas = seccion.columnas() == null ? List.of() : seccion.columnas();
            if ("TABLA".equals(seccion.tipo())) {
                tablasDefinidas.add(seccion.origenFijo() != null ? seccion.origenFijo()
                        : claveDePreview(seccion.clave(), seccion.nombre(), "tabla"));
            } else {
                for (var c : columnas) camposDefinidos.add(claveDePreview(c.clave(), c.nombre(), "campo"));
            }
        }
        Set<String> candidatoDefinidos = Arrays.stream(CampoCandidato.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet());
        // Tablas fijas de la hoja de vida (referencias, educación, experiencia laboral,
        // inactividades) — siempre disponibles, no dependen de lo que arme el constructor, así
        // que no deben marcarse como "desconocidas" aunque no estén en la lista de arriba.
        Set<String> tablasSiempreDisponibles = new HashSet<>(tablasDefinidas);
        tablasSiempreDisponibles.addAll(CandidatoInfoService.CLAVES_TABLAS_FIJAS);
        tablasSiempreDisponibles.addAll(TABLAS_CATALOGO);
        tablasSiempreDisponibles.addAll(TABLAS_SOLICITUD);

        List<String> desconocidos = new ArrayList<>();
        for (String c : camposUsados) if (!camposDefinidos.contains(c)) desconocidos.add("campos['" + c + "']");
        for (String c : tablasUsados) if (!tablasSiempreDisponibles.contains(c)) desconocidos.add("tablas['" + c + "']");
        for (String c : candidatoUsados) if (!candidatoDefinidos.contains(c)) desconocidos.add("candidato['" + c + "']");
        for (String r : riesgoUsados) if (!MARCADORES_RIESGO.contains(r)) desconocidos.add(r);
        Set<String> seccionesDefinidas = new HashSet<>();
        for (var seccion : req.secciones()) {
            seccionesDefinidas.add(seccion.origenFijo() != null ? seccion.origenFijo()
                    : claveDePreview(seccion.clave(), seccion.nombre(), "TABLA".equals(seccion.tipo()) ? "tabla" : "seccion"));
        }
        for (String c : seccionesUsadas) if (!seccionesDefinidas.contains(c)) desconocidos.add("secciones['" + c + "']");

        List<String> sinUsar = new ArrayList<>();
        for (String c : camposDefinidos) if (!camposUsados.contains(c)) sinUsar.add("campos['" + c + "']");
        for (String c : tablasDefinidas) if (!tablasUsados.contains(c)) sinUsar.add("tablas['" + c + "']");

        Set<String> tablasMalUsadasClaves = new HashSet<>(tablasEnTexto);
        tablasMalUsadasClaves.addAll(tablasComoMarcadorEnComentario);
        List<String> tablasMalUsadas = tablasMalUsadasClaves.stream().sorted().map(c -> "tablas['" + c + "']").toList();

        // Ensayo real de generación con datos de ejemplo — la comparación de arriba es solo por
        // nombre de marcador y no detecta un ${['clave']} suelto mal ubicado (fuera de una fila
        // repetida real): eso NO revienta por nombre desconocido, revienta al momento de resolver
        // la expresión contra el documento completo. Sin esto, "Analizar" podía decir que todo
        // estaba bien mientras "Vista previa" fallaba con el mismo archivo y los mismos campos.
        String errorGeneracion = null;
        try {
            intentarGenerarVistaPrevia(config, req);
        } catch (Exception e) {
            log.warn("analizarMarcadores: la plantilla Word ({}) no generaría un documento válido: {}", ruta, e.getMessage());
            errorGeneracion = causaLegible(e);
        }

        return new AnalisisPlantillaWordResponse(desconocidos.stream().sorted().toList(), sinUsar.stream().sorted().toList(),
                tablasMalUsadas, errorGeneracion);
    }

    private static final Pattern PATRON_TXBX_CONTENT = Pattern.compile("<w:txbxContent>([\\s\\S]*?)</w:txbxContent>");
    private static final Pattern PATRON_PARRAFO = Pattern.compile("<w:p[ >][\\s\\S]*?</w:p>");
    private static final Pattern PATRON_TEXTO_RUN = Pattern.compile("<w:t[^>]*>(.*?)</w:t>");

    /** VML (w:pict > v:shape > v:textbox) y DrawingML (w:drawing > wps:wsp > wps:txbx) guardan el
     *  contenido de un cuadro de texto de la misma forma por dentro: un <w:txbxContent> con
     *  párrafos normales adentro — por eso alcanza con buscar ese único tag, sin distinguir el
     *  formato. Se concatenan los <w:t> de un mismo párrafo antes de buscar marcadores, igual que
     *  hace XWPFParagraph.getText() para el resto del documento (un marcador puede quedar partido
     *  en varios <w:t> por el corrector ortográfico de Word). */
    // Sin "private" a propósito — FormularioPlantillaWordServiceTest la prueba directo, sin tener
    // que montar un FormularioPlantillaConfig + archivo en disco solo para esto.
    List<String> extraerTextosDeCuadrosDeTexto(String xmlCrudo) {
        List<String> textos = new ArrayList<>();
        Matcher cuadros = PATRON_TXBX_CONTENT.matcher(xmlCrudo);
        while (cuadros.find()) {
            Matcher parrafos = PATRON_PARRAFO.matcher(cuadros.group(1));
            while (parrafos.find()) {
                StringBuilder texto = new StringBuilder();
                Matcher runs = PATRON_TEXTO_RUN.matcher(parrafos.group());
                while (runs.find()) texto.append(runs.group(1));
                if (!texto.isEmpty()) textos.add(texto.toString());
            }
        }
        return textos;
    }

    /** Marcadores de riesgo válidos (docs/modelo-calificacion-riesgo.md §7) — cualquier otra
     *  clave de riesgo/calificacion se reporta como desconocida. */
    static final Set<String> MARCADORES_RIESGO = Set.of(
            "riesgo['nivel']", "riesgo['puntaje']", "calificacion['nivel']", "calificacion['puntaje']");

    private void extraerMarcadores(String texto, Set<String> campos, Set<String> candidato, Set<String> tablas, Set<String> riesgo) {
        if (texto == null || texto.isBlank()) return;
        Matcher m = MARCADOR.matcher(texto);
        while (m.find()) {
            String mapa = m.group(1);
            String clave = m.group(2);
            switch (mapa) {
                case "campos" -> campos.add(clave);
                case "candidato" -> candidato.add(clave);
                case "tablas" -> tablas.add(clave);
                case "riesgo", "calificacion" -> riesgo.add(mapa + "['" + clave + "']");
                default -> { }
            }
        }
    }

    /** Bloque condicional de una sección opcional (V212): repeatDocPart(secciones['clave']). */
    private static final Pattern BLOQUE_SECCION = Pattern.compile(
            "repeatDocPart\\(\\s*secciones\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*]\\s*\\)");

    private void extraerBloquesSeccion(String texto, Set<String> claves) {
        if (texto == null || texto.isBlank()) return;
        Matcher m = BLOQUE_SECCION.matcher(texto);
        while (m.find()) claves.add(m.group(1));
    }

    private void extraerRepeatTabla(String texto, Set<String> tablas) {
        if (texto == null || texto.isBlank()) return;
        Matcher m = REPEAT_TABLA.matcher(texto);
        while (m.find()) tablas.add(m.group(1));
    }

    /** Vista previa rápida: no necesita ser única ni estable como en el guardado real (guardar()
     *  en FormularioPlantillaService), solo sirve para que office-stamper tenga ALGO que resolver
     *  con el mismo nombre que el marcador que el admin ya escribió en el .docx. */
    private String claveDePreview(String claveExplicita, String nombre, String fallback) {
        if (claveExplicita != null && !claveExplicita.isBlank()) return claveExplicita;
        if (nombre != null && !nombre.isBlank()) {
            String sinTildes = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            String limpio = sinTildes.toLowerCase().trim().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (!limpio.isBlank()) return limpio;
        }
        return fallback;
    }
}
