package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.ArchivoDescarga;
import com.polygraph.erp.modules.formularios.dto.CampoCandidatoValorResponse;
import com.polygraph.erp.modules.formularios.dto.ColumnaResponse;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.formularios.dto.ProgresoFormularioResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest;
import com.polygraph.erp.modules.formularios.entity.*;
import com.polygraph.erp.modules.formularios.repository.*;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Llenado del formulario por el analista — un formulario por subproceso, sin importar de qué
 *  tipo sea. Qué secciones/columnas se muestran lo decide la plantilla (FormularioSeccion) según
 *  el tipo de progreso del subproceso — este servicio no conoce Antecedentes ni ningún otro
 *  módulo en particular, solo lee la plantilla configurada y guarda/lee los datos. */
@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FormularioService {

    private final FormularioRepository formularioRepository;
    private final FormularioSeccionRepository seccionRepository;
    private final FormularioFilaRepository filaRepository;
    private final FormularioDatoRepository datoRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;
    private final AlmacenamientoDocumentosService almacenamientoService;
    private final CandidatoInfoService candidatoInfoService;
    private final NotificacionService notificacionService;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    private final FormularioPlantillaConfigRepository plantillaConfigRepository;
    private final com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService calculoSubservicioService;

    private static final String CATEGORIA_ARCHIVOS = "formularios";

    @Transactional(readOnly = true)
    public FormularioResponse obtener(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = obtenerSubproceso(idServicioSubproceso);
        validarAcceso(subproceso, actor);
        Candidato candidato = candidatoDe(subproceso);
        Cliente cliente = clienteDe(subproceso);

        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null);
        List<FormularioSeccion> secciones = seccionRepository
                .findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso());

        Set<Integer> omitidas = formulario != null ? formulario.getSeccionesOmitidas() : Set.of();
        Map<Integer, List<FormularioFila>> filasPorSeccion = new HashMap<>();
        Map<Integer, String> valoresCampos = new HashMap<>(); // idColumna -> valor
        Map<Integer, Map<Integer, String>> valoresPorFila = new HashMap<>(); // idFila -> (idColumna -> valor)
        Map<Integer, String> justificacionesCampos = new HashMap<>(); // idColumna -> justificación
        Map<Integer, Map<Integer, String>> justificacionesPorFila = new HashMap<>(); // idFila -> (idColumna -> justificación)

        if (formulario != null) {
            List<FormularioFila> filas = filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario());
            for (FormularioFila f : filas) {
                filasPorSeccion.computeIfAbsent(f.getSeccion().getIdSeccion(), k -> new ArrayList<>()).add(f);
            }
            for (FormularioDato d : datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario())) {
                if (d.getFila() == null) {
                    valoresCampos.put(d.getColumna().getIdColumna(), d.getValor());
                    if (d.getJustificacion() != null) justificacionesCampos.put(d.getColumna().getIdColumna(), d.getJustificacion());
                } else {
                    valoresPorFila.computeIfAbsent(d.getFila().getIdFila(), k -> new HashMap<>())
                            .put(d.getColumna().getIdColumna(), d.getValor());
                    if (d.getJustificacion() != null) {
                        justificacionesPorFila.computeIfAbsent(d.getFila().getIdFila(), k -> new HashMap<>())
                                .put(d.getColumna().getIdColumna(), d.getJustificacion());
                    }
                }
            }
        }

        // Las columnas automáticas (candidato, cliente, estudio, calculadas) nunca guardan un dato
        // a mano — su valor se resuelve en vivo, aquí mismo, para que se vea siempre actualizado
        // (ver FormularioColumna.esAutomatica). Las calculadas quedan vacías hasta que el
        // subservicio tenga su cálculo propio (docs/antecedentes, Prompt 4).
        for (FormularioSeccion seccion : secciones) {
            for (FormularioColumna columna : seccion.getColumnas()) {
                String valor;
                if (columna.getCampoCandidato() != null) {
                    valor = candidatoInfoService.resolverUno(candidato, columna.getCampoCandidato());
                } else if (columna.getCampoCliente() != null) {
                    valor = candidatoInfoService.resolverUnoCliente(cliente, columna.getCampoCliente());
                } else if (columna.getCampoServicio() != null) {
                    valor = candidatoInfoService.resolverUnoServicio(subproceso, columna.getCampoServicio());
                } else if (criticidadDeSolicitud(subproceso, columna) != null) {
                    valor = criticidadDeSolicitud(subproceso, columna); // NS-07: la eligió el cliente al solicitar
                } else {
                    continue; // manuales y calculadas: las calculadas van abajo, con el cálculo
                }
                valoresCampos.put(columna.getIdColumna(), valor);
                // También en cada fila existente, por si esta columna vive en una sección tipo
                // TABLA — el dato es el mismo en todas las filas.
                for (Map<Integer, String> valoresFila : valoresPorFila.values()) {
                    valoresFila.put(columna.getIdColumna(), valor);
                }
            }
        }

        // Columnas calculadas por el subservicio (docs/antecedentes, Prompt 4): se calculan cada vez
        // que se abre o se guarda el formulario, con lo que está guardado — nunca se guardan.
        ValoresPorClave.Entrada entrada = ValoresPorClave.armar(secciones, valoresCampos, filasPorSeccion, valoresPorFila);
        com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario calculo = calculoSubservicioService
                .calcular(secciones, com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService.criticidadDelEstudio(subproceso),
                        com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService.datosDelEstudio(subproceso),
                        entrada.campos(), entrada.tablas()).orElse(null);
        for (FormularioSeccion seccion : secciones) {
            if (seccion.getTipo() == TipoSeccionFormulario.CAMPOS) {
                for (FormularioColumna c : seccion.getColumnas()) {
                    if (Boolean.TRUE.equals(c.getCalculado())) valoresCampos.put(c.getIdColumna(), ValoresPorClave.campo(calculo, c));
                }
            } else if (!Boolean.TRUE.equals(seccion.getCalculada())) {
                List<FormularioFila> filasSeccion = ValoresPorClave.filasOrdenadas(seccion, filasPorSeccion);
                for (int i = 0; i < filasSeccion.size(); i++) {
                    for (FormularioColumna c : seccion.getColumnas()) {
                        if (!Boolean.TRUE.equals(c.getCalculado())) continue;
                        valoresPorFila.computeIfAbsent(filasSeccion.get(i).getIdFila(), k -> new HashMap<>())
                                .put(c.getIdColumna(), ValoresPorClave.celda(calculo, seccion, i, c));
                    }
                }
            }
        }

        Map<String, List<Map<String, String>>> tablasFijasDatos = candidatoInfoService.resolverTablasFijas(candidato);
        List<FormularioResponse.TablaFijaResponse> tablasFijasResponse = new ArrayList<>();

        // Lo que el admin quitó de la plantilla (archivado) solo lo ve ADMIN_POLYGRAPH, que
        // supervisa el catálogo. Gestor, programador y ejecutores NUNCA lo ven, ni siquiera ya
        // COMPLETADO/PUBLICADO: antes se les volvía a mostrar al congelarse y el gestor veía
        // campos eliminados que el Word tampoco imprime (FormularioDocumentoService solo usa
        // columnas activas) — la pantalla debe mostrar lo mismo que el documento (bug 25-sep-2026).
        boolean ocultarArchivadoParaEjecutor = ocultaArchivados(actor);

        // El formulario SÍ trae los puntos por respuesta (decisión 25-sep-2026): quien diligencia ve
        // cuántos puntos suma cada fila de una tabla y el total — /estructura los sigue ocultando.
        List<FormularioResponse.SeccionDatosResponse> seccionesResponse = new ArrayList<>();
        for (FormularioSeccion seccion : secciones) {
            // Mientras sigue ASIGNADO, el formulario sigue la plantilla VIGENTE (si admin agrega
            // una sección mientras alguien todavía lo está llenando, sí debe verla) — el
            // congelamiento por versión solo aplica una vez COMPLETADO/PUBLICADO (ver
            // AsignacionService.completar(), que estampa versionPlantilla justo en ese momento, no
            // al crearse). Una sección agregada a la plantilla DESPUÉS de completado nunca aparece
            // acá, ni siquiera si por algún motivo este formulario puntual llegó a tener datos
            // guardados en ella.
            if (estaCongelado(subproceso.getEstado())
                    && seccion.getVersionCreacion() > subproceso.getVersionPlantilla()) continue;
            // Origen automático (referencias, educación...): se llena y se edita directo sobre el
            // registro real del candidato (ver CandidatoHojaVidaController) — aquí solo se
            // muestra en el mismo orden del constructor, aparte de las secciones editables (ver
            // tablasFijasResponse), nunca se diligencia como un FormularioFila más.
            if (seccion.getOrigenFijo() != null) {
                Set<String> ocultas = seccion.getColumnasOcultas() == null || seccion.getColumnasOcultas().isBlank()
                        ? Set.of() : Set.of(seccion.getColumnasOcultas().split(","));
                List<String> claves = CandidatoInfoService.COLUMNAS_TABLAS_FIJAS.getOrDefault(seccion.getOrigenFijo(), List.of())
                        .stream().filter(c -> !ocultas.contains(c)).toList();
                List<FormularioResponse.TablaFijaResponse.ColumnaFija> columnasFijas = claves.stream()
                        .map(c -> new FormularioResponse.TablaFijaResponse.ColumnaFija(c,
                                CandidatoInfoService.ETIQUETAS_COLUMNAS_TABLAS_FIJAS.getOrDefault(c, c)))
                        .toList();

                // Columnas de VALIDACIÓN: las que el admin agregó a esta sección (FormularioColumna
                // normales) para que analista/poligrafista/visitador opinen por fila — mismo
                // mecanismo de siempre (FormularioFila/FormularioDato), correlacionado con la fila
                // real del candidato vía FormularioFila.idOrigenFijo, nunca toca su tabla.
                List<ColumnaResponse> columnasValidacion = seccion.getColumnas().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getActivo())
                                || (!ocultarArchivadoParaEjecutor && columnaTieneDatos(c, valoresCampos, valoresPorFila)))
                        .sorted(Comparator.comparing(FormularioColumna::getOrden))
                        .map(c -> ColumnaResponse.de(c, OpcionesLista.leer(c.getOpciones()), true))
                        .toList();

                Map<Long, FormularioFila> filaPorOrigenFijo = filasPorSeccion.getOrDefault(seccion.getIdSeccion(), List.of())
                        .stream().filter(f -> f.getIdOrigenFijo() != null)
                        .collect(Collectors.toMap(FormularioFila::getIdOrigenFijo, f -> f, (a, b) -> a));
                Map<String, FormularioResponse.TablaFijaResponse.FilaValidacionResponse> validacionPorId = new HashMap<>();
                for (Map<String, String> filaFija : tablasFijasDatos.getOrDefault(seccion.getOrigenFijo(), List.of())) {
                    String idStr = filaFija.get("id");
                    FormularioFila filaValidacion = idStr == null ? null : filaPorOrigenFijo.get(Long.valueOf(idStr));
                    validacionPorId.put(idStr, new FormularioResponse.TablaFijaResponse.FilaValidacionResponse(
                            filaValidacion == null ? null : filaValidacion.getIdFila(),
                            filaValidacion == null ? Map.of() : valoresPorFila.getOrDefault(filaValidacion.getIdFila(), Map.of()),
                            filaValidacion == null ? Map.of() : justificacionesPorFila.getOrDefault(filaValidacion.getIdFila(), Map.of())));
                }

                tablasFijasResponse.add(new FormularioResponse.TablaFijaResponse(seccion.getIdSeccion(), seccion.getNombre(),
                        seccion.getOrden(), seccion.getOrigenFijo(), columnasFijas,
                        tablasFijasDatos.getOrDefault(seccion.getOrigenFijo(), List.of()),
                        columnasValidacion, validacionPorId, Boolean.TRUE.equals(seccion.getOpcional()),
                        omitidas.contains(seccion.getIdSeccion()), seccion.getGrupo()));
                continue;
            }
            // Una sección/columna archivada (el admin la quitó de la plantilla después de que ya
            // tenía respuestas) sigue apareciendo AQUÍ solo si este formulario puntual ya tiene
            // algo guardado en ella — así no se pierde de vista una respuesta ya dada, pero
            // tampoco aparece como campo nuevo para llenar en un formulario que apenas empieza.
            List<FormularioFila> filasExistentes = filasPorSeccion.get(seccion.getIdSeccion());
            boolean seccionTieneDatos = seccion.getColumnas().stream().anyMatch(c -> columnaTieneDatos(c, valoresCampos, valoresPorFila))
                    || (filasExistentes != null && !filasExistentes.isEmpty());
            boolean seccionArchivadaVisible = seccionTieneDatos && !ocultarArchivadoParaEjecutor;
            if (!Boolean.TRUE.equals(seccion.getActivo()) && !seccionArchivadaVisible) continue;

            List<ColumnaResponse> columnas = seccion.getColumnas().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getActivo())
                            || (!ocultarArchivadoParaEjecutor && columnaTieneDatos(c, valoresCampos, valoresPorFila)))
                    .sorted(Comparator.comparing(FormularioColumna::getOrden))
                    .map(c -> ColumnaResponse.de(c, OpcionesLista.leer(c.getOpciones()), true))
                    .toList();

            if (seccion.getTipo() == TipoSeccionFormulario.CAMPOS) {
                Map<Integer, String> valores = columnas.stream()
                        .collect(Collectors.toMap(ColumnaResponse::idColumna,
                                c -> valoresCampos.getOrDefault(c.idColumna(), c.valorDefecto() != null ? c.valorDefecto() : "")));
                seccionesResponse.add(new FormularioResponse.SeccionDatosResponse(
                        seccion.getIdSeccion(), seccion.getNombre(), seccion.getTipo().name(), seccion.getOrden(), columnas, valores, List.of(),
                        justificacionesCampos.entrySet().stream().filter(e -> valores.containsKey(e.getKey()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), false,
                        Boolean.TRUE.equals(seccion.getOpcional()), omitidas.contains(seccion.getIdSeccion()), seccion.getGrupo()));
            } else {
                List<FormularioFila> filas = filasPorSeccion.get(seccion.getIdSeccion());
                List<FormularioResponse.FilaDatosResponse> filasResponse;
                if (Boolean.TRUE.equals(seccion.getCalculada())) {
                    // La arma el subservicio (ej. hallazgos): filas sin id, solo lectura.
                    filasResponse = ValoresPorClave.filasCalculadas(calculo, seccion).stream()
                            .map(filaCalculada -> {
                                Map<Integer, String> valores = new HashMap<>();
                                for (ColumnaResponse c : columnas) {
                                    if (c.clave() != null) valores.put(c.idColumna(), filaCalculada.getOrDefault(c.clave(), ""));
                                }
                                return new FormularioResponse.FilaDatosResponse(null, "", valores, Map.of());
                            })
                            .toList();
                } else if (filas != null) {
                    filasResponse = filas.stream()
                            .sorted(Comparator.comparing(FormularioFila::getOrden))
                            .map(f -> new FormularioResponse.FilaDatosResponse(f.getIdFila(), f.getEtiqueta(),
                                    conDefectos(columnas, valoresPorFila.getOrDefault(f.getIdFila(), Map.of())),
                                    justificacionesPorFila.getOrDefault(f.getIdFila(), Map.of())))
                            .toList();
                } else if (CandidatoInfoService.PRECARGAS.containsKey(seccion.getClave())) {
                    // Aún no se ha guardado nada y la tabla nace de la hoja de vida del candidato (ej. las 4 referencias
                    // que registró): una fila por cada una, sin id hasta que el analista guarde.
                    CandidatoInfoService.Precarga precarga = CandidatoInfoService.PRECARGAS.get(seccion.getClave());
                    filasResponse = tablasFijasDatos.getOrDefault(precarga.tablaFija(), List.of()).stream()
                            .map(origen -> {
                                Map<Integer, String> valores = new HashMap<>();
                                for (ColumnaResponse c : columnas) {
                                    String fija = c.clave() == null ? null : precarga.columnas().get(c.clave());
                                    if (fija != null) valores.put(c.idColumna(), origen.getOrDefault(fija, ""));
                                }
                                return new FormularioResponse.FilaDatosResponse(null, "", conDefectos(columnas, valores), Map.of());
                            })
                            .toList();
                } else {
                    // Aún no se ha guardado nada — se muestran las filas sugeridas de la plantilla,
                    // sin id (todavía no existen como FormularioFila reales).
                    filasResponse = seccion.getFilasSugeridas().stream()
                            .sorted(Comparator.comparing(FormularioFilaSugerida::getOrden))
                            .map(f -> new FormularioResponse.FilaDatosResponse(null, f.getEtiqueta(),
                                    conDefectos(columnas, valoresDeFilaSugerida(columnas, f.getEtiqueta())), Map.of()))
                            .toList();
                }
                seccionesResponse.add(new FormularioResponse.SeccionDatosResponse(
                        seccion.getIdSeccion(), seccion.getNombre(), seccion.getTipo().name(), seccion.getOrden(), columnas, Map.of(), filasResponse, Map.of(),
                        Boolean.TRUE.equals(seccion.getCalculada()), Boolean.TRUE.equals(seccion.getOpcional()),
                        omitidas.contains(seccion.getIdSeccion()), seccion.getGrupo()));
            }
        }

        return toResponse(subproceso, candidato, formulario, seccionesResponse, tablasFijasResponse,
                calculo != null ? calculo.aviso() : null, calculo != null ? calculo.avisos() : List.of());
    }

    /** Máximo de caracteres de un campo de texto (V209): la pantalla ya no deja pasar de ahí; esto
     *  cubre a quien llame la API directo. */
    private static void validarLongitud(FormularioColumna columna, String valor, FormularioSeccion seccion) {
        Integer max = columna.longitudMaximaEfectiva();
        if (max != null && valor != null && valor.length() > max) {
            throw new ApiException("\"" + (columna.getNombre() != null ? columna.getNombre() : columna.getClave()) + "\" (sección \""
                    + seccion.getNombre() + "\") tiene " + valor.length() + " caracteres; el máximo es " + max + ".", HttpStatus.BAD_REQUEST);
        }
    }

    /** Una fila sugerida ("Registraduría") ya trae puesto su valor en la primera lista que lo tenga
     *  como opción (la columna Fuente / Lista) — así quien diligencia no lo vuelve a elegir. */
    private static Map<Integer, String> valoresDeFilaSugerida(List<ColumnaResponse> columnas, String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) return Map.of();
        for (ColumnaResponse c : columnas) {
            if (!c.automatico() && "LISTA".equals(c.tipoDato()) && c.opciones() != null && c.opciones().contains(etiqueta.trim())) {
                return Map.of(c.idColumna(), etiqueta.trim());
            }
        }
        return Map.of();
    }

    /** Valores de una fila con el valor por defecto de cada columna que todavía no tiene nada
     *  (ej. "—" en las columnas Ref.) — así lo ve y lo guarda quien diligencia. */
    private static Map<Integer, String> conDefectos(List<ColumnaResponse> columnas, Map<Integer, String> valores) {
        Map<Integer, String> resultado = new HashMap<>(valores);
        for (ColumnaResponse c : columnas) {
            if (c.valorDefecto() != null && !resultado.containsKey(c.idColumna())) resultado.put(c.idColumna(), c.valorDefecto());
        }
        return resultado;
    }

    private Candidato candidatoDe(ServicioSubproceso subproceso) {
        Servicio servicio = subproceso.getServicio();
        return servicio != null ? servicio.getCandidato() : null;
    }

    private Cliente clienteDe(ServicioSubproceso subproceso) {
        Servicio servicio = subproceso.getServicio();
        return servicio != null ? servicio.getCliente() : null;
    }

    /** Misma regla en obtener() y guardar(): solo ADMIN_POLYGRAPH ve lo archivado. */
    private static boolean ocultaArchivados(Usuario actor) {
        return actor.getRol() != Rol.ADMIN_POLYGRAPH;
    }

    private boolean columnaTieneDatos(FormularioColumna columna, Map<Integer, String> valoresCampos,
                                       Map<Integer, Map<Integer, String>> valoresPorFila) {
        // Las columnas automáticas (candidato/cliente/estudio/calculadas) nunca guardan datos: su
        // valor se resuelve en vivo, así que no cuentan como "archivada con datos".
        if (columna.esAutomatica()) return false;
        if (valoresCampos.containsKey(columna.getIdColumna())) return true;
        return valoresPorFila.values().stream().anyMatch(m -> m.containsKey(columna.getIdColumna()));
    }

    /** ¿El valor elegido es una respuesta CRÍTICA de un campo calificable? (docs/modelo-calificacion-riesgo.md §3) */
    static boolean esRespuestaCritica(FormularioColumna columna, String valor) {
        if (valor == null || valor.isBlank() || !Boolean.TRUE.equals(columna.getCalificaRiesgo())) return false;
        return columna.getPuntajes().stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getEsCritico()) && p.getOpcion().equals(valor.trim()));
    }

    /** Solo se guarda la justificación si la respuesta es crítica — si el ejecutor cambió a una
     *  respuesta normal, la explicación vieja se descarta. */
    private static String justificacionSiCritica(FormularioColumna columna, String valor, Map<Integer, String> justificaciones) {
        if (justificaciones == null || !esRespuestaCritica(columna, valor)) return null;
        String j = justificaciones.get(columna.getIdColumna());
        return j == null || j.isBlank() ? null : j.trim();
    }

    /** Respuestas críticas sin justificación en el formulario de un subproceso — si hay alguna,
     *  no se puede marcar como completado (AsignacionService.completar). Devuelve etiquetas
     *  legibles ("Sección › Campo"); vacío si todo está bien o no hay formulario. */
    @Transactional(readOnly = true)
    public List<String> respuestasCriticasSinJustificar(Long idServicioSubproceso) {
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null);
        if (formulario == null) return List.of();
        return datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario()).stream()
                .filter(d -> Boolean.TRUE.equals(d.getColumna().getActivo()))
                .filter(d -> esRespuestaCritica(d.getColumna(), d.getValor()))
                .filter(d -> d.getJustificacion() == null || d.getJustificacion().isBlank())
                .map(d -> {
                    FormularioColumna c = d.getColumna();
                    String campo = c.getNombre() != null ? c.getNombre() : c.getClave();
                    String fila = d.getFila() != null && d.getFila().getEtiqueta() != null && !d.getFila().getEtiqueta().isBlank()
                            ? " (" + d.getFila().getEtiqueta() + ")" : "";
                    return c.getSeccion().getNombre() + " › " + campo + fila;
                })
                .distinct()
                .toList();
    }

    /** Campos obligatorios (FormularioColumna.obligatorio) que siguen vacíos — si hay alguno, no se
     *  puede marcar como completado (AsignacionService.completar). En una tabla, cada fila que
     *  exista debe tenerlo. Devuelve etiquetas legibles ("Sección › Campo"); vacío si todo está bien. */
    @Transactional(readOnly = true)
    public List<String> camposObligatoriosVacios(Long idServicioSubproceso) {
        ServicioSubproceso subproceso = obtenerSubproceso(idServicioSubproceso);
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null);
        List<FormularioDato> datos = formulario == null ? List.of() : datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario());
        List<FormularioFila> filas = formulario == null ? List.of() : filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario());
        List<String> faltantes = new ArrayList<>();
        Set<Integer> omitidas = formulario == null ? Set.of() : formulario.getSeccionesOmitidas();
        for (FormularioSeccion seccion : seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso())) {
            if (!Boolean.TRUE.equals(seccion.getActivo())) continue;
            // Quien diligencia la quitó (solo se puede si es opcional): no se llena, no exige nada.
            if (omitidas.contains(seccion.getIdSeccion())) continue;
            for (FormularioColumna c : seccion.getColumnas()) {
                if (!Boolean.TRUE.equals(c.getActivo()) || !Boolean.TRUE.equals(c.getObligatorio())) continue;
                String etiqueta = seccion.getNombre() + " › " + (c.getNombre() != null ? c.getNombre() : c.getClave());
                if (seccion.getTipo() == TipoSeccionFormulario.CAMPOS) {
                    boolean lleno = datos.stream().anyMatch(d -> d.getFila() == null && d.getColumna().getIdColumna().equals(c.getIdColumna())
                            && d.getValor() != null && !d.getValor().isBlank());
                    if (!lleno) faltantes.add(etiqueta);
                } else {
                    boolean algunaVacia = filas.stream().filter(f -> f.getSeccion().getIdSeccion().equals(seccion.getIdSeccion()))
                            .anyMatch(f -> datos.stream().noneMatch(d -> d.getFila() != null && d.getFila().getIdFila().equals(f.getIdFila())
                                    && d.getColumna().getIdColumna().equals(c.getIdColumna()) && d.getValor() != null && !d.getValor().isBlank()));
                    if (algunaVacia) faltantes.add(etiqueta);
                }
            }
        }
        return faltantes;
    }

    public FormularioResponse guardar(Long idServicioSubproceso, GuardarFormularioRequest req, Usuario actor) {
        ServicioSubproceso subproceso = obtenerSubproceso(idServicioSubproceso);
        validarAcceso(subproceso, actor);
        if (estaCongelado(subproceso.getEstado())) {
            throw new ApiException("Este subproceso ya está completado, no se pueden hacer más cambios.", HttpStatus.CONFLICT);
        }

        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso)
                .orElseGet(() -> Formulario.builder()
                        .servicioSubproceso(subproceso)
                        .fechaCreacion(LocalDateTime.now())
                        .build());
        formulario.setFechaActualizacion(LocalDateTime.now());
        formulario = formularioRepository.save(formulario);
        // El responsable guardó avance: el trabajo ya empezó (ya no se cancela; si el candidato desiste, este sigue hasta publicarse).
        if (subproceso.getUsuarioAsignado() != null && actor != null && subproceso.getUsuarioAsignado().getIdUsuario().equals(actor.getIdUsuario())) {
            subproceso.marcarIniciado(LocalDateTime.now());
        }

        Map<Integer, FormularioSeccion> seccionesPorId = seccionRepository
                .findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso())
                .stream().collect(Collectors.toMap(FormularioSeccion::getIdSeccion, s -> s));

        // Secciones archivadas que este mismo actor ni siquiera puede ver en obtener() (ver
        // ocultarArchivadoParaEjecutor ahí, MISMA condición que acá) — el frontend nunca las
        // recibió, así que es imposible que vengan en el request. No se tocan aquí: ni se validan
        // como "filas borradas" ni se borran sus filas/datos — de lo contrario este guardado (de
        // OTRAS secciones, totalmente normal) las perdería para siempre, ya que este método
        // reemplaza todo el formulario.
        boolean ocultarArchivadoParaEjecutor = ocultaArchivados(actor);
        Set<Integer> seccionesOcultasIds = ocultarArchivadoParaEjecutor
                ? seccionesPorId.values().stream().filter(s -> !Boolean.TRUE.equals(s.getActivo()))
                        .map(FormularioSeccion::getIdSeccion).collect(Collectors.toSet())
                : Set.of();

        // Secciones que quien diligencia quitó (V212): solo las OPCIONALES de esta plantilla. Lo
        // que tuvieran se borra con el reemplazo de abajo y no se vuelve a guardar — una sección
        // quitada no suma hallazgos ni puntos con datos viejos.
        Set<Integer> omitidas = new java.util.HashSet<>();
        for (Integer idSeccion : req.seccionesOmitidas() == null ? List.<Integer>of() : req.seccionesOmitidas()) {
            FormularioSeccion seccion = seccionesPorId.get(idSeccion);
            if (seccion == null) continue; // ya no existe en la plantilla
            if (!Boolean.TRUE.equals(seccion.getOpcional())) {
                throw new ApiException("La sección \"" + seccion.getNombre() + "\" es obligatoria: no se puede quitar.", HttpStatus.BAD_REQUEST);
            }
            omitidas.add(idSeccion);
        }
        formulario.getSeccionesOmitidas().clear();
        formulario.getSeccionesOmitidas().addAll(omitidas);

        // Quitar una sección opcional sí puede borrar sus filas guardadas (es lo que pidió quien diligencia).
        Set<Integer> fueraDeValidacion = new java.util.HashSet<>(seccionesOcultasIds);
        fueraDeValidacion.addAll(omitidas);
        validarNoEliminaFilas(actor, formulario, req, fueraDeValidacion);

        // Reemplazo completo de filas y datos — mismo patrón de "guardar todo de una vez" que el
        // resto del sistema, evita estados intermedios inconsistentes. Las secciones ocultas de
        // arriba quedan fuera del reemplazo, intactas.
        //
        // deleteAllInBatch (un solo DELETE ... WHERE id IN (...)) en vez de deleteAll: deleteAll()
        // borra entidad por entidad vía el EntityManager, que antes de cada DELETE pone en NULL el
        // id_fila de cada FormularioDato referenciado (para "romper" la FK antes de borrar, aunque
        // la fila referenciada también se vaya a borrar en el mismo flush). Cuando dos filas
        // comparten columna de validación (mismo id_columna, típico de las tablas fijas con
        // origenFijo), esa nulificación intermedia deja momentáneamente dos datos con
        // (id_formulario, id_columna) repetido e id_fila=NULL — justo lo que prohíbe
        // ux_formulario_datos_campo — y el guardado falla aunque ambas filas se fueran a borrar de
        // todos modos. El borrado en bloque va directo a la BD (DELETE físico) sin pasar por esa
        // limpieza intermedia; se borran los datos primero (por más que id_fila tenga ON DELETE
        // CASCADE hacia formulario_filas, ser explícitos evita depender del orden del cascade).
        List<FormularioFila> filasABorrar = filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario())
                .stream().filter(f -> !seccionesOcultasIds.contains(f.getSeccion().getIdSeccion())).toList();
        List<FormularioDato> datosABorrar = datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario())
                .stream().filter(d -> !seccionesOcultasIds.contains(d.getColumna().getSeccion().getIdSeccion())).toList();
        datoRepository.deleteAllInBatch(datosABorrar);
        filaRepository.deleteAllInBatch(filasABorrar);

        List<FormularioDato> nuevosDatos = new ArrayList<>();
        if (req.secciones() != null) {
            for (var s : req.secciones()) {
                FormularioSeccion seccion = seccionesPorId.get(s.idSeccion());
                if (seccion == null) continue; // sección ya no existe en la plantilla — se ignora
                if (omitidas.contains(seccion.getIdSeccion())) continue; // la quitó quien diligencia
                // Tabla que arma el código (ej. hallazgos): nunca guarda filas del analista.
                if (Boolean.TRUE.equals(seccion.getCalculada())) continue;

                Map<Integer, FormularioColumna> columnasPorId = seccion.getColumnas().stream()
                        .collect(Collectors.toMap(FormularioColumna::getIdColumna, c -> c));

                if (seccion.getTipo() == TipoSeccionFormulario.CAMPOS) {
                    if (s.valores() != null) {
                        for (var e : s.valores().entrySet()) {
                            FormularioColumna columna = columnasPorId.get(e.getKey());
                            // Las columnas automáticas nunca se guardan a mano — su valor siempre
                            // se resuelve en vivo (ver obtener()).
                            if (columna == null || columna.esAutomatica()) continue;
                            // NS-07: la criticidad la fijó el cliente en la solicitud. Solo el gestor (o el admin) la cambia, y el cambio va a la solicitud;
                            // para los demás el valor enviado se ignora. Nunca queda una segunda copia en el formulario.
                            if (esCriticidadDeSolicitud(subproceso, columna)) {
                                cambiarCriticidadDeSolicitud(subproceso, e.getValue(), actor);
                                continue;
                            }
                            validarLongitud(columna, e.getValue(), seccion);
                            nuevosDatos.add(FormularioDato.builder()
                                    .formulario(formulario).columna(columna).fila(null).valor(e.getValue())
                                    .justificacion(justificacionSiCritica(columna, e.getValue(), s.justificaciones()))
                                    .build());
                        }
                    }
                } else if (s.filas() != null) {
                    int orden = 0;
                    for (var filaReq : s.filas()) {
                        FormularioFila fila = filaRepository.save(FormularioFila.builder()
                                .formulario(formulario).seccion(seccion)
                                .etiqueta(filaReq.etiqueta() != null ? filaReq.etiqueta() : "")
                                .orden(orden++)
                                .idOrigenFijo(filaReq.idOrigenFijo())
                                .build());
                        if (filaReq.valores() != null) {
                            for (var e : filaReq.valores().entrySet()) {
                                FormularioColumna columna = columnasPorId.get(e.getKey());
                                if (columna == null || columna.esAutomatica()) continue;
                                validarLongitud(columna, e.getValue(), seccion);
                                nuevosDatos.add(FormularioDato.builder()
                                        .formulario(formulario).columna(columna).fila(fila).valor(e.getValue())
                                        .justificacion(justificacionSiCritica(columna, e.getValue(), filaReq.justificaciones()))
                                        .build());
                            }
                        }
                    }
                }
            }
        }
        datoRepository.saveAll(nuevosDatos);

        notificarCambioFormulario(subproceso, actor);
        if (subproceso.getServicio() != null) {
            // El riesgo del caso depende de las respuestas calificables (docs/modelo-calificacion-riesgo.md §3).
            eventos.publishEvent(new com.polygraph.erp.modules.riesgo.service.RecalculoRiesgoSolicitado(
                    subproceso.getServicio().getIdServicio(), actor.getIdUsuario()));
        }

        return obtener(idServicioSubproceso, actor);
    }

    /** Avisa al gestor del cliente y, si aplica, al programador responsable de este subproceso
     *  puntual, cada vez que ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR guarda cambios en el
     *  formulario — así no tienen que entrar a revisar cada servicio para enterarse de que ya
     *  avanzó. GESTOR/ADMIN_POLYGRAPH no generan esta notificación (son quienes la reciben, y
     *  además ya ven el progreso desde su propia pantalla de supervisión). */
    private void notificarCambioFormulario(ServicioSubproceso subproceso, Usuario actor) {
        if (!ROLES_EJECUTORES.contains(actor.getRol())) return;
        Servicio servicio = subproceso.getServicio();
        if (servicio == null) return;

        String nombreActor = (actor.getNombre() + " " + (actor.getApellido() != null ? actor.getApellido() : "")).trim();
        String nombreProceso = subproceso.getTipoProgreso().getNombreProgreso();
        Candidato candidato = servicio.getCandidato();
        String nombreEvaluado = candidato != null
                ? (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim()
                : null;
        String mensaje = nombreActor + " guardó cambios en el formulario de " + nombreProceso
                + (nombreEvaluado != null && !nombreEvaluado.isBlank() ? " — " + nombreEvaluado : "");

        Cliente cliente = servicio.getCliente();
        if (cliente != null && cliente.getIdGestor() != null) {
            notificacionService.crearOAgruparParaUsuario(cliente.getIdGestor(), "FORMULARIO_ACTUALIZADO", "Formulario actualizado",
                    mensaje, "SERVICIO_SUBPROCESO", subproceso.getId());
        }
        Usuario programador = subproceso.getProgramadorResponsable();
        if (programador != null && !programador.getIdUsuario().equals(actor.getIdUsuario())) {
            notificacionService.crearOAgruparParaUsuario(programador.getIdUsuario(), "FORMULARIO_ACTUALIZADO", "Formulario actualizado",
                    mensaje, "SERVICIO_SUBPROCESO", subproceso.getId());
        }
    }

    private FormularioResponse toResponse(ServicioSubproceso subproceso, Candidato candidato, Formulario formulario,
                                           List<FormularioResponse.SeccionDatosResponse> secciones,
                                           List<FormularioResponse.TablaFijaResponse> tablasFijas, String avisoCalculo,
                                           List<com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.AvisoFormulario> avisosFormulario) {
        Servicio servicio = subproceso.getServicio();
        String nombreEvaluado = candidato != null
                ? (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim()
                : null;

        // "Profesional encargado" se resuelve desde la asignación del subproceso, no se guarda
        // en el formulario — servicio_subprocesos.usuarioAsignado ya es la fuente única de esa
        // información.
        Usuario profesionalEncargado = subproceso.getUsuarioAsignado();

        // Resumen fijo de arriba del documento — siempre nombre completo + cédula, sin
        // configuración; para cualquier otro dato del candidato, se arma columna por columna en
        // la plantilla (ver FormularioColumna.campoCandidato).
        List<CampoCandidatoValorResponse> datosCandidato = candidatoInfoService.resolver(candidato, null);

        // Mismo criterio que el documento: un subproceso ya cerrado muestra el nombre del Word con
        // el que se completó (rutaWordCongelada), no el que esté activo ahora mismo — así
        // analista/poligrafista/visitador/gestor/programador ven, sin ambigüedad, con cuál
        // documento quedó su trabajo, igual que ya se ve en admin/catálogo → Plantilla.
        String rutaWord = (estaCongelado(subproceso.getEstado()) && subproceso.getRutaWordCongelada() != null)
                ? subproceso.getRutaWordCongelada()
                : plantillaConfigRepository.findById(subproceso.getTipoProgreso().getIdTipoProgreso())
                        .map(FormularioPlantillaConfig::getPlantillaWordRuta).orElse(null);
        String nombreArchivoWord = nombreOriginalDeRuta(rutaWord);

        return new FormularioResponse(
                formulario != null ? formulario.getIdFormulario() : null,
                subproceso.getId(),
                servicio != null ? servicio.getIdServicio() : null,
                candidato != null ? candidato.getIdCandidato() : null,
                nombreEvaluado,
                candidato != null ? candidato.getCedula() : null,
                profesionalEncargado != null ? profesionalEncargado.getIdUsuario() : null,
                profesionalEncargado != null
                        ? (profesionalEncargado.getNombre() + (profesionalEncargado.getApellido() != null ? " " + profesionalEncargado.getApellido() : ""))
                        : null,
                estaCongelado(subproceso.getEstado()),
                datosCandidato,
                secciones,
                tablasFijas,
                subproceso.getVersionPlantilla(),
                subproceso.getTipoProgreso().getVersionPlantilla(),
                subproceso.getEstado().name(),
                nombreArchivoWord,
                subproceso.getTipoProgreso().getNombreProgreso(),
                subproceso.getFechaProgramada(),
                subproceso.getObservaciones(),
                telefonoDe(candidato),
                candidatoInfoService.resolverUno(candidato, "CIUDAD_RESIDENCIA"),
                servicio != null && servicio.getProceso() != null ? servicio.getProceso().getNombreProceso() : null,
                servicio != null ? servicio.getCargo() : null,
                subproceso.getFechaAsignacion(),
                subproceso.getFechaCompletado(),
                subproceso.getDuracionMinutos() != null ? subproceso.getDuracionMinutos()
                        : subproceso.getTipoProgreso().getMinutosEstimados(),
                servicio != null ? servicio.fechaParaMostrar() : null,
                avisoCalculo,
                avisosFormulario,
                servicio != null && servicio.getCriticidadCargo() != null
        );
    }

    private String telefonoDe(Candidato candidato) {
        String celular = candidatoInfoService.resolverUno(candidato, "CELULAR");
        return celular != null && !celular.isBlank() ? celular : candidatoInfoService.resolverUno(candidato, "TELEFONO_FIJO");
    }

    /** Mismo criterio que FormularioPlantillaService.nombreOriginalDeRuta — la ruta guarda
     *  "{uuid}_{nombre}" para que dos cargas no choquen en disco; esto le quita el prefijo para
     *  mostrar solo el nombre real (ya renombrado a "{subproceso}_{fecha}.docx" desde que se sube,
     *  ver FormularioPlantillaService.subirPlantillaWord()). null si no hay ningún Word asociado. */
    private String nombreOriginalDeRuta(String ruta) {
        if (ruta == null) return null;
        String segmento = ruta.substring(ruta.lastIndexOf('/') + 1);
        int guionBajo = segmento.indexOf('_');
        return guionBajo >= 0 ? segmento.substring(guionBajo + 1) : segmento;
    }

    /** Sube un archivo (imagen/documento) para adjuntarlo a una columna tipo ARCHIVO — el
     *  frontend guarda la ruta devuelta como el "valor" de esa columna, igual que cualquier
     *  otro dato, al guardar el formulario. */
    public String subirArchivo(Long idServicioSubproceso, MultipartFile archivo, Usuario actor) {
        ServicioSubproceso subproceso = obtenerSubproceso(idServicioSubproceso);
        validarAcceso(subproceso, actor);
        return almacenamientoService.guardar(archivo, CATEGORIA_ARCHIVOS, String.valueOf(idServicioSubproceso));
    }

    /** Solo sirve archivos cuya ruta ya está dentro de la carpeta de ESTE subproceso — evita que
     *  alguien con acceso a un subproceso adivine la ruta de otro. */
    @Transactional(readOnly = true)
    public ArchivoDescarga descargarArchivo(Long idServicioSubproceso, String ruta, Usuario actor) {
        ServicioSubproceso subproceso = obtenerSubproceso(idServicioSubproceso);
        validarAcceso(subproceso, actor);
        String prefijoEsperado = CATEGORIA_ARCHIVOS + "/" + idServicioSubproceso + "/";
        if (ruta == null || !ruta.startsWith(prefijoEsperado)) {
            throw new ApiException("Archivo no encontrado", HttpStatus.NOT_FOUND);
        }
        String segmento = ruta.substring(ruta.lastIndexOf('/') + 1);
        String nombreArchivo = segmento.contains("_") ? segmento.substring(segmento.indexOf('_') + 1) : segmento;
        return new ArchivoDescarga(almacenamientoService.cargarComoResource(ruta), nombreArchivo,
                almacenamientoService.tipoContenido(nombreArchivo));
    }

    /** Qué tan lleno está el formulario — solo cuenta columnas de secciones tipo CAMPOS (las
     *  TABLA no tienen un total fijo). Usado por GESTOR para ver de un vistazo cómo va el
     *  llenado sin tener que abrir el formulario completo. */
    @Transactional(readOnly = true)
    public ProgresoFormularioResponse progreso(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = obtenerSubproceso(idServicioSubproceso);
        validarAcceso(subproceso, actor);

        List<FormularioSeccion> secciones = seccionRepository
                .findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso());
        // Solo columnas de secciones ACTIVAS cuentan — sin este filtro, el "X/Y" quedaba inflado
        // con columnas de secciones archivadas (ej. de pruebas viejas) que ni siquiera se ven ya
        // en el formulario, así que no cuadraba con lo que la persona tenía en pantalla. Mismo
        // criterio de congelamiento por versión que obtener()/generar(): una vez COMPLETADO, una
        // sección agregada a la plantilla DESPUÉS no debe inflar el total (sin esto, "X/Y" seguía
        // subiendo con cada cambio a la plantilla aunque el formulario ya estuviera cerrado).
        boolean congelado = estaCongelado(subproceso.getEstado());
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null);
        // Una sección que quitó quien diligencia (V212) no cuenta para el avance.
        Set<Integer> omitidas = formulario == null ? Set.of() : formulario.getSeccionesOmitidas();
        Set<Integer> columnasActivasIds = secciones.stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()) && s.getTipo() == TipoSeccionFormulario.CAMPOS)
                .filter(s -> !omitidas.contains(s.getIdSeccion()))
                .filter(s -> !congelado || s.getVersionCreacion() <= subproceso.getVersionPlantilla())
                .flatMap(s -> s.getColumnas().stream())
                .filter(c -> Boolean.TRUE.equals(c.getActivo()) && !c.esAutomatica())
                .map(FormularioColumna::getIdColumna)
                .collect(Collectors.toSet());
        int total = columnasActivasIds.size();

        int llenos = formulario == null ? 0 : (int) datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario())
                .stream()
                .filter(d -> d.getFila() == null)
                .filter(d -> columnasActivasIds.contains(d.getColumna().getIdColumna()))
                .filter(d -> d.getValor() != null && !d.getValor().isBlank())
                .count();

        // Avance por secciones (ver ProgresoFormularioResponse.porcentaje).
        List<FormularioSeccion> seccionesVigentes = secciones.stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .filter(s -> !omitidas.contains(s.getIdSeccion()))
                .filter(s -> !congelado || s.getVersionCreacion() <= subproceso.getVersionPlantilla())
                .toList();
        Set<Integer> seccionesConFilas = formulario == null ? Set.of()
                : filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario()).stream()
                        .filter(f -> f.getIdOrigenFijo() == null && f.getSeccion() != null)
                        .map(f -> f.getSeccion().getIdSeccion())
                        .collect(Collectors.toSet());
        Map<String, List<Map<String, String>>> tablasFijas = seccionesVigentes.stream().anyMatch(s -> s.getOrigenFijo() != null)
                ? candidatoInfoService.resolverTablasFijas(subproceso.getServicio() != null ? subproceso.getServicio().getCandidato() : null)
                : Map.of();
        Set<Integer> llenosIds = formulario == null ? Set.of()
                : datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario()).stream()
                        .filter(d -> d.getFila() == null && d.getValor() != null && !d.getValor().isBlank())
                        .map(d -> d.getColumna().getIdColumna())
                        .collect(Collectors.toSet());

        double suma = 0;
        int seccionesTotal = 0;
        int seccionesCompletas = 0;
        for (FormularioSeccion s : seccionesVigentes) {
            double fraccion;
            if (s.getTipo() == TipoSeccionFormulario.CAMPOS) {
                List<Integer> cols = s.getColumnas().stream().filter(c -> Boolean.TRUE.equals(c.getActivo()) && !c.esAutomatica())
                        .map(FormularioColumna::getIdColumna).toList();
                if (cols.isEmpty()) continue;
                fraccion = cols.stream().filter(llenosIds::contains).count() / (double) cols.size();
            } else if (Boolean.TRUE.equals(s.getCalculada())) {
                continue; // la arma el sistema (ej. hallazgos), no es trabajo de quien diligencia
            } else if (s.getOrigenFijo() != null) {
                fraccion = tablasFijas.getOrDefault(s.getOrigenFijo(), List.of()).isEmpty() ? 0 : 1;
            } else {
                fraccion = seccionesConFilas.contains(s.getIdSeccion()) ? 1 : 0;
            }
            seccionesTotal++;
            if (fraccion >= 1) seccionesCompletas++;
            suma += fraccion;
        }
        Integer porcentaje = seccionesTotal == 0 ? null : (int) Math.round(suma / seccionesTotal * 100);

        return new ProgresoFormularioResponse(llenos, total, porcentaje, seccionesCompletas, seccionesTotal);
    }

    private ServicioSubproceso obtenerSubproceso(Long id) {
        return servicioSubprocesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
    }

    /** COMPLETADO y PUBLICADO son ambos "cerrados" para el formulario: ya no se edita y queda
     *  congelado en la versión de plantilla que tenía al completarse (PUBLICADO no vuelve a
     *  estampar versión propia, hereda la de COMPLETADO — ver AsignacionService.aprobar()). Antes
     *  de que existiera PUBLICADO, esto solo comparaba contra COMPLETADO; sin este helper,
     *  aprobar un subproceso lo volvía a dejar editable, porque `!= COMPLETADO` ya no era cierto. */
    private static boolean estaCongelado(EstadoSubproceso estado) {
        return estado == EstadoSubproceso.FINALIZADO || estado == EstadoSubproceso.PUBLICADO;
    }

    private static final Set<Rol> ROLES_EJECUTORES = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    /** ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR solo acceden a subprocesos asignados a ellos
     *  mismos — GESTOR/ADMIN_POLYGRAPH entran sin restricción (supervisión). */
    private void validarAcceso(ServicioSubproceso subproceso, Usuario actor) {
        com.polygraph.erp.shared.utils.AccesoGestor.validar(subproceso, actor);
        if (ROLES_EJECUTORES.contains(actor.getRol())) {
            Usuario asignado = subproceso.getUsuarioAsignado();
            if (asignado == null || !actor.getIdUsuario().equals(asignado.getIdUsuario())) {
                throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
            }
        }
    }

    /** ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR pueden agregar/editar filas de una tabla, pero no
     *  borrarlas — como este servicio reemplaza TODAS las filas de la sección en cada guardado
     *  (no hay un id estable de fila entre un guardado y el siguiente), la única señal disponible
     *  es que no vengan MENOS filas de las que ya había en esa sección. Imperfecto (cambiar una
     *  fila por otra distinta sin bajar el total no se detecta), pero cubre el caso obvio de
     *  saltarse la interfaz (que ya no muestra el botón de borrar para estos roles) y llamar la
     *  API directo con menos filas. */
    private void validarNoEliminaFilas(Usuario actor, Formulario formulario, GuardarFormularioRequest req,
                                        Set<Integer> seccionesOcultasIds) {
        if (!ROLES_EJECUTORES.contains(actor.getRol()) || formulario.getIdFormulario() == null) return;
        Map<Integer, Long> existentesPorSeccion = filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario())
                .stream().filter(f -> !seccionesOcultasIds.contains(f.getSeccion().getIdSeccion()))
                .collect(Collectors.groupingBy(f -> f.getSeccion().getIdSeccion(), Collectors.counting()));
        Map<Integer, Integer> nuevasPorSeccion = new HashMap<>();
        if (req.secciones() != null) {
            for (var s : req.secciones()) {
                nuevasPorSeccion.put(s.idSeccion(), s.filas() == null ? 0 : s.filas().size());
            }
        }
        for (var entry : existentesPorSeccion.entrySet()) {
            int nuevas = nuevasPorSeccion.getOrDefault(entry.getKey(), 0);
            if (nuevas < entry.getValue()) {
                throw new ApiException("No tienes permiso para borrar filas ya guardadas — solo puedes editarlas o agregar nuevas.",
                        HttpStatus.FORBIDDEN);
            }
        }
    }

    private static final java.util.Map<com.polygraph.erp.shared.enums.CriticidadCargo, String> ETIQUETA_CRITICIDAD = java.util.Map.of(
            com.polygraph.erp.shared.enums.CriticidadCargo.ESTANDAR, "Estándar",
            com.polygraph.erp.shared.enums.CriticidadCargo.SENSIBLE, "Sensible",
            com.polygraph.erp.shared.enums.CriticidadCargo.CRITICO, "Crítico");

    /** ¿Esta columna es el campo de criticidad de la plantilla y la solicitud ya trae una (NS-07)? */
    private static boolean esCriticidadDeSolicitud(ServicioSubproceso subproceso, FormularioColumna columna) {
        return columna.getUsoCalificacion() != null && "CRITICIDAD".equals(columna.getUsoCalificacion())
                && subproceso.getServicio().getCriticidadCargo() != null;
    }

    /** El valor (etiqueta de la lista) con el que se precarga el campo de criticidad; null si la columna no es esa o la solicitud no la tiene. */
    private static String criticidadDeSolicitud(ServicioSubproceso subproceso, FormularioColumna columna) {
        return esCriticidadDeSolicitud(subproceso, columna) ? ETIQUETA_CRITICIDAD.get(subproceso.getServicio().getCriticidadCargo()) : null;
    }

    /** Solo gestor o admin; si el valor es distinto, se actualiza la solicitud, queda quién lo cambió y se recalcula el riesgo (evento de guardar). */
    private void cambiarCriticidadDeSolicitud(ServicioSubproceso subproceso, String valor, Usuario actor) {
        if (actor == null || (actor.getRol() != Rol.GESTOR && actor.getRol() != Rol.ADMIN_POLYGRAPH)) return;
        var nueva = com.polygraph.erp.modules.riesgo.hallazgos.MotorHallazgos.criticidad(valor);
        var servicio = subproceso.getServicio();
        if (nueva == null || nueva == servicio.getCriticidadCargo()) return;
        log.info("Criticidad del cargo del servicio {} cambiada de {} a {} por {} desde el formulario", servicio.getIdServicio(),
                servicio.getCriticidadCargo(), nueva, actor.getIdUsuario());
        servicio.setCriticidadCargo(nueva);
        servicio.setUsuarioCriticidad(actor);
    }
}
