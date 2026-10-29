package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.formularios.entity.*;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioFilaRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.*;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Carga el caso de docs/antecedentes/Ejemplo_Antecedentes_ficticio.pdf como un estudio de prueba
 * (Laura Ejemplo Pérez, Empresa Ejemplo S.A.S.) con el formulario de Antecedentes ya diligenciado,
 * para ver la plantilla funcionando y descargar el Word. Datos FICTICIOS. Si ya existe (misma
 * cédula de ejemplo), vuelve a escribir el formulario y no crea nada más. Da 60,0 puntos, MEDIO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EjemploAntecedentesService {

    public static final String CEDULA_EJEMPLO = "1000000001";
    public static final String EMAIL_CLIENTE_EJEMPLO = "empresa.ejemplo@ejemplo.com";

    private final TipoProgresoRepository tipoProgresoRepository;
    private final ProcesoTipoProgresoRepository procesoTipoProgresoRepository;
    private final ProcesoRepository procesoRepository;
    private final ClienteRepository clienteRepository;
    private final CandidatoRepository candidatoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final FormularioRepository formularioRepository;
    private final FormularioSeccionRepository seccionRepository;
    private final FormularioFilaRepository filaRepository;
    private final FormularioDatoRepository datoRepository;

    public record EjemploCargado(Integer idServicio, Long idServicioSubproceso, boolean nuevo, String analista) {}

    public EjemploCargado cargar(String emailAdmin) {
        TipoProgreso antecedentes = tipoAntecedentes();
        Usuario admin = usuarioRepository.findByEmail(emailAdmin)
                .orElseThrow(() -> new ApiException("Usuario no encontrado.", HttpStatus.NOT_FOUND));
        Usuario analista = usuarioRepository.findByRolIn(List.of(Rol.ANALISTA_INTERNO)).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo())).min(Comparator.comparing(Usuario::getIdUsuario)).orElse(null);
        Usuario gestor = usuarioRepository.findByRolIn(List.of(Rol.GESTOR)).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo())).min(Comparator.comparing(Usuario::getIdUsuario)).orElse(null);

        Cliente cliente = clienteRepository.findByEmailPrincipal(EMAIL_CLIENTE_EJEMPLO).orElseGet(() -> clienteRepository.save(
                Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                        .razonSocial("Empresa Ejemplo S.A.S.").nombreComercial("Empresa Ejemplo S.A.S.").nit("900000000").dv("0")
                        .emailPrincipal(EMAIL_CLIENTE_EJEMPLO).idGestor(gestor != null ? gestor.getIdUsuario() : null).build()));
        Candidato candidato = candidatoRepository.findByCedula(CEDULA_EJEMPLO).orElseGet(() -> candidatoRepository.save(
                Candidato.builder().cedula(CEDULA_EJEMPLO).tipoDocumento("CC").nombres("Laura Ejemplo").apellidos("Pérez")
                        .fechaExpedicion(LocalDate.of(2010, 5, 10)).fechaNacimiento(LocalDate.of(1992, 2, 2))
                        .lugarNacimiento("Bogotá D.C.").direccionResidencia("Cra 00 # 00-00").celular("3000000000")
                        .emailPrincipal("correo@ejemplo.com").rut("Registrado").build()));

        ServicioSubproceso sp = servicioRepository.findAll().stream()
                .filter(s -> s.getCandidato() != null && CEDULA_EJEMPLO.equals(s.getCandidato().getCedula()))
                .flatMap(s -> subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(s.getIdServicio()).stream())
                .filter(x -> x.getTipoProgreso().getIdTipoProgreso().equals(antecedentes.getIdTipoProgreso()))
                .findFirst().orElse(null);
        boolean nuevo = sp == null;
        if (nuevo) {
            Servicio servicio = servicioRepository.save(Servicio.builder().cliente(cliente).candidato(candidato)
                    .proceso(procesoDe(antecedentes)).usuarioSolicita(admin).cargo("Asesor comercial")
                    .criticidadCargo(CriticidadCargo.ESTANDAR).fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION)
                    .observacion("Estudio de EJEMPLO (datos ficticios) — docs/antecedentes/Ejemplo_Antecedentes_ficticio.pdf").build());
            sp = subprocesoRepository.save(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(antecedentes)
                    .usuarioAsignado(analista).estado(analista != null ? EstadoSubproceso.EN_EJECUCION : EstadoSubproceso.PENDIENTE)
                    .fechaAsignacion(analista != null ? LocalDateTime.now() : null)
                    .fechaCreacion(LocalDateTime.now()).versionPlantilla(antecedentes.getVersionPlantilla()).build());
        }
        llenarFormulario(sp);
        log.info("Estudio de ejemplo de Antecedentes {} (servicio {}, subproceso {}) por {}", nuevo ? "creado" : "actualizado",
                sp.getServicio().getIdServicio(), sp.getId(), emailAdmin);
        Usuario asignado = sp.getUsuarioAsignado();
        return new EjemploCargado(sp.getServicio().getIdServicio(), sp.getId(), nuevo,
                asignado == null ? null : (asignado.getNombre() + " " + (asignado.getApellido() == null ? "" : asignado.getApellido())).trim());
    }

    /** El subservicio cuya plantilla es la de Antecedentes (tiene la sección procesos_privados). */
    private TipoProgreso tipoAntecedentes() {
        return tipoProgresoRepository.findAll().stream()
                .filter(t -> seccionRepository.findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(t.getIdTipoProgreso()).stream()
                        .anyMatch(s -> "procesos_privados".equals(s.getClave())))
                .findFirst()
                .orElseThrow(() -> new ApiException("No hay ningún servicio con la plantilla de Antecedentes.", HttpStatus.CONFLICT));
    }

    /** Un proceso que incluya Antecedentes; si ninguno, el primero que exista. */
    private Proceso procesoDe(TipoProgreso antecedentes) {
        return procesoTipoProgresoRepository.findByTipoProgreso_IdTipoProgreso(antecedentes.getIdTipoProgreso()).stream()
                .map(ProcesoTipoProgreso::getProceso).findFirst()
                .or(() -> procesoRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new ApiException("No hay procesos creados para el estudio de ejemplo.", HttpStatus.CONFLICT));
    }

    // ───────────────────────────── el formulario del ejemplo ─────────────────────────────

    private static final String HOY = "2026-10-02";

    static final Map<String, String> CAMPOS = Map.ofEntries(
            Map.entry("ev_lugar_exp", "Bogotá D.C."),
            Map.entry("ev_criticidad", "Estándar"),
            Map.entry("ev_tipo_cargo", "Comercial, ventas y atención al cliente"),
            Map.entry("ev_conduccion", "No"),
            Map.entry("ev_estado_doc", "Vigente"),
            Map.entry("ev_rut_actividad", "Asalariado"),
            Map.entry("dec1_respuesta", "Sí"),
            Map.entry("dec1_detalle", "Denuncia por hurto en 2016, archivada"),
            Map.entry("dec1_verificacion", "Dos procesos inactivos en bases privadas"),
            Map.entry("dec1_coincide", "Sí"),
            Map.entry("dec2_respuesta", "No"),
            Map.entry("dec2_detalle", "—"),
            Map.entry("dec2_verificacion", "Policía: sin asuntos pendientes"),
            Map.entry("dec2_coincide", "Sí"),
            Map.entry("dec3_respuesta", "No"),
            Map.entry("dec3_detalle", "—"),
            Map.entry("dec3_verificacion", "Procuraduría y Contraloría: sin registros"),
            Map.entry("dec3_coincide", "Sí"),
            Map.entry("simit_multas", "2 pendientes de pago"),
            Map.entry("simit_comparendos", "No registra"),
            Map.entry("simit_acuerdos", "Ninguno"),
            Map.entry("simit_inmov", "Ninguna"),
            Map.entry("simit_ref", "LEG-17"),
            Map.entry("rec_dir_coincide", "Sí"),
            Map.entry("rec_residencial", "Estable"),
            Map.entry("concepto", "No registra hallazgos críticos ni requerimientos vigentes. Registra dos medidas correctivas y dos "
                    + "procesos penales inactivos como indiciado (hurto, 2016; violencia intrafamiliar, 2014), sin condena conocida. "
                    + "Las multas de tránsito no suman porque el cargo no implica conducción."),
            Map.entry("analista_fecha", HOY));

    static final Map<String, List<Map<String, String>>> TABLAS = Map.of(
            "informativos", List.of(fila("i_situacion", "Multas de tránsito (cargo sin conducción)",
                    "i_detalle", "SIMIT: 2 multas pendientes de pago.", "i_tratamiento", "No suman. Remitir a análisis financiero.")),
            "listas_nacionales", List.of(
                    nacional("Registraduría", "Sin novedad", "—", "—", "Cédula vigente"),
                    nacional("Procuraduría", "Sin novedad", "—", "—", ""),
                    nacional("Policía Nacional", "Sin novedad", "—", "—", "No tiene asuntos pendientes"),
                    nacional("INPEC", "Sin novedad", "—", "—", ""),
                    nacional("Medidas correctivas (RNMC)", "Con hallazgo", "LEG-18", "Vigente", "Multa tipo 2, espacio público (2021)"),
                    nacional("Medidas correctivas (RNMC)", "Con hallazgo", "LEG-18", "En proceso", "Multa tipo 1, transporte masivo (2019)"),
                    nacional("Rama Judicial", "Sin novedad", "—", "—", "Sin procesos"),
                    nacional("Contraloría", "Sin novedad", "—", "—", ""),
                    nacional("RUES", "Sin novedad", "—", "—", "")),
            "listas_internacionales", List.of(
                    internacional("OFAC – Lista Clinton", ""),
                    internacional("Consejo de Seguridad ONU (vinculante)", ""),
                    internacional("Interpol", ""),
                    internacional("Noticias criminales", ""),
                    internacional("PEP nacionales", "No es PEP")),
            "runt", List.of(fila("runt_categoria", "No inscrito", "runt_estado", "—", "runt_categoria_ok", "—", "runt_ref", "—")),
            "direcciones", List.of(fila("d_direccion", "Cra 00 # 00-00", "d_tipo", "RES", "d_ciudad", "Bogotá D.C.",
                    "d_desde", "02/2021", "d_ultimo", "08/2026", "d_coincide", "Sí")),
            "telefonos", List.of(fila("t_numero", "300 000 0000", "t_tipo", "CORR", "t_ciudad", "Bogotá D.C.", "t_ultimo", "08/2026",
                    "t_coincide", "Sí")),
            "correos", List.of(fila("e_correo", "correo@ejemplo.com", "e_ultimo", "07/2026", "e_coincide", "Sí")),
            "procesos_privados", List.of(
                    fila("pp_calidad", "Investigado / indiciado", "pp_delito", "Hurto (art. 239)", "pp_despacho", "Fiscalía 106",
                            "pp_fecha", "2016-12-01", "pp_etapa", "Indagación", "pp_estado", "Inactivo", "pp_relacion", "No",
                            "pp_calificacion", "LEG-19", "pp_obs", "Registra proceso por hurto (2016), en calidad de indiciado, inactivo."),
                    fila("pp_calidad", "Investigado / indiciado", "pp_delito", "Violencia intrafamiliar (art. 229)", "pp_despacho", "Fiscalía 227",
                            "pp_fecha", "2014-02-01", "pp_etapa", "Indagación", "pp_estado", "Inactivo", "pp_relacion", "No",
                            "pp_calificacion", "LEG-19", "pp_obs", "Registra proceso por violencia intrafamiliar (2014), en calidad de indiciado, inactivo.")));

    private static Map<String, String> nacional(String fuente, String resultado, String ref, String estado, String obs) {
        return fila("ln_fuente", fuente, "ln_resultado", resultado, "ln_ref", ref, "ln_estado", estado, "ln_fecha", HOY,
                "ln_soporte", "Adjunto", "ln_observacion", obs);
    }

    private static Map<String, String> internacional(String lista, String obs) {
        return fila("li_lista", lista, "li_resultado", "Sin novedad", "li_coincidencia", "—", "li_ref", "—", "li_fecha", HOY,
                "li_soporte", "Adjunto", "li_observacion", obs);
    }

    private static Map<String, String> fila(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Borra lo que tenga el formulario del ejemplo y lo escribe con los datos del PDF. Cada valor de
     *  lista se revisa contra las opciones de la plantilla: si no está, falla (nunca guarda basura). */
    private void llenarFormulario(ServicioSubproceso sp) {
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(sp.getId()).orElseGet(() -> formularioRepository.save(
                Formulario.builder().servicioSubproceso(sp).fechaCreacion(LocalDateTime.now()).build()));
        datoRepository.deleteAll(datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario()));
        filaRepository.deleteAll(filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario()));
        datoRepository.flush();

        List<FormularioSeccion> secciones = seccionRepository
                .findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(sp.getTipoProgreso().getIdTipoProgreso());
        List<FormularioDato> datos = new ArrayList<>();
        for (FormularioSeccion s : secciones) {
            Map<String, FormularioColumna> columnas = new HashMap<>();
            s.getColumnas().stream().filter(c -> Boolean.TRUE.equals(c.getActivo()) && !c.esAutomatica() && c.getClave() != null)
                    .forEach(c -> columnas.put(c.getClave(), c));
            if (s.getTipo() == TipoSeccionFormulario.CAMPOS) {
                columnas.forEach((clave, c) -> {
                    String v = CAMPOS.getOrDefault(clave, c.getValorDefecto());
                    if (v != null) datos.add(dato(formulario, c, null, v));
                });
            } else if (TABLAS.containsKey(s.getClave())) {
                int orden = 0;
                for (Map<String, String> valores : TABLAS.get(s.getClave())) {
                    String etiqueta = valores.values().iterator().next();
                    FormularioFila f = filaRepository.save(FormularioFila.builder().formulario(formulario).seccion(s)
                            .etiqueta(etiqueta.length() > 150 ? etiqueta.substring(0, 150) : etiqueta).orden(orden++).build());
                    valores.forEach((clave, v) -> {
                        FormularioColumna c = columnas.get(clave);
                        if (c == null) throw new IllegalStateException("La plantilla no tiene la columna " + clave);
                        if (!v.isEmpty()) datos.add(dato(formulario, c, f, v));
                    });
                }
            }
        }
        datoRepository.saveAll(datos);
        formulario.setFechaActualizacion(LocalDateTime.now());
        formularioRepository.save(formulario);
    }

    private static FormularioDato dato(Formulario formulario, FormularioColumna c, FormularioFila fila, String valor) {
        if (c.getTipoDato() == TipoDatoFormulario.LISTA && !OpcionesLista.leer(c.getOpciones()).contains(valor)) {
            throw new IllegalStateException("\"" + valor + "\" no es una opción de " + c.getClave());
        }
        return FormularioDato.builder().formulario(formulario).columna(c).fila(fila).valor(valor).build();
    }
}
