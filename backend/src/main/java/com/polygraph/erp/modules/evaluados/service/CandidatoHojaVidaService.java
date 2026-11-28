package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.evaluados.dto.CandidatoHojaVidaResponse;
import com.polygraph.erp.modules.evaluados.dto.GuardarTablaFijaRequest;
import com.polygraph.erp.modules.evaluados.dto.TablaFijaEditableResponse;
import com.polygraph.erp.modules.evaluados.entity.*;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.formularios.service.CandidatoInfoService;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** CRUD de la hoja de vida REAL del candidato (referencias, educación, experiencia laboral,
 *  inactividades) para uso interno — analista/poligrafista/visitador la ven y la corrigen/amplían
 *  directamente, no una copia por servicio: un cambio aquí se ve en cualquier otro servicio de
 *  esa misma persona (y en su propio link de autodiligenciamiento), a propósito. */
@Service
@RequiredArgsConstructor
@Transactional
public class CandidatoHojaVidaService {

    private final CandidatoRepository candidatoRepository;
    private final HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;

    private static final Set<Rol> ROLES_EJECUTORES = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    @Transactional(readOnly = true)
    public CandidatoHojaVidaResponse obtener(Long idCandidato, Usuario actor) {
        Candidato candidato = obtenerCandidato(idCandidato);
        validarAcceso(candidato, actor);
        HojaVidaEvaluado hv = hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(idCandidato).orElse(null);
        return toResponse(candidato, hv);
    }

    public TablaFijaEditableResponse guardarTabla(Long idCandidato, String clave, GuardarTablaFijaRequest req, Usuario actor) {
        Candidato candidato = obtenerCandidato(idCandidato);
        validarAcceso(candidato, actor);
        if (!CandidatoInfoService.CLAVES_TABLAS_FIJAS.contains(clave)) {
            throw new ApiException("Tabla inválida: " + clave, HttpStatus.BAD_REQUEST);
        }
        if (CandidatoInfoService.TABLAS_SOLO_LECTURA.contains(clave)) {
            throw new ApiException("Las declaraciones solo las responde el candidato desde su link.", HttpStatus.FORBIDDEN);
        }
        // LB-02 / AC-02: la experiencia y los estudios son lo que declaró el evaluado; el analista los consulta pero no los cambia (los leen otros servicios del mismo candidato).
        if (("experiencia_laboral".equals(clave) || "educacion".equals(clave)) && actor.getRol() == Rol.ANALISTA_INTERNO) {
            throw new ApiException("Lo que declaró el evaluado (experiencia y estudios) lo declara él desde su link; el analista no lo modifica.", HttpStatus.FORBIDDEN);
        }
        HojaVidaEvaluado hv = hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(idCandidato)
                .orElseGet(() -> hojaVidaEvaluadoRepository.save(HojaVidaEvaluado.builder()
                        .candidato(candidato).autorizacionDatos(false).completado(false).build()));

        List<GuardarTablaFijaRequest.FilaFijaRequest> filas = req.filas() == null ? List.of() : req.filas();
        validarNoEliminaFilas(actor, idsExistentesDe(clave, hv), filas);
        switch (clave) {
            case "referencias" -> guardarReferencias(hv, filas);
            case "educacion" -> guardarEducacion(hv, filas);
            case "experiencia_laboral" -> guardarExperienciaLaboral(hv, filas);
            case "inactividades" -> guardarInactividades(hv, filas);
            default -> throw new ApiException("Tabla inválida: " + clave, HttpStatus.BAD_REQUEST);
        }
        hojaVidaEvaluadoRepository.save(hv);
        return tablaResponseDe(clave, hv);
    }

    private void guardarReferencias(HojaVidaEvaluado hv, List<GuardarTablaFijaRequest.FilaFijaRequest> filas) {
        Map<Long, ReferenciaPersonalEvaluado> existentes = indexarPorId(hv.getReferencias(), ReferenciaPersonalEvaluado::getId);
        List<ReferenciaPersonalEvaluado> resultado = new ArrayList<>();
        for (var filaReq : filas) {
            Map<String, String> v = valores(filaReq);
            if (todosVacios(v)) continue;
            String nombre = texto(v.get("nombre"));
            if (nombre == null) throw new ApiException("El nombre es obligatorio en cada referencia.", HttpStatus.BAD_REQUEST);
            ReferenciaPersonalEvaluado r = filaReq.id() != null ? existentes.get(filaReq.id()) : null;
            if (r == null) r = ReferenciaPersonalEvaluado.builder().hojaVidaEvaluado(hv).build();
            r.setNombre(nombre);
            r.setParentesco(texto(v.get("parentesco")));
            r.setTelefono(texto(v.get("telefono")));
            r.setTiempoConocimiento(texto(v.get("tiempo_conocimiento")));
            if (v.containsKey("confirma_no_familiar")) r.setConfirmaNoFamiliar(booleano(v.get("confirma_no_familiar")));
            if (v.containsKey("hablo_ultimos_30_dias")) r.setHabloUltimos30Dias(booleano(v.get("hablo_ultimos_30_dias")));
            resultado.add(r);
        }
        hv.getReferencias().clear();
        hv.getReferencias().addAll(resultado);
    }

    private void guardarEducacion(HojaVidaEvaluado hv, List<GuardarTablaFijaRequest.FilaFijaRequest> filas) {
        Map<Long, EducacionEvaluado> existentes = indexarPorId(hv.getEducacion(), EducacionEvaluado::getId);
        List<EducacionEvaluado> resultado = new ArrayList<>();
        for (var filaReq : filas) {
            Map<String, String> v = valores(filaReq);
            if (todosVacios(v)) continue;
            String institucion = texto(v.get("institucion"));
            if (institucion == null) throw new ApiException("La institución es obligatoria en cada fila de educación.", HttpStatus.BAD_REQUEST);
            EducacionEvaluado e = filaReq.id() != null ? existentes.get(filaReq.id()) : null;
            if (e == null) e = EducacionEvaluado.builder().hojaVidaEvaluado(hv).build();
            e.setNivel(texto(v.get("nivel")));
            e.setInstitucion(institucion);
            e.setTitulo(texto(v.get("titulo")));
            e.setFechaInicio(fecha(v.get("fecha_inicio")));
            e.setFechaFin(fecha(v.get("fecha_fin")));
            e.setEnCurso(booleano(v.get("en_curso")));
            e.setCiudad(texto(v.get("ciudad")));
            if (v.containsKey("estado")) e.setEstado(texto(v.get("estado")));
            if (v.containsKey("en_exterior")) e.setEnExterior(booleano(v.get("en_exterior")));
            if (v.containsKey("pais_exterior")) e.setPaisExterior(texto(v.get("pais_exterior")));
            resultado.add(e);
        }
        hv.getEducacion().clear();
        hv.getEducacion().addAll(resultado);
    }

    private void guardarExperienciaLaboral(HojaVidaEvaluado hv, List<GuardarTablaFijaRequest.FilaFijaRequest> filas) {
        Map<Long, ExperienciaLaboralEvaluado> existentes = indexarPorId(hv.getExperienciaLaboral(), ExperienciaLaboralEvaluado::getId);
        List<ExperienciaLaboralEvaluado> resultado = new ArrayList<>();
        for (var filaReq : filas) {
            Map<String, String> v = valores(filaReq);
            if (todosVacios(v)) continue;
            String empresa = texto(v.get("empresa"));
            String cargo = texto(v.get("cargo"));
            LocalDate fechaInicio = fecha(v.get("fecha_inicio"));
            if (empresa == null || cargo == null || fechaInicio == null) {
                throw new ApiException("Empresa, cargo y fecha de inicio son obligatorios en cada experiencia laboral.", HttpStatus.BAD_REQUEST);
            }
            ExperienciaLaboralEvaluado ex = filaReq.id() != null ? existentes.get(filaReq.id()) : null;
            if (ex == null) ex = ExperienciaLaboralEvaluado.builder().hojaVidaEvaluado(hv).build();
            ex.setEmpresa(empresa);
            ex.setCargo(cargo);
            ex.setFechaInicio(fechaInicio);
            ex.setFechaFin(fecha(v.get("fecha_fin")));
            ex.setLaboraActualmente(booleano(v.get("labora_actualmente")));
            ex.setCiudad(texto(v.get("ciudad")));
            ex.setTelefonoEmpresa(texto(v.get("telefono_empresa")));
            ex.setMotivoRetiro(texto(v.get("motivo_retiro")));
            ex.setNombreJefe(texto(v.get("nombre_jefe")));
            ex.setCargoJefe(texto(v.get("cargo_jefe")));
            // Columnas nuevas (V211): una plantilla armada antes no las manda — no se borran.
            if (v.containsKey("tipo_contrato")) ex.setTipoContrato(texto(v.get("tipo_contrato")));
            if (v.containsKey("salario")) ex.setSalario(salario(v.get("salario")));
            if (v.containsKey("llamados_atencion")) ex.setLlamadosAtencion(texto(v.get("llamados_atencion")));
            if (v.containsKey("contratado_por_temporal")) ex.setContratadoPorTemporal(booleano(v.get("contratado_por_temporal")));
            if (v.containsKey("nombre_temporal")) ex.setNombreTemporal(texto(v.get("nombre_temporal")));
            resultado.add(ex);
        }
        hv.getExperienciaLaboral().clear();
        hv.getExperienciaLaboral().addAll(resultado);
    }

    private void guardarInactividades(HojaVidaEvaluado hv, List<GuardarTablaFijaRequest.FilaFijaRequest> filas) {
        Map<Long, InactividadLaboralEvaluado> existentes = indexarPorId(hv.getInactividades(), InactividadLaboralEvaluado::getId);
        List<InactividadLaboralEvaluado> resultado = new ArrayList<>();
        for (var filaReq : filas) {
            Map<String, String> v = valores(filaReq);
            if (todosVacios(v)) continue;
            LocalDate fechaInicio = fecha(v.get("fecha_inicio"));
            LocalDate fechaFin = fecha(v.get("fecha_fin"));
            if (fechaInicio == null || fechaFin == null) {
                throw new ApiException("Fecha de inicio y fecha de fin son obligatorias en cada inactividad.", HttpStatus.BAD_REQUEST);
            }
            InactividadLaboralEvaluado in = filaReq.id() != null ? existentes.get(filaReq.id()) : null;
            if (in == null) in = InactividadLaboralEvaluado.builder().hojaVidaEvaluado(hv).build();
            in.setFechaInicio(fechaInicio);
            in.setFechaFin(fechaFin);
            String diasTexto = texto(v.get("dias_inactivo"));
            in.setDiasInactivo(diasTexto != null ? Integer.parseInt(diasTexto) : (int) (fechaFin.toEpochDay() - fechaInicio.toEpochDay()));
            in.setJustificacion(texto(v.get("justificacion")));
            if (v.containsKey("actividad")) in.setActividad(texto(v.get("actividad")));
            if (v.containsKey("quien_confirma")) in.setQuienConfirma(texto(v.get("quien_confirma")));
            in.setRequiereCuestionario(Boolean.TRUE.equals(booleano(v.get("requiere_cuestionario"))));
            resultado.add(in);
        }
        hv.getInactividades().clear();
        hv.getInactividades().addAll(resultado);
    }

    private <T> Map<Long, T> indexarPorId(List<T> lista, java.util.function.Function<T, Long> idDe) {
        Map<Long, T> mapa = new HashMap<>();
        for (T item : lista) {
            Long id = idDe.apply(item);
            if (id != null) mapa.put(id, item);
        }
        return mapa;
    }

    private Map<String, String> valores(GuardarTablaFijaRequest.FilaFijaRequest fila) {
        return fila.valores() == null ? Map.of() : fila.valores();
    }

    private boolean todosVacios(Map<String, String> valores) {
        return valores.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    private String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private LocalDate fecha(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            throw new ApiException("Fecha inválida: " + v, HttpStatus.BAD_REQUEST);
        }
    }

    private Long salario(String v) {
        String digitos = v == null ? "" : v.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) return null;
        if (digitos.length() > 15) throw new ApiException("Salario inválido: " + v, HttpStatus.BAD_REQUEST);
        return Long.parseLong(digitos);
    }

    private Boolean booleano(String v) {
        if (v == null || v.isBlank()) return null;
        return Boolean.parseBoolean(v);
    }

    private CandidatoHojaVidaResponse toResponse(Candidato candidato, HojaVidaEvaluado hv) {
        List<TablaFijaEditableResponse> tablas = CandidatoInfoService.CLAVES_TABLAS_FIJAS.stream()
                .filter(clave -> !CandidatoInfoService.TABLAS_SOLO_LECTURA.contains(clave))
                .map(clave -> tablaResponseDe(clave, hv))
                .toList();
        String nombreCompleto = (candidato.getNombres() + " " + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim();
        return new CandidatoHojaVidaResponse(candidato.getIdCandidato(), nombreCompleto, candidato.getCedula(), tablas);
    }

    private TablaFijaEditableResponse tablaResponseDe(String clave, HojaVidaEvaluado hv) {
        List<String> clavesColumnas = CandidatoInfoService.COLUMNAS_TABLAS_FIJAS.getOrDefault(clave, List.of());
        List<TablaFijaEditableResponse.ColumnaFija> columnas = clavesColumnas.stream()
                .map(c -> new TablaFijaEditableResponse.ColumnaFija(c,
                        CandidatoInfoService.ETIQUETAS_COLUMNAS_TABLAS_FIJAS.getOrDefault(c, c),
                        CandidatoInfoService.TIPOS_DATO_TABLAS_FIJAS.getOrDefault(c, com.polygraph.erp.shared.enums.TipoDatoFormulario.TEXTO).name()))
                .toList();
        List<TablaFijaEditableResponse.FilaFija> filas = switch (clave) {
            case "referencias" -> (hv == null ? List.<ReferenciaPersonalEvaluado>of() : hv.getReferencias()).stream()
                    .map(r -> new TablaFijaEditableResponse.FilaFija(r.getId(), mapaOrdenado(
                            "nombre", val(r.getNombre()), "parentesco", val(r.getParentesco()),
                            "telefono", val(r.getTelefono()), "tiempo_conocimiento", val(r.getTiempoConocimiento()),
                            "confirma_no_familiar", valBooleano(r.getConfirmaNoFamiliar()),
                            "hablo_ultimos_30_dias", valBooleano(r.getHabloUltimos30Dias()))))
                    .toList();
            case "educacion" -> (hv == null ? List.<EducacionEvaluado>of() : hv.getEducacion()).stream()
                    .map(e -> new TablaFijaEditableResponse.FilaFija(e.getId(), mapaOrdenado(
                            "nivel", val(e.getNivel()), "institucion", val(e.getInstitucion()), "titulo", val(e.getTitulo()),
                            "fecha_inicio", valFecha(e.getFechaInicio()), "fecha_fin", valFecha(e.getFechaFin()),
                            "en_curso", valBooleano(e.getEnCurso()), "ciudad", val(e.getCiudad()),
                            "estado", val(e.getEstado()), "en_exterior", valBooleano(e.getEnExterior()),
                            "pais_exterior", val(e.getPaisExterior()))))
                    .toList();
            case "experiencia_laboral" -> (hv == null ? List.<ExperienciaLaboralEvaluado>of() : hv.getExperienciaLaboral()).stream()
                    .map(ex -> new TablaFijaEditableResponse.FilaFija(ex.getId(), mapaOrdenado(
                            "empresa", val(ex.getEmpresa()), "cargo", val(ex.getCargo()),
                            "fecha_inicio", valFecha(ex.getFechaInicio()), "fecha_fin", valFecha(ex.getFechaFin()),
                            "labora_actualmente", valBooleano(ex.getLaboraActualmente()), "ciudad", val(ex.getCiudad()),
                            "telefono_empresa", val(ex.getTelefonoEmpresa()), "motivo_retiro", val(ex.getMotivoRetiro()),
                            "nombre_jefe", val(ex.getNombreJefe()), "cargo_jefe", val(ex.getCargoJefe()),
                            "tipo_contrato", val(ex.getTipoContrato()),
                            "salario", ex.getSalario() == null ? "" : String.valueOf(ex.getSalario()),
                            "llamados_atencion", val(ex.getLlamadosAtencion()),
                            "contratado_por_temporal", valBooleano(ex.getContratadoPorTemporal()),
                            "nombre_temporal", val(ex.getNombreTemporal()))))
                    .toList();
            case "inactividades" -> (hv == null ? List.<InactividadLaboralEvaluado>of() : hv.getInactividades()).stream()
                    .map(in -> new TablaFijaEditableResponse.FilaFija(in.getId(), mapaOrdenado(
                            "fecha_inicio", valFecha(in.getFechaInicio()), "fecha_fin", valFecha(in.getFechaFin()),
                            "dias_inactivo", in.getDiasInactivo() == null ? "" : String.valueOf(in.getDiasInactivo()),
                            "justificacion", val(in.getJustificacion()), "actividad", val(in.getActividad()),
                            "quien_confirma", val(in.getQuienConfirma()), "requiere_cuestionario", valBooleano(in.getRequiereCuestionario()))))
                    .toList();
            default -> List.of();
        };
        String etiqueta = switch (clave) {
            case "referencias" -> "Referencias personales";
            case "educacion" -> "Educación";
            case "experiencia_laboral" -> "Experiencia laboral";
            case "inactividades" -> "Inactividades laborales";
            default -> clave;
        };
        return new TablaFijaEditableResponse(clave, etiqueta, columnas, filas);
    }

    private String val(String s) {
        return s == null ? "" : s;
    }

    private String valFecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.toString();
    }

    private String valBooleano(Boolean valor) {
        return valor == null ? "" : valor.toString();
    }

    private Map<String, String> mapaOrdenado(String... parejas) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < parejas.length; i += 2) mapa.put(parejas[i], parejas[i + 1]);
        return mapa;
    }

    private Candidato obtenerCandidato(Long idCandidato) {
        return candidatoRepository.findById(idCandidato)
                .orElseThrow(() -> new ApiException("Candidato no encontrado", HttpStatus.NOT_FOUND));
    }

    /** ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR solo acceden a candidatos con los que tienen
     *  relación real (algún servicio de esa persona asignado a ellos) — GESTOR/PROGRAMADOR/
     *  ADMIN_POLYGRAPH entran sin restricción (supervisión), igual que en FormularioService. */
    private void validarAcceso(Candidato candidato, Usuario actor) {
        if (ROLES_EJECUTORES.contains(actor.getRol())
                && !servicioSubprocesoRepository.existsByServicio_Candidato_IdCandidatoAndUsuarioAsignado_IdUsuario(
                        candidato.getIdCandidato(), actor.getIdUsuario())) {
            throw new ApiException("No tienes ningún servicio asignado de este candidato.", HttpStatus.FORBIDDEN);
        }
        // GESTOR: solo candidatos de sus clientes (ver AccesoGestor, corregido 25-sep-2026).
        if (actor.getRol() == com.polygraph.erp.shared.enums.Rol.GESTOR
                && !servicioSubprocesoRepository.existsByServicio_Candidato_IdCandidatoAndServicio_Cliente_IdGestor(
                        candidato.getIdCandidato(), actor.getIdUsuario())) {
            throw new ApiException("Este candidato es de un cliente que no gestionas.", HttpStatus.FORBIDDEN);
        }
    }

    private List<Long> idsExistentesDe(String clave, HojaVidaEvaluado hv) {
        if (hv == null) return List.of();
        return switch (clave) {
            case "referencias" -> hv.getReferencias().stream().map(ReferenciaPersonalEvaluado::getId).toList();
            case "educacion" -> hv.getEducacion().stream().map(EducacionEvaluado::getId).toList();
            case "experiencia_laboral" -> hv.getExperienciaLaboral().stream().map(ExperienciaLaboralEvaluado::getId).toList();
            case "inactividades" -> hv.getInactividades().stream().map(InactividadLaboralEvaluado::getId).toList();
            default -> List.of();
        };
    }

    /** ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR pueden agregar y editar filas de la hoja de vida
     *  real del candidato, pero no borrar ninguna — evita que se pierda por accidente (o a
     *  propósito) un dato que puede estar en uso en otro servicio de esa misma persona. La UI ya
     *  no muestra el botón de borrar para estos roles, pero esto lo hace cumplir de verdad
     *  aunque alguien llame la API directamente. */
    private void validarNoEliminaFilas(Usuario actor, List<Long> idsExistentes,
                                        List<GuardarTablaFijaRequest.FilaFijaRequest> filasNuevas) {
        if (!ROLES_EJECUTORES.contains(actor.getRol()) || idsExistentes.isEmpty()) return;
        Set<Long> idsEnRequest = filasNuevas.stream()
                .map(GuardarTablaFijaRequest.FilaFijaRequest::id).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!idsEnRequest.containsAll(idsExistentes)) {
            throw new ApiException("No tienes permiso para borrar filas ya guardadas — solo puedes editarlas o agregar nuevas.",
                    HttpStatus.FORBIDDEN);
        }
    }
}
