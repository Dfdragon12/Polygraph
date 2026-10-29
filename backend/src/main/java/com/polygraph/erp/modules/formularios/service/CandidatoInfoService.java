package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.formularios.dto.CampoCandidatoValorResponse;
import com.polygraph.erp.modules.formularios.dto.FichaCandidatoResponse;
import com.polygraph.erp.shared.enums.CampoCandidato;
import com.polygraph.erp.shared.enums.CampoCliente;
import com.polygraph.erp.shared.enums.CampoServicio;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resuelve los datos del candidato (y, para columnas campoCliente, del cliente) que la plantilla
 *  configuró mostrar (ver FormularioPlantillaConfig.camposCandidato) — misma lógica para el
 *  formulario en pantalla (FormularioService) y para el documento Word generado
 *  (FormularioDocumentoService), así nunca se desincroniza uno del otro. El valor siempre sale en
 *  vivo de la tabla de candidatos/clientes, nunca de un dato guardado a mano. */
@Service
@RequiredArgsConstructor
public class CandidatoInfoService {

    private static final List<String> CAMPOS_POR_DEFECTO = List.of("NOMBRE_COMPLETO", "CEDULA");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CiudadRepository ciudadRepository;
    private final HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;

    /** camposCsv null/vacío = comportamiento de siempre (nombre completo + cédula) — se usa solo
     *  para el resumen fijo de arriba del documento. */
    public List<CampoCandidatoValorResponse> resolver(Candidato candidato, String camposCsv) {
        return parsearClaves(camposCsv).stream()
                .map(this::parseCampo)
                .filter(Objects::nonNull)
                .map(campo -> new CampoCandidatoValorResponse(campo.name(), campo.getEtiqueta(), valorDe(candidato, campo)))
                .toList();
    }

    /** Resuelve UNA columna de la plantilla marcada como dato del candidato (ver
     *  FormularioColumna.campoCandidato) — clave inválida o desconocida devuelve "". */
    public String resolverUno(Candidato candidato, String clave) {
        CampoCandidato campo = parseCampo(clave);
        return campo == null ? "" : valorDe(candidato, campo);
    }

    /** Grupos fijos de la ficha del candidato que se consulta desde el formulario (ver
     *  FormularioService.fichaCandidato). NOMBRES/APELLIDOS se omiten: ya van en NOMBRE_COMPLETO. */
    private static final LinkedHashMap<String, List<CampoCandidato>> GRUPOS_FICHA = new LinkedHashMap<>();
    static {
        GRUPOS_FICHA.put("Identificación", List.of(CampoCandidato.NOMBRE_COMPLETO, CampoCandidato.TIPO_DOCUMENTO,
                CampoCandidato.CEDULA, CampoCandidato.FECHA_EXPEDICION, CampoCandidato.CIUDAD_EXPEDICION,
                CampoCandidato.FECHA_NACIMIENTO,
                CampoCandidato.LUGAR_NACIMIENTO, CampoCandidato.CIUDAD_NACIMIENTO, CampoCandidato.ESTADO_CIVIL,
                CampoCandidato.RH));
        GRUPOS_FICHA.put("Contacto y residencia", List.of(CampoCandidato.CELULAR, CampoCandidato.TELEFONO_FIJO,
                CampoCandidato.EMAIL_PRINCIPAL, CampoCandidato.CIUDAD_RESIDENCIA, CampoCandidato.DIRECCION_RESIDENCIA,
                CampoCandidato.BARRIO_RESIDENCIA, CampoCandidato.SECTOR, CampoCandidato.ESTRATO));
        GRUPOS_FICHA.put("Otros datos", List.of(CampoCandidato.NIVEL_EDUCATIVO, CampoCandidato.LIBRETA_MILITAR,
                CampoCandidato.RUT, CampoCandidato.PASAPORTE, CampoCandidato.VISA, CampoCandidato.EPS,
                CampoCandidato.FONDO_PENSIONES));
    }

    /** Todos los datos del candidato agrupados — el valor viene vacío ("") si no lo tiene. */
    public List<FichaCandidatoResponse.GrupoDatos> resolverFicha(Candidato candidato) {
        return GRUPOS_FICHA.entrySet().stream()
                .map(e -> new FichaCandidatoResponse.GrupoDatos(e.getKey(),
                        e.getValue().stream()
                                .map(c -> new CampoCandidatoValorResponse(c.name(), c.getEtiqueta(), valorDe(candidato, c)))
                                .toList()))
                .toList();
    }

    /** Solo la etiqueta legible, sin resolver ningún candidato — para la vista previa de la
     *  plantilla, donde todavía no hay un formulario real de fondo. */
    public String etiquetaDe(String clave) {
        CampoCandidato campo = parseCampo(clave);
        return campo == null ? clave : campo.getEtiqueta();
    }

    private List<String> parsearClaves(String camposCsv) {
        if (camposCsv == null || camposCsv.isBlank()) return CAMPOS_POR_DEFECTO;
        return Arrays.stream(camposCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private CampoCandidato parseCampo(String clave) {
        try {
            return CampoCandidato.valueOf(clave);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String valorDe(Candidato candidato, CampoCandidato campo) {
        if (candidato == null) return "";
        return switch (campo) {
            case NOMBRE_COMPLETO -> (val(candidato.getNombres()) + " " + val(candidato.getApellidos())).trim();
            case NOMBRES -> val(candidato.getNombres());
            case APELLIDOS -> val(candidato.getApellidos());
            case CEDULA -> val(candidato.getCedula());
            case TIPO_DOCUMENTO -> val(candidato.getTipoDocumento());
            case FECHA_NACIMIENTO -> formatearFecha(candidato.getFechaNacimiento());
            case LUGAR_NACIMIENTO -> val(candidato.getLugarNacimiento());
            case FECHA_EXPEDICION -> formatearFecha(candidato.getFechaExpedicion());
            case CIUDAD_EXPEDICION -> nombreCiudad(candidato.getIdCiudadExpedicion());
            case CIUDAD_NACIMIENTO -> nombreCiudad(candidato.getIdCiudadNacimiento());
            case CIUDAD_RESIDENCIA -> nombreCiudad(candidato.getIdCiudadResidencia());
            case DIRECCION_RESIDENCIA -> val(candidato.getDireccionResidencia());
            case BARRIO_RESIDENCIA -> val(candidato.getBarrioResidencia());
            case TELEFONO_FIJO -> val(candidato.getTelefonoFijo());
            case CELULAR -> val(candidato.getCelular());
            case EMAIL_PRINCIPAL -> val(candidato.getEmailPrincipal());
            case SECTOR -> val(candidato.getSector());
            case ESTRATO -> val(candidato.getEstrato());
            case LIBRETA_MILITAR -> val(candidato.getLibretaMilitar());
            case RUT -> val(candidato.getRut());
            // Estos siete no viven en Candidato — vienen de la hoja de vida que el propio
            // evaluado diligencia (HojaVidaEvaluado), no de los datos básicos que carga el
            // gestor/analista al crear el candidato.
            case ESTADO_CIVIL -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getEstadoCivil).orElse(null));
            case NIVEL_EDUCATIVO -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getNivelEducativo).orElse(null));
            case RH -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getRh).orElse(null));
            case VISA -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getVisa).orElse(null));
            case PASAPORTE -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getPasaporte).orElse(null));
            case FONDO_PENSIONES -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getFondoPensiones).orElse(null));
            case EPS -> val(hojaVidaDe(candidato).map(HojaVidaEvaluado::getEps).orElse(null));
        };
    }

    /** Resuelve UNA columna de la plantilla marcada como dato del cliente (ver
     *  FormularioColumna.campoCliente) — el cliente que solicitó el servicio, no el candidato.
     *  Clave inválida/desconocida o cliente null devuelve "". */
    public String resolverUnoCliente(Cliente cliente, String clave) {
        CampoCliente campo = parseCampoCliente(clave);
        return campo == null ? "" : valorDeCliente(cliente, campo);
    }

    /** Solo la etiqueta legible del campo de cliente, sin resolver ningún cliente real — para la
     *  vista previa de la plantilla. */
    public String etiquetaDeCliente(String clave) {
        CampoCliente campo = parseCampoCliente(clave);
        return campo == null ? clave : campo.getEtiqueta();
    }

    private CampoCliente parseCampoCliente(String clave) {
        try {
            return CampoCliente.valueOf(clave);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Valor en vivo de una columna campoServicio — un dato del estudio (FormularioColumna.campoServicio).
     *  FECHA_EMISION es la fecha en que se arma (pantalla o documento). Subproceso null o clave
     *  desconocida devuelve "". */
    public String resolverUnoServicio(ServicioSubproceso subproceso, String clave) {
        CampoServicio campo = parseCampoServicio(clave);
        if (campo == null) return "";
        if (campo == CampoServicio.FECHA_EMISION) return LocalDate.now().format(FORMATO_FECHA);
        if (subproceso == null) return "";
        Servicio servicio = subproceso.getServicio();
        return switch (campo) {
            case NUMERO_ESTUDIO -> servicio == null || servicio.getIdServicio() == null ? "" : String.valueOf(servicio.getIdServicio());
            case CLIENTE -> servicio == null || servicio.getCliente() == null ? "" : nombreEmpresaDe(servicio.getCliente());
            case CARGO -> servicio == null ? "" : val(servicio.getCargo());
            case PROFESIONAL -> nombreDe(subproceso.getUsuarioAsignado());
            case ES_OEA -> servicio != null && com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService.esOea(servicio) ? "Sí" : "No";
            case FECHA_EMISION -> "";
        };
    }

    public String etiquetaDeServicio(String clave) {
        CampoServicio campo = parseCampoServicio(clave);
        return campo == null ? clave : campo.getEtiqueta();
    }

    private CampoServicio parseCampoServicio(String clave) {
        if (clave == null) return null;
        try {
            return CampoServicio.valueOf(clave);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nombreDe(Usuario u) {
        if (u == null) return "";
        return ((u.getNombre() != null ? u.getNombre() : "") + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }

    private String valorDeCliente(Cliente cliente, CampoCliente campo) {
        if (cliente == null) return "";
        return switch (campo) {
            case NIT -> val(cliente.getNit());
            case NOMBRE_EMPRESA -> nombreEmpresaDe(cliente);
            case TELEFONO -> val(cliente.getTelefono());
        };
    }

    /** Razón social si la tiene (persona jurídica); si no, nombre comercial; si tampoco, nombre +
     *  apellido del cliente (persona natural) — siempre algo que identifique al cliente, sea cual
     *  sea su tipo. */
    private String nombreEmpresaDe(Cliente cliente) {
        if (cliente.getRazonSocial() != null && !cliente.getRazonSocial().isBlank()) return cliente.getRazonSocial();
        if (cliente.getNombreComercial() != null && !cliente.getNombreComercial().isBlank()) return cliente.getNombreComercial();
        return (val(cliente.getNombre()) + " " + val(cliente.getApellido())).trim();
    }

    private java.util.Optional<HojaVidaEvaluado> hojaVidaDe(Candidato candidato) {
        if (candidato == null || candidato.getIdCandidato() == null) return java.util.Optional.empty();
        return hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(candidato.getIdCandidato());
    }

    private String nombreCiudad(Integer idCiudad) {
        if (idCiudad == null) return "";
        return ciudadRepository.findById(idCiudad).map(Ciudad::getNombreCiudad).orElse("");
    }

    private String val(String s) {
        return s == null ? "" : s;
    }

    private String formatearFecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FORMATO_FECHA);
    }

    private String siNo(Boolean valor) {
        return Boolean.TRUE.equals(valor) ? "Sí" : "No";
    }

    /** Listas de la hoja de vida del candidato (educación, experiencia laboral, inactividades,
     *  referencias personales) — siempre disponibles como marcadores de tabla fijos
     *  (${tablas['referencias']}, etc.), sin necesidad de configurarlas en el constructor, igual
     *  que los campos sueltos de "candidato". Candidato sin hoja de vida (o sin diligenciar
     *  todavía) devuelve las cuatro listas vacías, nunca null. Cada fila incluye "id" (el id real
     *  de esa fila en la tabla del candidato) — no es una columna fija más (no aparece en
     *  COLUMNAS_TABLAS_FIJAS, así que ningún marcador de Word ni el formulario de solo lectura lo
     *  muestra), solo sirve para correlacionar con la fila de VALIDACIÓN del analista/
     *  poligrafista/visitador (ver FormularioFila.idOrigenFijo, FormularioService). */
    public Map<String, List<Map<String, String>>> resolverTablasFijas(Candidato candidato) {
        Map<String, List<Map<String, String>>> tablas = new HashMap<>();
        HojaVidaEvaluado hojaVida = hojaVidaDe(candidato).orElse(null);

        tablas.put("referencias", hojaVida == null ? List.of() : hojaVida.getReferencias().stream()
                .map(r -> Map.of(
                        "id", String.valueOf(r.getId()),
                        "nombre", val(r.getNombre()),
                        "parentesco", val(r.getParentesco()),
                        "telefono", val(r.getTelefono()),
                        "tiempo_conocimiento", val(r.getTiempoConocimiento()),
                        "confirma_no_familiar", siNo(r.getConfirmaNoFamiliar()),
                        "hablo_ultimos_30_dias", siNo(r.getHabloUltimos30Dias())))
                .toList());

        tablas.put("educacion", hojaVida == null ? List.of() : hojaVida.getEducacion().stream()
                .map(e -> Map.ofEntries(
                        Map.entry("id", String.valueOf(e.getId())),
                        Map.entry("nivel", val(e.getNivel())),
                        Map.entry("institucion", val(e.getInstitucion())),
                        Map.entry("titulo", val(e.getTitulo())),
                        Map.entry("fecha_inicio", formatearFecha(e.getFechaInicio())),
                        Map.entry("fecha_fin", formatearFecha(e.getFechaFin())),
                        Map.entry("en_curso", siNo(e.getEnCurso())),
                        Map.entry("ciudad", val(e.getCiudad())),
                        Map.entry("estado", val(e.getEstado())),
                        Map.entry("en_exterior", siNo(e.getEnExterior())),
                        Map.entry("pais_exterior", val(e.getPaisExterior()))))
                .toList());

        tablas.put("experiencia_laboral", hojaVida == null ? List.of() : hojaVida.getExperienciaLaboral().stream()
                .map(e -> {
                    Map<String, String> fila = new HashMap<>();
                    fila.put("id", String.valueOf(e.getId()));
                    fila.put("empresa", val(e.getEmpresa()));
                    fila.put("cargo", val(e.getCargo()));
                    fila.put("fecha_inicio", formatearFecha(e.getFechaInicio()));
                    fila.put("fecha_fin", formatearFecha(e.getFechaFin()));
                    fila.put("labora_actualmente", siNo(e.getLaboraActualmente()));
                    fila.put("ciudad", val(e.getCiudad()));
                    fila.put("telefono_empresa", val(e.getTelefonoEmpresa()));
                    fila.put("motivo_retiro", val(e.getMotivoRetiro()));
                    fila.put("nombre_jefe", val(e.getNombreJefe()));
                    fila.put("cargo_jefe", val(e.getCargoJefe()));
                    fila.put("tipo_contrato", val(e.getTipoContrato()));
                    fila.put("salario", e.getSalario() == null ? "" : String.valueOf(e.getSalario()));
                    fila.put("llamados_atencion", val(e.getLlamadosAtencion()));
                    fila.put("contratado_por_temporal", siNo(e.getContratadoPorTemporal()));
                    fila.put("nombre_temporal", val(e.getNombreTemporal()));
                    return fila;
                })
                .toList());

        tablas.put("inactividades", hojaVida == null ? List.of() : hojaVida.getInactividades().stream()
                .map(i -> Map.of(
                        "id", String.valueOf(i.getId()),
                        "fecha_inicio", formatearFecha(i.getFechaInicio()),
                        "fecha_fin", formatearFecha(i.getFechaFin()),
                        "dias_inactivo", i.getDiasInactivo() == null ? "" : String.valueOf(i.getDiasInactivo()),
                        "justificacion", val(i.getJustificacion()),
                        "actividad", val(i.getActividad()),
                        "quien_confirma", val(i.getQuienConfirma()),
                        "requiere_cuestionario", siNo(i.getRequiereCuestionario())))
                .toList());

        tablas.put("declaraciones", declaraciones(hojaVida).stream()
                .map(d -> Map.of(
                        "id", String.valueOf(d.id()),
                        "pregunta", d.pregunta(),
                        "respuesta", d.respuesta(),
                        "detalle", d.detalle()))
                .toList());

        return tablas;
    }

    /** Una declaración del candidato lista para mostrar: respuesta "Sí"/"No" y detalle "" si no hay. */
    public record Declaracion(Long id, String pregunta, String respuesta, String detalle) {}

    /** Lo que el candidato respondió a las preguntas de declaración de su hoja de vida (Admin →
     *  Hoja de vida del candidato → Preguntas), en el orden en que las respondió. La misma lista
     *  alimenta la ficha del formulario y la tabla de Word tablas['declaraciones']. */
    public List<Declaracion> declaraciones(Candidato candidato) {
        return declaraciones(hojaVidaDe(candidato).orElse(null));
    }

    private List<Declaracion> declaraciones(HojaVidaEvaluado hojaVida) {
        if (hojaVida == null) return List.of();
        return hojaVida.getRespuestas().stream()
                .filter(r -> r.getRespuesta() != null)
                .map(r -> new Declaracion(r.getId(), val(r.getPregunta()), siNo(r.getRespuesta()), val(r.getDetalle())))
                .toList();
    }

    /** Tablas fijas que nadie del equipo edita ni se usan como origen de una sección: son lo que
     *  el candidato declaró. Solo se consultan (ficha del formulario) y salen en Word. */
    public static final java.util.Set<String> TABLAS_SOLO_LECTURA = java.util.Set.of("declaraciones");

    /** Columnas de cada tabla fija, en el mismo orden que las arma resolverTablasFijas() — usadas
     *  por el análisis de marcadores (para no marcar las tablas fijas como "desconocidas" aunque
     *  no estén definidas en el constructor) y por la vista previa (para armar una fila de
     *  ejemplo con las claves reales, no un genérico). */
    public static final Map<String, List<String>> COLUMNAS_TABLAS_FIJAS = Map.of(
            "referencias", List.of("nombre", "parentesco", "telefono", "tiempo_conocimiento",
                    "confirma_no_familiar", "hablo_ultimos_30_dias"),
            "educacion", List.of("nivel", "institucion", "titulo", "fecha_inicio", "fecha_fin", "en_curso", "ciudad",
                    "estado", "en_exterior", "pais_exterior"),
            "experiencia_laboral", List.of("empresa", "cargo", "fecha_inicio", "fecha_fin", "labora_actualmente",
                    "ciudad", "telefono_empresa", "motivo_retiro", "nombre_jefe", "cargo_jefe",
                    "tipo_contrato", "salario", "llamados_atencion", "contratado_por_temporal", "nombre_temporal"),
            "inactividades", List.of("fecha_inicio", "fecha_fin", "dias_inactivo", "justificacion", "actividad",
                    "quien_confirma", "requiere_cuestionario"),
            "declaraciones", List.of("pregunta", "respuesta", "detalle"));

    /** Una tabla de un formulario cuyas filas nacen de una tabla fija del candidato: sección (por clave) → de cuál
     *  tabla fija y qué columna toma de cada una (clave de la columna de la plantilla → columna fija). Solo precarga:
     *  lo que el candidato registre después ya no mueve lo que el analista guardó. */
    public record Precarga(String tablaFija, Map<String, String> columnas) {}

    public static final Map<String, Precarga> PRECARGAS = Map.of(
            "referencias_declaradas", new Precarga("referencias", Map.of(
                    "rd_nombre", "nombre", "rd_parentesco", "parentesco",
                    "rd_tiempo", "tiempo_conocimiento", "rd_telefono", "telefono")));

    /** Claves de las tablas fijas — usadas por el análisis de marcadores para no marcarlas como
     *  "desconocidas" aunque no estén definidas en el constructor. */
    public static final List<String> CLAVES_TABLAS_FIJAS = List.copyOf(COLUMNAS_TABLAS_FIJAS.keySet());

    /** Etiqueta legible de cada columna fija, para mostrarla en el formulario de quien
     *  diligencia (nunca la clave técnica en snake_case) — ver
     *  FormularioService.tablaFijaResponse(). Una sola clave por nombre de columna: aunque
     *  "fecha_inicio"/"fecha_fin" se repiten en más de una tabla fija, el significado siempre es
     *  el mismo, así que comparten etiqueta. */
    public static final Map<String, String> ETIQUETAS_COLUMNAS_TABLAS_FIJAS = Map.ofEntries(
            Map.entry("nombre", "Nombre"),
            Map.entry("parentesco", "Parentesco"),
            Map.entry("telefono", "Teléfono"),
            Map.entry("tiempo_conocimiento", "Tiempo de conocimiento"),
            Map.entry("nivel", "Nivel"),
            Map.entry("institucion", "Institución"),
            Map.entry("titulo", "Título"),
            Map.entry("fecha_inicio", "Fecha de inicio"),
            Map.entry("fecha_fin", "Fecha de fin"),
            Map.entry("en_curso", "En curso"),
            Map.entry("ciudad", "Ciudad"),
            Map.entry("empresa", "Empresa"),
            Map.entry("cargo", "Cargo"),
            Map.entry("labora_actualmente", "Labora actualmente"),
            Map.entry("telefono_empresa", "Teléfono de la empresa"),
            Map.entry("motivo_retiro", "Motivo de retiro"),
            Map.entry("nombre_jefe", "Nombre del jefe"),
            Map.entry("cargo_jefe", "Cargo del jefe"),
            Map.entry("tipo_contrato", "Tipo de contrato"),
            Map.entry("salario", "Salario devengado"),
            Map.entry("llamados_atencion", "Llamados de atención"),
            Map.entry("dias_inactivo", "Días inactivo"),
            Map.entry("justificacion", "Justificación"),
            Map.entry("actividad", "Actividad realizada"),
            Map.entry("quien_confirma", "Quién o qué documento lo confirma"),
            Map.entry("estado", "Estado del estudio"),
            Map.entry("en_exterior", "Realizado en el exterior"),
            Map.entry("pais_exterior", "País del estudio"),
            Map.entry("contratado_por_temporal", "Contratado por temporal"),
            Map.entry("nombre_temporal", "Empresa de servicios temporales"),
            Map.entry("confirma_no_familiar", "Confirma que no es familiar ni convive"),
            Map.entry("hablo_ultimos_30_dias", "Hablaron en los últimos 30 días"),
            Map.entry("requiere_cuestionario", "Requiere cuestionario"),
            Map.entry("pregunta", "Pregunta"),
            Map.entry("respuesta", "Respuesta"),
            Map.entry("detalle", "Explicación"));

    /** Tipo de dato de cada columna fija — usado al sintetizar las FormularioColumna reales de
     *  una sección de origen automático (ver FormularioPlantillaService.guardar()), así el
     *  analista/poligrafista/visitador edita cada campo con el control correcto (fecha, sí/no...)
     *  en vez de un texto plano genérico. */
    public static final Map<String, TipoDatoFormulario> TIPOS_DATO_TABLAS_FIJAS = Map.ofEntries(
            Map.entry("nombre", TipoDatoFormulario.TEXTO),
            Map.entry("parentesco", TipoDatoFormulario.TEXTO),
            Map.entry("telefono", TipoDatoFormulario.TEXTO),
            Map.entry("tiempo_conocimiento", TipoDatoFormulario.TEXTO),
            Map.entry("nivel", TipoDatoFormulario.TEXTO),
            Map.entry("institucion", TipoDatoFormulario.TEXTO),
            Map.entry("titulo", TipoDatoFormulario.TEXTO),
            Map.entry("fecha_inicio", TipoDatoFormulario.FECHA),
            Map.entry("fecha_fin", TipoDatoFormulario.FECHA),
            Map.entry("en_curso", TipoDatoFormulario.BOOLEANO),
            Map.entry("ciudad", TipoDatoFormulario.TEXTO),
            Map.entry("empresa", TipoDatoFormulario.TEXTO),
            Map.entry("cargo", TipoDatoFormulario.TEXTO),
            Map.entry("labora_actualmente", TipoDatoFormulario.BOOLEANO),
            Map.entry("telefono_empresa", TipoDatoFormulario.TEXTO),
            Map.entry("motivo_retiro", TipoDatoFormulario.TEXTO),
            Map.entry("nombre_jefe", TipoDatoFormulario.TEXTO),
            Map.entry("cargo_jefe", TipoDatoFormulario.TEXTO),
            Map.entry("tipo_contrato", TipoDatoFormulario.TEXTO),
            Map.entry("salario", TipoDatoFormulario.NUMERO),
            Map.entry("llamados_atencion", TipoDatoFormulario.TEXTO),
            Map.entry("dias_inactivo", TipoDatoFormulario.NUMERO),
            Map.entry("justificacion", TipoDatoFormulario.TEXTO),
            Map.entry("actividad", TipoDatoFormulario.TEXTO),
            Map.entry("quien_confirma", TipoDatoFormulario.TEXTO),
            Map.entry("estado", TipoDatoFormulario.TEXTO),
            Map.entry("en_exterior", TipoDatoFormulario.BOOLEANO),
            Map.entry("pais_exterior", TipoDatoFormulario.TEXTO),
            Map.entry("contratado_por_temporal", TipoDatoFormulario.BOOLEANO),
            Map.entry("nombre_temporal", TipoDatoFormulario.TEXTO),
            Map.entry("confirma_no_familiar", TipoDatoFormulario.BOOLEANO),
            Map.entry("hablo_ultimos_30_dias", TipoDatoFormulario.BOOLEANO),
            Map.entry("requiere_cuestionario", TipoDatoFormulario.BOOLEANO),
            Map.entry("pregunta", TipoDatoFormulario.TEXTO),
            Map.entry("respuesta", TipoDatoFormulario.TEXTO),
            Map.entry("detalle", TipoDatoFormulario.TEXTO));
}
