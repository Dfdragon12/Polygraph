package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.AnalisisPlantillaWordResponse;
import com.polygraph.erp.modules.formularios.dto.ArchivoDescarga;
import com.polygraph.erp.modules.formularios.dto.CampoArchivadoResponse;
import com.polygraph.erp.modules.formularios.dto.CampoCandidatoOpcion;
import com.polygraph.erp.modules.formularios.dto.ColumnaResponse;
import com.polygraph.erp.modules.formularios.dto.FilaSugeridaResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.dto.ImpactoPlantillaResponse;
import com.polygraph.erp.modules.formularios.dto.PlantillaConfigResponse;
import com.polygraph.erp.modules.formularios.dto.PlantillaVersionResponse;
import com.polygraph.erp.modules.formularios.dto.SeccionPlantillaResponse;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioColumnaPuntaje;
import com.polygraph.erp.modules.formularios.entity.FormularioFilaSugerida;
import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaConfig;
import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaWordHistorial;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioColumnaRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioFilaRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioPlantillaConfigRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioPlantillaWordHistorialRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.shared.enums.CampoCandidato;
import com.polygraph.erp.shared.enums.CampoCliente;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Plantilla del documento — el administrador arma libremente las secciones, columnas y filas
 *  sugeridas de CUALQUIER tipo de subproceso (tipos_progreso), no solo Antecedentes.
 *
 *  Al guardar, una sección o columna que ya tiene respuestas guardadas (formulario_datos) y deja
 *  de venir en el request NO se borra — se ARCHIVA (activo = false). Deja de aparecer para
 *  formularios nuevos, pero las respuestas ya guardadas se conservan intactas (se siguen viendo
 *  donde ya se habían diligenciado, y en el documento generado). Solo se borra de verdad una
 *  sección/columna que nunca tuvo datos. */
@Service
@RequiredArgsConstructor
@Transactional
public class FormularioPlantillaService {

    private final FormularioSeccionRepository seccionRepository;
    private final FormularioColumnaRepository columnaRepository;
    private final FormularioDatoRepository datoRepository;
    private final FormularioFilaRepository filaRepository;
    private final TipoProgresoRepository tipoProgresoRepository;
    private final FormularioPlantillaConfigRepository configRepository;
    private final FormularioDocumentoService documentoService;
    private final FormularioPlantillaWordService plantillaWordService;
    private final AlmacenamientoDocumentosService almacenamientoService;
    private final FormularioPlantillaWordHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final CandidatoInfoService candidatoInfoService;
    private final com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository codigoRepository;

    private static final String CATEGORIA_ARCHIVOS = "plantillas";

    @Transactional(readOnly = true)
    /** incluirPuntajes: solo el administrador ve los puntos de riesgo de cada respuesta — esta
     *  misma estructura la leen analista/poligrafista/visitador para saber qué mostrar. */
    public List<SeccionPlantillaResponse> listarEstructura(Integer idTipoProgreso, boolean incluirPuntajes) {
        return seccionRepository.findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(idTipoProgreso)
                .stream().map(s -> toResponse(s, incluirPuntajes)).toList();
    }

    /** Genera un .docx de ejemplo (datos ficticios) a partir de la estructura que se está
     *  editando ahora mismo — sin necesidad de haberla guardado todavía. */
    @Transactional(readOnly = true)
    public byte[] vistaPrevia(Integer idTipoProgreso, GuardarPlantillaRequest req) {
        tipoProgresoRepository.findById(idTipoProgreso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado.", HttpStatus.NOT_FOUND));
        return documentoService.generarVistaPrevia(idTipoProgreso, req);
    }

    /** Compara los marcadores del .docx subido contra las columnas/
     *  secciones definidas en el constructor que se está editando ahora mismo (aunque no se haya
     *  guardado todavía) — qué marcadores del documento no corresponden a ningún campo actual, y
     *  qué campos actuales no se están usando en ningún marcador del documento. */
    @Transactional(readOnly = true)
    public AnalisisPlantillaWordResponse analizarPlantillaWord(Integer idTipoProgreso, GuardarPlantillaRequest req) {
        FormularioPlantillaConfig config = configRepository.findById(idTipoProgreso)
                .orElseThrow(() -> new ApiException("Esta plantilla no tiene un documento Word subido todavía.", HttpStatus.BAD_REQUEST));
        return plantillaWordService.analizarMarcadores(config, req);
    }

    /** Qué secciones/columnas con datos guardados se archivarían si se confirma este guardado —
     *  para que el admin vea una alerta ANTES de guardar de verdad. */
    @Transactional(readOnly = true)
    public ImpactoPlantillaResponse verificarImpacto(Integer idTipoProgreso, GuardarPlantillaRequest req) {
        List<FormularioSeccion> existentes = seccionRepository
                .findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(idTipoProgreso);
        Set<Integer> idsSeccionesEnRequest = req.secciones().stream()
                .map(GuardarPlantillaRequest.SeccionRequest::idSeccion).filter(Objects::nonNull).collect(Collectors.toSet());

        List<String> secciones = new ArrayList<>();
        List<String> columnas = new ArrayList<>();

        for (FormularioSeccion existente : existentes) {
            if (!idsSeccionesEnRequest.contains(existente.getIdSeccion())) {
                if (seccionTieneDatos(existente)) secciones.add(existente.getNombre());
                continue;
            }
            var seccionReq = req.secciones().stream()
                    .filter(s -> existente.getIdSeccion().equals(s.idSeccion())).findFirst().orElse(null);
            if (seccionReq == null) continue;
            Set<Integer> idsColumnasEnRequest = seccionReq.columnas() == null ? Set.of()
                    : seccionReq.columnas().stream().map(GuardarPlantillaRequest.ColumnaRequest::idColumna)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
            for (FormularioColumna columna : existente.getColumnas()) {
                if (!Boolean.TRUE.equals(columna.getActivo())) continue;
                if (idsColumnasEnRequest.contains(columna.getIdColumna())) continue;
                if (datoRepository.existsByColumna_IdColumna(columna.getIdColumna())) {
                    String nombreColumna = columna.getNombre() != null && !columna.getNombre().isBlank()
                            ? columna.getNombre() : "(sin título)";
                    columnas.add(existente.getNombre() + " — " + nombreColumna);
                }
            }
        }
        return new ImpactoPlantillaResponse(secciones, columnas);
    }

    public List<SeccionPlantillaResponse> guardar(Integer idTipoProgreso, GuardarPlantillaRequest req) {
        TipoProgreso tipoProgreso = tipoProgresoRepository.findById(idTipoProgreso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado.", HttpStatus.NOT_FOUND));

        // La versión "nueva" que este guardado está a punto de escribir — cualquier sección
        // creada AQUÍ (no las que ya existían) queda estampada con este número, no con el actual,
        // porque a partir de este guardado la plantilla YA es esta versión nueva.
        int nuevaVersion = tipoProgreso.getVersionPlantilla() + 1;

        List<FormularioSeccion> existentes = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(idTipoProgreso);
        Map<Integer, FormularioSeccion> existentesPorId = existentes.stream()
                .collect(Collectors.toMap(FormularioSeccion::getIdSeccion, s -> s));
        Set<Integer> idsEnRequest = req.secciones().stream()
                .map(GuardarPlantillaRequest.SeccionRequest::idSeccion).filter(Objects::nonNull).collect(Collectors.toSet());

        // Claves ya usadas en TODA la plantilla (secciones y columnas por separado) — se
        // recolectan de lo existente para no chocar al generar/validar las nuevas. Se liberan y
        // vuelven a reservar dentro de resolverClave() a medida que se procesa cada elemento.
        Set<String> clavesSecciones = existentes.stream().map(FormularioSeccion::getClave)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<String> clavesColumnas = existentes.stream().flatMap(s -> s.getColumnas().stream())
                .map(FormularioColumna::getClave).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        // Secciones existentes que ya no vienen en el request: archivar (si tienen datos) o
        // borrar de verdad (si nunca tuvieron nada).
        for (FormularioSeccion existente : existentes) {
            if (idsEnRequest.contains(existente.getIdSeccion())) continue;
            if (seccionTieneDatos(existente)) {
                existente.setActivo(false);
                seccionRepository.save(existente);
            } else {
                seccionRepository.delete(existente);
            }
        }

        // Un origen automático (referencias, educación...) no puede repetirse entre secciones —
        // ambas terminarían apuntando a la misma clave de tabla fija.
        Set<String> origenesFijosUsados = new HashSet<>();

        // Calificación por hallazgos: códigos que existen en el catálogo y claves usadas como referencia.
        Set<String> codigosCatalogo = codigoRepository.findAll().stream()
                .map(com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion::getCodigo).collect(Collectors.toSet());
        Map<String, Integer> usosUnicos = new HashMap<>();
        List<String[]> referenciasNota = new ArrayList<>();
        Set<String> clavesPlantilla = new HashSet<>();

        // Un mismo dato automático (candidato o cliente) no debe repetirse en dos columnas de
        // TODA la plantilla — casi siempre es un descuido del admin (arrastró el mismo campo dos
        // veces sin darse cuenta), nunca aporta nada real tenerlo dos veces.
        Set<String> camposAutomaticosUsados = new HashSet<>();

        List<FormularioSeccion> resultado = new ArrayList<>();
        int ordenSeccion = 0;
        for (var s : req.secciones()) {
            FormularioSeccion seccion = s.idSeccion() != null ? existentesPorId.get(s.idSeccion()) : null;
            TipoSeccionFormulario tipo = parseTipoSeccion(s.tipo());
            String origenFijo = s.origenFijo() != null && !s.origenFijo().isBlank() ? s.origenFijo() : null;
            if (origenFijo != null) {
                if (tipo != TipoSeccionFormulario.TABLA) {
                    throw new ApiException("El origen automático solo aplica a secciones tipo Tabla.", HttpStatus.BAD_REQUEST);
                }
                if (!CandidatoInfoService.CLAVES_TABLAS_FIJAS.contains(origenFijo)
                        || CandidatoInfoService.TABLAS_SOLO_LECTURA.contains(origenFijo)) {
                    throw new ApiException("Origen automático inválido: " + origenFijo, HttpStatus.BAD_REQUEST);
                }
                if (!origenesFijosUsados.add(origenFijo)) {
                    throw new ApiException("Ya hay otra sección usando ese mismo origen automático.", HttpStatus.BAD_REQUEST);
                }
                // Si la sección ya existía como manual y el analista ya guardó filas ahí, pasarla
                // a automático la escondería del formulario (ver FormularioService.obtener()) sin
                // borrar esos datos — quedarían huérfanos, invisibles para todos. Se bloquea en
                // vez de perderlos de vista.
                if (seccion != null && seccion.getOrigenFijo() == null && seccionTieneDatos(seccion)) {
                    throw new ApiException("La sección \"" + seccion.getNombre() + "\" ya tiene filas guardadas por el "
                            + "analista — no se puede cambiar a origen automático sin perder de vista esos datos. "
                            + "Crea una sección nueva para el origen automático en su lugar.", HttpStatus.BAD_REQUEST);
                }
            }

            boolean calculada = Boolean.TRUE.equals(s.calculada());
            if (calculada && (tipo != TipoSeccionFormulario.TABLA || origenFijo != null)) {
                throw new ApiException("Solo una tabla manual puede ser calculada (la arma el código, no el analista).",
                        HttpStatus.BAD_REQUEST);
            }

            if (seccion == null) {
                seccion = FormularioSeccion.builder()
                        .tipoProgreso(tipoProgreso)
                        .columnas(new ArrayList<>())
                        .filasSugeridas(new ArrayList<>())
                        .versionCreacion(nuevaVersion)
                        .build();
            }
            seccion.setNombre(s.nombre());
            seccion.setTipo(tipo);
            seccion.setActivo(true);
            seccion.setOrden(ordenSeccion++);
            seccion.setOrigenFijo(origenFijo);
            seccion.setCalculada(calculada);
            seccion.setOpcional(Boolean.TRUE.equals(s.opcional()));
            seccion.setGrupo(s.grupo() == null || s.grupo().isBlank() ? null : s.grupo().trim());
            // El origen automático fija la clave (así el marcador ${tablas['referencias']}
            // siempre coincide, sin depender de lo que el admin escriba) — el manual sigue
            // resolviéndose del nombre/clave a mano, como siempre.
            seccion.setClave(origenFijo != null ? origenFijo : resolverClave(s.clave(), seccion.getClave(), s.nombre(),
                    "seccion_" + (seccion.getOrden() + 1), clavesSecciones));
            seccion.setColumnasOcultas(origenFijo != null && s.columnasOcultas() != null && !s.columnasOcultas().isEmpty()
                    ? String.join(",", s.columnasOcultas()) : null);

            Map<Integer, FormularioColumna> columnasExistentesPorId = seccion.getColumnas().stream()
                    .filter(c -> c.getIdColumna() != null)
                    .collect(Collectors.toMap(FormularioColumna::getIdColumna, c -> c));
            Set<Integer> idsColumnasEnRequest = (s.columnas() == null
                    ? List.<GuardarPlantillaRequest.ColumnaRequest>of() : s.columnas()).stream()
                    .map(GuardarPlantillaRequest.ColumnaRequest::idColumna).filter(Objects::nonNull).collect(Collectors.toSet());

            // Columnas que ya no vienen: archivar (si tienen datos, se conservan en la lista
            // marcadas inactivas) o dejarlas fuera (orphanRemoval las borra al guardar).
            List<FormularioColumna> columnasFinales = new ArrayList<>();
            for (FormularioColumna existenteCol : new ArrayList<>(seccion.getColumnas())) {
                if (existenteCol.getIdColumna() != null && idsColumnasEnRequest.contains(existenteCol.getIdColumna())) continue;
                if (existenteCol.getIdColumna() != null && datoRepository.existsByColumna_IdColumna(existenteCol.getIdColumna())) {
                    existenteCol.setActivo(false);
                    columnasFinales.add(existenteCol);
                }
            }

            // Mismo título visible no debe repetirse DENTRO de esta sección — dos columnas con la
            // misma etiqueta son indistinguibles a simple vista para quien llena el formulario.
            // Una columna sin título (nombre vacío) no cuenta: eso ya es intencional (ver
            // FormularioColumna.nombre) y puede haber varias en la misma sección sin problema.
            Set<String> nombresUsadosEnSeccion = new HashSet<>();

            int ordenColumna = 0;
            if (s.columnas() != null) {
                for (var c : s.columnas()) {
                    FormularioColumna columna = c.idColumna() != null ? columnasExistentesPorId.get(c.idColumna()) : null;
                    if (columna == null) {
                        columna = FormularioColumna.builder().seccion(seccion).build();
                    }
                    columna.setNombre(c.nombre() != null && !c.nombre().isBlank() ? c.nombre().trim() : null);
                    columna.setCampoCandidato(parseCampoCandidato(c.campoCandidato()));
                    columna.setCampoCliente(parseCampoCliente(c.campoCliente()));
                    columna.setCampoServicio(parseCampoServicio(c.campoServicio()));
                    columna.setCalculado(calculada || Boolean.TRUE.equals(c.calculado()));
                    long origenes = java.util.stream.Stream.of(columna.getCampoCandidato(), columna.getCampoCliente(),
                            columna.getCampoServicio()).filter(Objects::nonNull).count()
                            + (Boolean.TRUE.equals(columna.getCalculado()) ? 1 : 0);
                    if (origenes > 1) {
                        throw new ApiException("La columna \"" + (c.nombre() != null ? c.nombre() : c.clave()) + "\" tiene más de un origen: "
                                + "elige uno solo (dato del candidato, del cliente, del estudio o calculado).", HttpStatus.BAD_REQUEST);
                    }

                    if (columna.getNombre() != null && !nombresUsadosEnSeccion.add(columna.getNombre().toLowerCase())) {
                        throw new ApiException("La sección \"" + s.nombre() + "\" tiene dos columnas tituladas \""
                                + columna.getNombre() + "\" — dale un título distinto a cada una.", HttpStatus.BAD_REQUEST);
                    }
                    String campoAutomatico = columna.getCampoCandidato() != null ? "CANDIDATO:" + columna.getCampoCandidato()
                            : columna.getCampoCliente() != null ? "CLIENTE:" + columna.getCampoCliente()
                            : columna.getCampoServicio() != null ? "SERVICIO:" + columna.getCampoServicio() : null;
                    if (campoAutomatico != null && !camposAutomaticosUsados.add(campoAutomatico)) {
                        String etiqueta = columna.getCampoCandidato() != null
                                ? candidatoInfoService.etiquetaDe(columna.getCampoCandidato())
                                : columna.getCampoCliente() != null
                                ? candidatoInfoService.etiquetaDeCliente(columna.getCampoCliente())
                                : candidatoInfoService.etiquetaDeServicio(columna.getCampoServicio());
                        throw new ApiException("El dato \"" + etiqueta + "\" ya está usado en otra columna de esta plantilla — "
                                + "no tiene sentido repetirlo.", HttpStatus.BAD_REQUEST);
                    }

                    // Una columna de candidato/cliente siempre se resuelve como texto — su
                    // tipoDato no importa para la edición (nunca se edita a mano), solo para no
                    // dejar la columna NOT NULL sin valor.
                    columna.setTipoDato(columna.getCampoCandidato() != null || columna.getCampoCliente() != null
                            || columna.getCampoServicio() != null
                            ? TipoDatoFormulario.TEXTO : parseTipoDato(c.tipoDato()));
                    columna.setOrden(ordenColumna++);
                    columna.setActivo(true);
                    columna.setClave(resolverClave(c.clave(), columna.getClave(), c.nombre(),
                            "campo_" + (columna.getOrden() + 1), clavesColumnas));
                    columna.setOpciones(resolverOpciones(columna.getTipoDato(), c.opciones(), c.nombre()));
                    // Valor por defecto y obligatorio solo tienen sentido en lo que llena una persona.
                    boolean manual = !columna.esAutomatica();
                    String defecto = c.valorDefecto() != null && !c.valorDefecto().isBlank() ? c.valorDefecto().trim() : null;
                    if (manual && defecto != null && columna.getTipoDato() == TipoDatoFormulario.LISTA
                            && !OpcionesLista.leer(columna.getOpciones()).contains(defecto)) {
                        throw new ApiException("El valor por defecto de \"" + (c.nombre() != null ? c.nombre() : c.clave())
                                + "\" debe ser una de sus opciones.", HttpStatus.BAD_REQUEST);
                    }
                    columna.setValorDefecto(manual ? defecto : null);
                    columna.setObligatorio(manual && Boolean.TRUE.equals(c.obligatorio()));
                    if (c.longitudMaxima() != null && (c.longitudMaxima() < 1 || c.longitudMaxima() > 10000)) {
                        throw new ApiException("El máximo de caracteres de \"" + (c.nombre() != null ? c.nombre() : c.clave())
                                + "\" debe estar entre 1 y 10.000.", HttpStatus.BAD_REQUEST);
                    }
                    columna.setLongitudMaxima(manual && columna.getTipoDato() == TipoDatoFormulario.TEXTO ? c.longitudMaxima() : null);
                    sincronizarPuntajes(columna, c, s.nombre());
                    sincronizarHallazgos(columna, c.calificacion(), s.nombre(), tipo == TipoSeccionFormulario.TABLA, calculada,
                            codigosCatalogo, usosUnicos, referenciasNota);
                    clavesPlantilla.add(columna.getClave());
                    columnasFinales.add(columna);
                }
            }
            // No reemplazar la colección con setColumnas(...): al tener orphanRemoval=true,
            // Hibernate exige mutar la MISMA instancia de colección administrada — asignar una
            // ArrayList nueva la deja "sin referenciar" y explota en el próximo flush con
            // "A collection with cascade=all-delete-orphan was no longer referenced".
            List<FormularioColumna> columnasManejadas = seccion.getColumnas();
            columnasManejadas.clear();
            columnasManejadas.addAll(columnasFinales);

            // Filas sugeridas: nunca guardan datos reales del analista (esos viven en
            // formulario_filas, aparte) — se pueden reemplazar completas sin riesgo, pero por la
            // misma razón de arriba se muta la colección en vez de reasignarla.
            List<FormularioFilaSugerida> filasSugeridasFinales = new ArrayList<>();
            if (tipo == TipoSeccionFormulario.TABLA && s.filasSugeridas() != null) {
                int ordenFila = 0;
                for (var f : s.filasSugeridas()) {
                    if (f.etiqueta() == null || f.etiqueta().isBlank()) continue;
                    filasSugeridasFinales.add(FormularioFilaSugerida.builder()
                            .seccion(seccion).etiqueta(f.etiqueta()).orden(ordenFila++).build());
                }
            }
            List<FormularioFilaSugerida> filasManejadas = seccion.getFilasSugeridas();
            filasManejadas.clear();
            filasManejadas.addAll(filasSugeridasFinales);

            clavesPlantilla.add(seccion.getClave());
            resultado.add(seccion);
        }

        for (String[] ref : referenciasNota) {
            if (!clavesPlantilla.contains(ref[1])) {
                throw new ApiException("La nota \"" + ref[0] + "\" mira \"" + ref[1] + "\", que no es un campo ni una tabla de esta plantilla.",
                        HttpStatus.BAD_REQUEST);
            }
        }
        // El servicio califica por hallazgos si algún campo los aplica (lo usan el estudio y el formulario).
        boolean califica = resultado.stream().flatMap(x -> x.getColumnas().stream())
                .anyMatch(c -> Boolean.TRUE.equals(c.getActivo())
                        && ("HALLAZGO".equals(c.getUsoCalificacion()) || !c.getHallazgos().isEmpty()));
        tipoProgreso.setGrupoCalificacion(califica ? com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService.GRUPO_HALLAZGOS : null);

        // Sube el contador de versión de la plantilla — cada ServicioSubproceso guarda en su
        // propio versionPlantilla el valor vigente al crearse (ver SolicitudService.
        // generarSubprocesos), así que esto es lo que permite comparar "con qué versión se creó
        // este subproceso" contra "cuál es la versión actual de la plantilla".
        tipoProgreso.setVersionPlantilla(nuevaVersion);
        tipoProgresoRepository.save(tipoProgreso);

        return seccionRepository.saveAll(resultado).stream().map(s -> toResponse(s, true)).toList();
    }

    /** Secciones/columnas archivadas (activo = false) de este subproceso — para que el
     *  administrador las vea en "Campos antiguos" y decida si las restaura. Una sección
     *  archivada completa no lista sus columnas por separado (se restaura entera). */
    @Transactional(readOnly = true)
    public List<CampoArchivadoResponse> listarArchivados(Integer idTipoProgreso) {
        List<FormularioSeccion> todas = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(idTipoProgreso);
        List<CampoArchivadoResponse> resultado = new ArrayList<>();
        for (FormularioSeccion seccion : todas) {
            if (!Boolean.TRUE.equals(seccion.getActivo())) {
                resultado.add(new CampoArchivadoResponse("SECCION", seccion.getIdSeccion(), seccion.getNombre(),
                        null, null, null));
                continue;
            }
            for (FormularioColumna columna : seccion.getColumnas()) {
                if (!Boolean.TRUE.equals(columna.getActivo())) {
                    String nombreColumna = columna.getNombre() != null && !columna.getNombre().isBlank()
                            ? columna.getNombre() : "(sin título)";
                    resultado.add(new CampoArchivadoResponse("COLUMNA", seccion.getIdSeccion(), seccion.getNombre(),
                            columna.getIdColumna(), nombreColumna, columna.getTipoDato().name()));
                }
            }
        }
        return resultado;
    }

    /** Reactiva una sección archivada — vuelve a aparecer en la plantilla y en formularios nuevos
     *  (los ya iniciados antes de que existiera solo la verán si "congelamiento" lo permite, ver
     *  Formulario.fechaPrimerLlenado). */
    public void restaurarSeccion(Integer idTipoProgreso, Integer idSeccion) {
        FormularioSeccion seccion = seccionRepository.findById(idSeccion)
                .filter(s -> s.getTipoProgreso().getIdTipoProgreso().equals(idTipoProgreso))
                .orElseThrow(() -> new ApiException("Sección no encontrada.", HttpStatus.NOT_FOUND));
        seccion.setActivo(true);
        seccionRepository.save(seccion);
    }

    /** Reactiva una columna archivada dentro de una sección activa. */
    public void restaurarColumna(Integer idTipoProgreso, Integer idColumna) {
        FormularioColumna columna = columnaRepository.findById(idColumna)
                .filter(c -> c.getSeccion().getTipoProgreso().getIdTipoProgreso().equals(idTipoProgreso))
                .orElseThrow(() -> new ApiException("Columna no encontrada.", HttpStatus.NOT_FOUND));
        columna.setActivo(true);
        columnaRepository.save(columna);
    }

    /** Si tiene al menos una respuesta guardada en algún formulario (por cualquier columna, o
     *  cualquier fila en una sección tipo tabla), no se puede borrar de verdad. */
    private boolean seccionTieneDatos(FormularioSeccion seccion) {
        boolean algunaColumnaTieneDatos = seccion.getColumnas().stream()
                .anyMatch(c -> datoRepository.existsByColumna_IdColumna(c.getIdColumna()));
        if (algunaColumnaTieneDatos) return true;
        return filaRepository.existsBySeccion_IdSeccion(seccion.getIdSeccion());
    }

    private TipoSeccionFormulario parseTipoSeccion(String valor) {
        try {
            return TipoSeccionFormulario.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Tipo de sección inválido: " + valor, HttpStatus.BAD_REQUEST);
        }
    }

    private TipoDatoFormulario parseTipoDato(String valor) {
        try {
            return TipoDatoFormulario.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Tipo de dato inválido: " + valor, HttpStatus.BAD_REQUEST);
        }
    }

    /** Resuelve la clave de una sección/columna: si el admin mandó una a mano, se usa esa
     *  (validando que no choque con otra); si ya tenía una, se conserva tal cual — no se
     *  regenera solo porque cambió el título, para no romper marcadores de un .docx ya escrito;
     *  si es nueva y sin clave manual, se genera del nombre (o del "fallback" si no hay
     *  nombre). Siempre única dentro de la plantilla. */
    // formulario_secciones.clave y formulario_columnas.clave son VARCHAR(50) en la base de
    // datos — sin este límite, un nombre largo (común al escribir en español, con tildes que se
    // vuelven "_") generaba una clave más larga que la columna, y Postgres rechazaba el guardado
    // con un DataIntegrityViolationException que además el usuario veía con un mensaje genérico
    // sin ninguna pista de la causa real (ver GlobalExceptionHandler).
    private static final int LONGITUD_MAXIMA_CLAVE = 50;

    private String resolverClave(String claveManual, String claveExistente, String nombreBase,
                                  String fallback, Set<String> usadas) {
        String base;
        if (claveManual != null && !claveManual.isBlank()) {
            base = normalizarClave(claveManual);
        } else if (claveExistente != null && !claveExistente.isBlank()) {
            base = claveExistente;
        } else if (nombreBase != null && !nombreBase.isBlank()) {
            base = normalizarClave(nombreBase);
        } else {
            base = fallback;
        }
        if (base.length() > LONGITUD_MAXIMA_CLAVE) {
            base = base.substring(0, LONGITUD_MAXIMA_CLAVE);
        }
        usadas.remove(claveExistente);
        String candidata = base;
        int sufijo = 2;
        while (usadas.contains(candidata)) {
            String sufijoTexto = "_" + sufijo++;
            String baseParaSufijo = base.length() + sufijoTexto.length() > LONGITUD_MAXIMA_CLAVE
                    ? base.substring(0, LONGITUD_MAXIMA_CLAVE - sufijoTexto.length())
                    : base;
            candidata = baseParaSufijo + sufijoTexto;
        }
        usadas.add(candidata);
        return candidata;
    }

    private String normalizarClave(String texto) {
        String sinTildes = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase().trim().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return limpio.isBlank() ? "campo" : limpio;
    }

    private String parseCampoCandidato(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return CampoCandidato.valueOf(valor).name();
        } catch (IllegalArgumentException e) {
            throw new ApiException("Campo de candidato inválido: " + valor, HttpStatus.BAD_REQUEST);
        }
    }

    private String parseCampoServicio(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return com.polygraph.erp.shared.enums.CampoServicio.valueOf(valor).name();
        } catch (IllegalArgumentException e) {
            throw new ApiException("Dato del estudio inválido: " + valor, HttpStatus.BAD_REQUEST);
        }
    }

    private String parseCampoCliente(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return CampoCliente.valueOf(valor).name();
        } catch (IllegalArgumentException e) {
            throw new ApiException("Campo de cliente inválido: " + valor, HttpStatus.BAD_REQUEST);
        }
    }

    // formulario_columnas.opciones era VARCHAR(1000) (TEXT desde V203) — sin este límite, una lista desplegable con
    // muchas opciones (o algunas opciones largas) generaba un texto unido más largo que la
    // columna, y Postgres rechazaba el guardado con el mismo DataIntegrityViolationException
    // genérico de antes (mismo problema que la clave truncada más arriba, pero acá no se puede
    // recortar en silencio sin perder opciones que el admin sí escribió — se rechaza con un
    // mensaje claro para que acorte algo él mismo).
    private static final int LONGITUD_MAXIMA_OPCIONES = 4000;

    /** LISTA exige al menos una opción no vacía (si no, el analista vería una lista desplegable
     *  sin nada para elegir) — cualquier otro tipo de dato ignora lo que venga en opciones. */
    private String resolverOpciones(TipoDatoFormulario tipoDato, List<String> opciones, String nombreColumna) {
        if (tipoDato != TipoDatoFormulario.LISTA) return null;
        List<String> limpias = opciones == null ? List.of() : opciones.stream()
                .map(String::trim).filter(o -> !o.isBlank()).toList();
        String etiquetaColumna = nombreColumna == null || nombreColumna.isBlank() ? "(sin título)" : nombreColumna;
        if (limpias.isEmpty()) {
            throw new ApiException("La columna \"" + etiquetaColumna
                    + "\" es de tipo lista desplegable pero no tiene opciones.", HttpStatus.BAD_REQUEST);
        }
        String unidas = OpcionesLista.unir(limpias);
        if (unidas.length() > LONGITUD_MAXIMA_OPCIONES) {
            throw new ApiException("Las opciones de la columna \"" + etiquetaColumna + "\" suman demasiado texto entre todas "
                    + "(máximo " + LONGITUD_MAXIMA_OPCIONES + " caracteres) — acorta el texto de alguna opción o quita alguna.",
                    HttpStatus.BAD_REQUEST);
        }
        return unidas;
    }

    /** Puntos de riesgo por respuesta (docs/modelo-calificacion-riesgo.md §3). Solo campos Lista
     *  o Sí/No escritos a mano pueden calificar. Se guardan únicamente las respuestas que existen
     *  hoy en el campo: si el admin renombró o quitó una opción, sus puntos se descartan (el
     *  constructor ya le avisa antes de guardar). Muta la MISMA colección (orphanRemoval). */
    private void sincronizarPuntajes(FormularioColumna columna, GuardarPlantillaRequest.ColumnaRequest c, String nombreSeccion) {
        boolean pide = Boolean.TRUE.equals(c.calificaRiesgo());
        boolean automatica = columna.esAutomatica();
        boolean tipoCalificable = columna.getTipoDato() == TipoDatoFormulario.LISTA || columna.getTipoDato() == TipoDatoFormulario.BOOLEANO;
        String etiqueta = columna.getNombre() != null ? columna.getNombre() : columna.getClave();
        if (pide && (automatica || !tipoCalificable)) {
            throw new ApiException("\"" + etiqueta + "\" (sección \"" + nombreSeccion + "\") no puede calificar riesgo: "
                    + "solo los campos Lista o Sí/No que llena quien diligencia.", HttpStatus.BAD_REQUEST);
        }
        columna.setCalificaRiesgo(pide);
        List<FormularioColumnaPuntaje> manejados = columna.getPuntajes();
        if (!pide) {
            manejados.clear();
            return;
        }
        List<String> validas = columna.getTipoDato() == TipoDatoFormulario.BOOLEANO
                ? List.of("true", "false") : OpcionesLista.leer(columna.getOpciones());
        Map<String, GuardarPlantillaRequest.PuntajeOpcionRequest> pedidos = new LinkedHashMap<>();
        if (c.puntajes() != null) {
            for (var pr : c.puntajes()) {
                if (pr.opcion() != null && validas.contains(pr.opcion().trim())) pedidos.put(pr.opcion().trim(), pr);
            }
        }
        Map<String, FormularioColumnaPuntaje> existentes = manejados.stream()
                .collect(Collectors.toMap(FormularioColumnaPuntaje::getOpcion, p -> p, (a, b) -> a));
        manejados.removeIf(p -> !pedidos.containsKey(p.getOpcion()));
        for (var e : pedidos.entrySet()) {
            BigDecimal puntos = e.getValue().puntos() != null ? e.getValue().puntos().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            boolean critico = Boolean.TRUE.equals(e.getValue().esCritico());
            FormularioColumnaPuntaje p = existentes.get(e.getKey());
            if (p == null) {
                manejados.add(FormularioColumnaPuntaje.builder().columna(columna).opcion(e.getKey()).puntos(puntos).esCritico(critico).build());
            } else {
                p.setPuntos(puntos);
                p.setEsCritico(critico);
            }
        }
    }

    private static final Set<String> USOS = Set.of("HALLAZGO", "CRITICIDAD", "CONDICION");
    private static final Set<String> CALCULOS_CAMPO = Set.of("PUNTAJE", "FORMULA", "NIVEL", "SEMAFORO_BAJO", "SEMAFORO_MEDIO",
            "SEMAFORO_ALTO", "NOTA", "MINIMO_FILAS", "PROMEDIO_TABLA");
    private static final Set<String> CALCULOS_FILA = Set.of("NUMERO_FILA", "PROMEDIO_FILA", "CODIGOS_FILA", "TEXTO_SI");
    /** Cálculos que guardan referencia y texto: la nota, el texto condicional y los tres de promedio / mínimo de filas. */
    private static final Set<String> CALCULOS_CON_REFERENCIA = Set.of("NOTA", "TEXTO_SI", "PROMEDIO_FILA", "MINIMO_FILAS", "PROMEDIO_TABLA");
    private static final java.util.regex.Pattern REGLA_MENOR = java.util.regex.Pattern.compile("^\\s*[A-Za-z0-9-]+\\s*<\\s*\\d+([.,]\\d+)?\\s*$");
    private static final Set<String> CALCULOS_HALLAZGOS = Set.of("REF", "DESCRIPCION", "FUENTE", "DETALLE", "PUNTOS");

    /** Calificación por hallazgos de una columna (V205): valida y guarda uso, cálculo, nota,
     *  fuente/detalle y el hallazgo de cada opción. Muta la MISMA colección (orphanRemoval). */
    private void sincronizarHallazgos(FormularioColumna columna, com.polygraph.erp.modules.formularios.dto.CalificacionColumnaDto cfg,
                                      String nombreSeccion, boolean enTabla, boolean seccionCalculada, Set<String> codigosCatalogo,
                                      Map<String, Integer> usosUnicos, List<String[]> referenciasNota) {
        String etiqueta = "\"" + (columna.getNombre() != null ? columna.getNombre() : columna.getClave()) + "\" (sección \"" + nombreSeccion + "\")";
        String uso = cfg == null || cfg.uso() == null || cfg.uso().isBlank() ? null : cfg.uso().trim().toUpperCase();
        String calculo = cfg == null || cfg.calculo() == null || cfg.calculo().isBlank() ? null : cfg.calculo().trim().toUpperCase();
        boolean manual = !columna.esAutomatica();
        boolean lista = columna.getTipoDato() == TipoDatoFormulario.LISTA;

        if (uso != null) {
            if (!USOS.contains(uso)) throw new ApiException("Uso de calificación no válido en " + etiqueta + ".", HttpStatus.BAD_REQUEST);
            if (!manual || !lista) {
                throw new ApiException(etiqueta + ": solo una lista que llena quien diligencia puede ser hallazgo, criticidad o condición.",
                        HttpStatus.BAD_REQUEST);
            }
            List<String> opciones = OpcionesLista.leer(columna.getOpciones());
            switch (uso) {
                case "HALLAZGO" -> {
                    List<String> desconocidos = opciones.stream().filter(o -> !"—".equals(o) && !codigosCatalogo.contains(o)).toList();
                    if (!desconocidos.isEmpty()) {
                        throw new ApiException(etiqueta + ": " + String.join(", ", desconocidos) + " no está en el catálogo de hallazgos.",
                                HttpStatus.BAD_REQUEST);
                    }
                }
                case "CRITICIDAD" -> {
                    if (enTabla) throw new ApiException(etiqueta + ": la criticidad del cargo va en una sección de campos, no en una tabla.", HttpStatus.BAD_REQUEST);
                    if (opciones.stream().anyMatch(o -> !"—".equals(o) && com.polygraph.erp.modules.riesgo.hallazgos.MotorHallazgos.criticidad(o) == null)) {
                        throw new ApiException(etiqueta + ": las opciones de la criticidad deben ser Estándar, Sensible y Crítico.", HttpStatus.BAD_REQUEST);
                    }
                }
                case "CONDICION" -> {
                    if (enTabla) throw new ApiException(etiqueta + ": la condición va en una sección de campos, no en una tabla.", HttpStatus.BAD_REQUEST);
                    if (!opciones.contains("Sí")) throw new ApiException(etiqueta + ": la condición necesita la opción \"Sí\".", HttpStatus.BAD_REQUEST);
                }
                default -> { }
            }
            if (!"HALLAZGO".equals(uso) && usosUnicos.merge(uso, 1, Integer::sum) > 1) {
                throw new ApiException("Solo puede haber un campo de " + ("CRITICIDAD".equals(uso) ? "criticidad del cargo" : "condición")
                        + " en la plantilla.", HttpStatus.BAD_REQUEST);
            }
        }

        if (calculo != null) {
            boolean valido = seccionCalculada ? CALCULOS_HALLAZGOS.contains(calculo)
                    : enTabla ? CALCULOS_FILA.contains(calculo) : CALCULOS_CAMPO.contains(calculo);
            if (!Boolean.TRUE.equals(columna.getCalculado()) || !valido) {
                throw new ApiException(etiqueta + ": ese resultado no aplica aquí. "
                        + (seccionCalculada ? "En la tabla de hallazgos: Ref., Descripción, Fuente, Detalle o Puntos."
                        : enTabla ? "En una tabla: número de fila, códigos de la fila o texto según otro campo de la fila." : "En campos: puntaje, fórmula, nivel, semáforo o nota."),
                        HttpStatus.BAD_REQUEST);
            }
            if ("TEXTO_SI".equals(calculo)) {
                if (cfg.referencia() == null || !cfg.referencia().contains("=") || cfg.textoNota() == null || cfg.textoNota().isBlank()) {
                    throw new ApiException(etiqueta + ": el texto condicional necesita \"campo=Valor\" y el texto que sale.", HttpStatus.BAD_REQUEST);
                }
            }
            if ("PROMEDIO_FILA".equals(calculo)) {
                if (cfg.referencia() == null || cfg.referencia().isBlank()) {
                    throw new ApiException(etiqueta + ": el promedio de la fila necesita los campos que promedia (separados por coma).", HttpStatus.BAD_REQUEST);
                }
                if (cfg.textoNota() != null && !cfg.textoNota().isBlank() && !REGLA_MENOR.matcher(cfg.textoNota()).matches()) {
                    throw new ApiException(etiqueta + ": la regla del promedio se escribe \"CÓDIGO<número\" (ej. PER-03<3).", HttpStatus.BAD_REQUEST);
                }
            }
            if ("MINIMO_FILAS".equals(calculo)) {
                if (cfg.referencia() == null || cfg.referencia().isBlank() || cfg.textoNota() == null || !REGLA_MENOR.matcher(cfg.textoNota()).matches()) {
                    throw new ApiException(etiqueta + ": el mínimo de filas necesita la tabla que cuenta y la regla \"CÓDIGO<número\" (ej. PER-05<2).", HttpStatus.BAD_REQUEST);
                }
                referenciasNota.add(new String[]{columna.getNombre() != null ? columna.getNombre() : columna.getClave(), cfg.referencia().trim()});
            }
            if ("PROMEDIO_TABLA".equals(calculo)) {
                if (cfg.referencia() == null || cfg.referencia().isBlank()) {
                    throw new ApiException(etiqueta + ": el promedio de la tabla necesita la tabla que promedia.", HttpStatus.BAD_REQUEST);
                }
                referenciasNota.add(new String[]{columna.getNombre() != null ? columna.getNombre() : columna.getClave(), cfg.referencia().trim()});
            }
            if ("NOTA".equals(calculo)) {
                if (cfg.referencia() == null || cfg.referencia().isBlank() || cfg.textoNota() == null || cfg.textoNota().isBlank()) {
                    throw new ApiException(etiqueta + ": la nota necesita el campo o tabla que mira y su texto.", HttpStatus.BAD_REQUEST);
                }
                referenciasNota.add(new String[]{columna.getNombre() != null ? columna.getNombre() : columna.getClave(), cfg.referencia().trim()});
            }
        }

        // Hallazgo que genera cada opción (solo listas normales que llena una persona).
        Map<String, String> porOpcion = new LinkedHashMap<>();
        if (cfg != null && cfg.porOpcion() != null && manual && lista && uso == null) {
            List<String> validas = OpcionesLista.leer(columna.getOpciones());
            for (var h : cfg.porOpcion()) {
                if (h.opcion() == null || h.codigo() == null || h.codigo().isBlank() || !validas.contains(h.opcion().trim())) continue;
                if (!codigosCatalogo.contains(h.codigo().trim())) {
                    throw new ApiException(etiqueta + ": " + h.codigo() + " no está en el catálogo de hallazgos.", HttpStatus.BAD_REQUEST);
                }
                porOpcion.put(h.opcion().trim(), h.codigo().trim());
            }
        }
        boolean generaPorRegla = ("PROMEDIO_FILA".equals(calculo) || "MINIMO_FILAS".equals(calculo))
                && cfg != null && cfg.textoNota() != null && !cfg.textoNota().isBlank();
        boolean daHallazgo = "HALLAZGO".equals(uso) || !porOpcion.isEmpty() || generaPorRegla;

        columna.setUsoCalificacion(uso);
        columna.setCalculo(calculo);
        boolean conTexto = calculo != null && CALCULOS_CON_REFERENCIA.contains(calculo);
        columna.setReferencia(conTexto ? cfg.referencia().trim()
                : "FORMULA".equals(calculo) && cfg != null && "CON_CODIGO".equals(cfg.referencia()) ? "CON_CODIGO" : null);
        columna.setTextoNota(conTexto && cfg.textoNota() != null && !cfg.textoNota().isBlank() ? cfg.textoNota().trim() : null);
        columna.setFuenteHallazgo(daHallazgo && cfg.fuente() != null && !cfg.fuente().isBlank() ? cfg.fuente().trim() : null);
        columna.setDetalleHallazgo(daHallazgo && cfg.detalle() != null && !cfg.detalle().isBlank() ? cfg.detalle().trim() : null);
        List<com.polygraph.erp.modules.formularios.entity.FormularioColumnaHallazgo> manejados = columna.getHallazgos();
        manejados.removeIf(h -> !porOpcion.containsKey(h.getOpcion()));
        for (var e : porOpcion.entrySet()) {
            var existente = manejados.stream().filter(h -> h.getOpcion().equals(e.getKey())).findFirst().orElse(null);
            if (existente != null) existente.setCodigo(e.getValue());
            else manejados.add(com.polygraph.erp.modules.formularios.entity.FormularioColumnaHallazgo.builder()
                    .columna(columna).opcion(e.getKey()).codigo(e.getValue()).build());
        }
    }

    private SeccionPlantillaResponse toResponse(FormularioSeccion s, boolean incluirPuntajes) {
        List<ColumnaResponse> columnas = s.getColumnas().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .sorted((a, b) -> a.getOrden().compareTo(b.getOrden()))
                .map(c -> ColumnaResponse.de(c, OpcionesLista.leer(c.getOpciones()), incluirPuntajes))
                .toList();
        List<FilaSugeridaResponse> filasSugeridas = s.getFilasSugeridas().stream()
                .sorted((a, b) -> a.getOrden().compareTo(b.getOrden()))
                .map(f -> new FilaSugeridaResponse(f.getIdFilaSugerida(), f.getEtiqueta(), f.getOrden()))
                .toList();
        List<String> columnasOcultas = s.getColumnasOcultas() == null || s.getColumnasOcultas().isBlank()
                ? List.of() : Arrays.asList(s.getColumnasOcultas().split(","));
        return new SeccionPlantillaResponse(s.getIdSeccion(), s.getNombre(), s.getTipo().name(), s.getOrden(),
                s.getClave(), s.getOrigenFijo(), columnasOcultas, columnas, filasSugeridas,
                Boolean.TRUE.equals(s.getCalculada()), Boolean.TRUE.equals(s.getOpcional()), s.getGrupo());
    }

    /** Ruta del .docx con marcadores subido para este subproceso, si hay uno. */
    @Transactional(readOnly = true)
    public PlantillaConfigResponse obtenerConfig(Integer idTipoProgreso) {
        return toConfigResponse(idTipoProgreso, configRepository.findById(idTipoProgreso).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<CampoCandidatoOpcion> listarCamposCandidatoDisponibles() {
        return Arrays.stream(CampoCandidato.values())
                .map(c -> new CampoCandidatoOpcion(c.name(), c.getEtiqueta()))
                .toList();
    }

    /** Mismo mecanismo que listarCamposCandidatoDisponibles(), para los datos del cliente que
     *  solicitó el servicio (NIT, nombre de la empresa, teléfono) — reutiliza el mismo DTO
     *  {clave, etiqueta}, no hay nada específico de candidato en su forma. */
    @Transactional(readOnly = true)
    public List<CampoCandidatoOpcion> listarCamposClienteDisponibles() {
        return Arrays.stream(CampoCliente.values())
                .map(c -> new CampoCandidatoOpcion(c.name(), c.getEtiqueta()))
                .toList();
    }

    /** Datos del estudio (N° de estudio, fecha de emisión, cliente, cargo, profesional) que una
     *  columna puede mostrar sola — ver CampoServicio. */
    public List<CampoCandidatoOpcion> listarCamposServicioDisponibles() {
        return Arrays.stream(com.polygraph.erp.shared.enums.CampoServicio.values())
                .map(c -> new CampoCandidatoOpcion(c.name(), c.getEtiqueta()))
                .toList();
    }

    /** Sube el .docx diseñado en Word con marcadores — se valida que sea un .docx real
     *  (abriéndolo con POI) antes de guardarlo, para no dejar guardada una plantilla corrupta que
     *  reviente al generar el documento. Si ya había una plantilla activa, esa versión saliente se
     *  archiva en el historial antes de sobrescribirla — no se pierde. */
    public PlantillaConfigResponse subirPlantillaWord(Integer idTipoProgreso, MultipartFile archivo, String emailUsuario) {
        try (var in = archivo.getInputStream();
             var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(in)) {
            if (doc.getParagraphs().isEmpty() && doc.getTables().isEmpty()) {
                throw new ApiException("El documento está vacío.", HttpStatus.BAD_REQUEST);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("El archivo no es un documento Word (.docx) válido.", HttpStatus.BAD_REQUEST);
        }
        FormularioPlantillaConfig config = obtenerOCrearConfig(idTipoProgreso);
        if (config.getPlantillaWordRuta() != null) {
            historialRepository.save(FormularioPlantillaWordHistorial.builder()
                    .tipoProgreso(config.getTipoProgreso())
                    .ruta(config.getPlantillaWordRuta())
                    .fechaCarga(config.getFechaCarga() != null ? config.getFechaCarga() : LocalDateTime.now())
                    .usuarioCarga(config.getUsuarioCarga())
                    .build());
        }
        Usuario usuario = emailUsuario != null ? usuarioRepository.findByEmail(emailUsuario).orElse(null) : null;
        // Se ignora el nombre con el que se subió el archivo desde el computador — queda guardado
        // (y se descarga después) con el nombre del subproceso + la fecha de carga, para que se
        // identifique de una vez de qué se trata sin tener que abrirlo, tanto en la config activa
        // como en cada versión del historial.
        String nombreDeseado = config.getTipoProgreso().getNombreProgreso() + "_"
                + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        config.setPlantillaWordRuta(almacenamientoService.guardar(archivo, CATEGORIA_ARCHIVOS, String.valueOf(idTipoProgreso), nombreDeseado));
        config.setFechaCarga(LocalDateTime.now());
        config.setUsuarioCarga(usuario);
        return toConfigResponse(idTipoProgreso, configRepository.save(config));
    }

    /** Todas las versiones del .docx de este subproceso — la activa primero, luego las
     *  archivadas de más reciente a más antigua. */
    @Transactional(readOnly = true)
    public List<PlantillaVersionResponse> listarHistorialPlantillaWord(Integer idTipoProgreso) {
        List<PlantillaVersionResponse> resultado = new ArrayList<>();
        configRepository.findById(idTipoProgreso)
                .filter(c -> c.getPlantillaWordRuta() != null)
                .ifPresent(c -> resultado.add(new PlantillaVersionResponse(null, nombreOriginalDeRuta(c.getPlantillaWordRuta()),
                        c.getFechaCarga(), c.getUsuarioCarga() != null ? c.getUsuarioCarga().getNombre() : null, true)));
        historialRepository.findByTipoProgreso_IdTipoProgresoOrderByFechaCargaDesc(idTipoProgreso).forEach(h ->
                resultado.add(new PlantillaVersionResponse(h.getId(), nombreOriginalDeRuta(h.getRuta()),
                        h.getFechaCarga(), h.getUsuarioCarga() != null ? h.getUsuarioCarga().getNombre() : null, false)));
        return resultado;
    }

    /** Descarga una versión ARCHIVADA (no la activa — para esa está descargarPlantillaWord). */
    @Transactional(readOnly = true)
    public ArchivoDescarga descargarVersionHistorial(Integer idTipoProgreso, Long idHistorial) {
        FormularioPlantillaWordHistorial version = historialRepository.findById(idHistorial)
                .filter(h -> h.getTipoProgreso().getIdTipoProgreso().equals(idTipoProgreso))
                .orElseThrow(() -> new ApiException("Esa versión no existe.", HttpStatus.NOT_FOUND));
        return new ArchivoDescarga(almacenamientoService.cargarComoResource(version.getRuta()),
                nombreOriginalDeRuta(version.getRuta()),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** Borra una versión ARCHIVADA del historial — el registro y el archivo .docx en disco. No
     *  toca la versión activa (para eso está quitarPlantillaWord); cada versión archivada tiene su
     *  propia ruta única (se genera un nombre nuevo en cada subida), así que borrar una no afecta
     *  a ninguna otra ni a la config activa. */
    public void eliminarVersionHistorial(Integer idTipoProgreso, Long idHistorial) {
        FormularioPlantillaWordHistorial version = historialRepository.findById(idHistorial)
                .filter(h -> h.getTipoProgreso().getIdTipoProgreso().equals(idTipoProgreso))
                .orElseThrow(() -> new ApiException("Esa versión no existe.", HttpStatus.NOT_FOUND));
        historialRepository.delete(version);
        almacenamientoService.eliminar(version.getRuta());
    }

    public void quitarPlantillaWord(Integer idTipoProgreso) {
        configRepository.findById(idTipoProgreso).ifPresent(config -> {
            if (config.getPlantillaWordRuta() != null) {
                historialRepository.save(FormularioPlantillaWordHistorial.builder()
                        .tipoProgreso(config.getTipoProgreso())
                        .ruta(config.getPlantillaWordRuta())
                        .fechaCarga(config.getFechaCarga() != null ? config.getFechaCarga() : LocalDateTime.now())
                        .usuarioCarga(config.getUsuarioCarga())
                        .build());
            }
            config.setPlantillaWordRuta(null);
            config.setFechaCarga(null);
            config.setUsuarioCarga(null);
            configRepository.save(config);
        });
    }

    /** El .docx tal cual se subió, con su nombre original (no el UUID interno de almacenamiento)
     *  — para que el admin pueda recuperarlo si perdió su copia local, editarlo y volver a
     *  subirlo. */
    @Transactional(readOnly = true)
    public ArchivoDescarga descargarPlantillaWord(Integer idTipoProgreso) {
        FormularioPlantillaConfig config = configRepository.findById(idTipoProgreso)
                .filter(c -> c.getPlantillaWordRuta() != null)
                .orElseThrow(() -> new ApiException("Esta plantilla no tiene un documento Word subido todavía.", HttpStatus.BAD_REQUEST));
        String ruta = config.getPlantillaWordRuta();
        return new ArchivoDescarga(almacenamientoService.cargarComoResource(ruta), nombreOriginalDeRuta(ruta),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private String nombreOriginalDeRuta(String ruta) {
        String segmento = ruta.substring(ruta.lastIndexOf('/') + 1);
        int guionBajo = segmento.indexOf('_');
        return guionBajo >= 0 ? segmento.substring(guionBajo + 1) : segmento;
    }

    private FormularioPlantillaConfig obtenerOCrearConfig(Integer idTipoProgreso) {
        TipoProgreso tipoProgreso = tipoProgresoRepository.findById(idTipoProgreso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado.", HttpStatus.NOT_FOUND));
        return configRepository.findById(idTipoProgreso)
                .orElseGet(() -> FormularioPlantillaConfig.builder().tipoProgreso(tipoProgreso).build());
    }

    private PlantillaConfigResponse toConfigResponse(Integer idTipoProgreso, FormularioPlantillaConfig config) {
        Integer versionPlantilla = tipoProgresoRepository.findById(idTipoProgreso)
                .map(TipoProgreso::getVersionPlantilla).orElse(null);
        if (config == null) return new PlantillaConfigResponse(idTipoProgreso, null, null, null, versionPlantilla);
        return new PlantillaConfigResponse(idTipoProgreso, config.getPlantillaWordRuta(), config.getFechaCarga(),
                config.getUsuarioCarga() != null ? config.getUsuarioCarga().getNombre() : null, versionPlantilla);
    }
}
