package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.clientes.entity.AprobacionPospago;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.entity.ExpedientePospago;
import com.polygraph.erp.modules.clientes.entity.SoporteCliente;
import com.polygraph.erp.modules.clientes.repository.AprobacionPospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.ExpedientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.SoporteClienteRepository;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.enums.TipoSoporte;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Alta controlada del cliente pospago (TP-13). La lista de verificación se arma SIN pedir los documentos legales otra vez: RUT, Cámara de Comercio y
 * documento del representante se leen del módulo Documentos (cumplen solo si están APROBADOS ahí). El resto (empleados, procesos, estudio de crédito,
 * contrato, cupo y corte) se reúne en {@link ExpedientePospago}.
 * <p>
 * Excepción: solo empleados y procesos admiten excepción (cliente estratégico), solo la aprueba el administrador principal y exige motivo escrito.
 * Los documentos de la empresa y el estudio de crédito no tienen excepción.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AltaPospagoService {

    public static final String PERSONA_JURIDICA = "PERSONA_JURIDICA";
    public static final String EMPLEADOS = "EMPLEADOS";
    public static final String PROCESOS = "PROCESOS";
    public static final String RUT = "RUT";
    public static final String CAMARA_COMERCIO = "CAMARA_COMERCIO";
    public static final String DOCUMENTO_REPRESENTANTE = "DOCUMENTO_REPRESENTANTE";
    public static final String ESTUDIO_CREDITO = "ESTUDIO_CREDITO";
    public static final String CONTRATO = "CONTRATO";
    public static final String CUPO_CORTE = "CUPO_CORTE";

    /** Soportes que se adjuntan en la pantalla (los documentos legales NO: esos van en Documentos). */
    public enum SoportePospago { EMPLEADOS, PROCESOS, ESTUDIO_CREDITO, CONTRATO }

    private final ClienteRepository clienteRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final SoporteClienteRepository soporteRepository;
    private final ExpedientePospagoRepository expedienteRepository;
    private final AprobacionPospagoRepository aprobacionRepository;
    private final ParametrosTiendaService parametros;
    private final AlmacenamientoDocumentosService almacenamiento;
    private final NotificacionService notificacionService;
    private final Clock clock;

    /** `enlace` = DOCUMENTOS cuando se resuelve en la ventana «Documentos legales» del cliente. */
    public record Requisito(String codigo, String titulo, boolean cumple, boolean exceptuable, String detalle, String falta, String enlace) {}

    public record Verificacion(Integer idCliente, String nombre, String tipoPersona, String tipoCliente, boolean aplica, List<Requisito> requisitos,
                               boolean sinFaltantes, boolean aprobable, boolean requiereExcepcion, String mensajeBloqueo, Expediente expediente) {}

    public record Expediente(Integer empleadosDeclarados, String soporteEmpleados, Integer procesosProyectados, String soporteProcesos,
                             String estudioCreditoArchivo, LocalDate estudioCreditoFecha, String estudioCreditoResultado, String estudioCreditoPor,
                             String contratoArchivo, BigDecimal cupoSolicitado, Integer diaCorte, int minimoEmpleados, int minimoProcesos, int diasCamara) {}

    public record ExpedienteRequest(Integer empleadosDeclarados, Integer procesosProyectados, LocalDate estudioCreditoFecha, String estudioCreditoResultado,
                                    String estudioCreditoPor, BigDecimal cupoSolicitado, Integer diaCorte) {}

    public record FilaReporte(Integer idCliente, String nombre, String nit, BigDecimal cupo, String estadoMora, List<String> faltantes, boolean cumpleTodo) {}

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA));
    }

    // ───────────────────────── verificación ─────────────────────────

    @Transactional(readOnly = true)
    public Verificacion verificar(Integer idCliente) {
        Cliente c = cliente(idCliente);
        ExpedientePospago e = expedienteRepository.findById(idCliente).orElse(null);
        List<Requisito> reqs = requisitos(c, e);
        List<String> faltas = reqs.stream().filter(r -> !r.cumple()).map(Requisito::falta).toList();
        boolean duros = reqs.stream().anyMatch(r -> !r.cumple() && !r.exceptuable());
        boolean soloExceptuables = !duros && !faltas.isEmpty();
        boolean aplica = c.getTipoPersona() == TipoPersona.JURIDICA;
        String bloqueo = faltas.isEmpty() ? null : String.join(" · ", faltas);
        return new Verificacion(idCliente, nombre(c), c.getTipoPersona() != null ? c.getTipoPersona().name() : null, c.getTipoCliente().name(), aplica, reqs,
                faltas.isEmpty(), !duros, soloExceptuables, bloqueo, expediente(e));
    }

    private Expediente expediente(ExpedientePospago e) {
        int minE = parametros.entero("pospagoEmpleadosMinimos"), minP = parametros.entero("pospagoProcesosMinimosMes"),
                dias = parametros.entero("diasVigenciaCamaraComercio");
        if (e == null) return new Expediente(null, null, null, null, null, null, null, null, null, null, null, minE, minP, dias);
        return new Expediente(e.getEmpleadosDeclarados(), nombreArchivo(e.getSoporteEmpleados()), e.getProcesosProyectados(), nombreArchivo(e.getSoporteProcesos()),
                nombreArchivo(e.getEstudioCreditoArchivo()), e.getEstudioCreditoFecha(), e.getEstudioCreditoResultado(), e.getEstudioCreditoPor(),
                nombreArchivo(e.getContratoArchivo()), e.getCupoSolicitado(), e.getDiaCorte(), minE, minP, dias);
    }

    List<Requisito> requisitos(Cliente c, ExpedientePospago e) {
        int minE = parametros.entero("pospagoEmpleadosMinimos"), minP = parametros.entero("pospagoProcesosMinimosMes"),
                diasCamara = parametros.entero("diasVigenciaCamaraComercio");
        LocalDate hoy = ahora().toLocalDate();
        Map<TipoSoporte, SoporteCliente> docs = new EnumMap<>(TipoSoporte.class);
        for (SoporteCliente s : soporteRepository.findByCliente_IdCliente(c.getIdCliente())) docs.put(s.getTipoSoporte(), s);
        List<Requisito> r = new ArrayList<>();

        boolean juridica = c.getTipoPersona() == TipoPersona.JURIDICA && c.getNit() != null && !c.getNit().isBlank();
        r.add(new Requisito(PERSONA_JURIDICA, "Persona jurídica", juridica, false,
                juridica ? "Persona jurídica con NIT " + c.getNit() : "El pospago es solo para personas jurídicas con NIT",
                "Falta que el cliente sea persona jurídica con NIT", null));

        Integer emp = e != null ? e.getEmpleadosDeclarados() : null;
        boolean empOk = emp != null && emp > minE && e.getSoporteEmpleados() != null;
        r.add(new Requisito(EMPLEADOS, "Más de " + minE + " empleados", empOk, true,
                emp == null ? "Sin declarar" : emp + " empleados declarados" + (e.getSoporteEmpleados() == null ? ", sin soporte" : " con soporte"),
                "Faltan los empleados: más de " + minE + " declarados y su soporte (PILA o certificación del revisor fiscal)", null));

        Integer proc = e != null ? e.getProcesosProyectados() : null;
        boolean procOk = proc != null && proc > minP && e.getSoporteProcesos() != null;
        r.add(new Requisito(PROCESOS, "Más de " + minP + " procesos al mes", procOk, true,
                proc == null ? "Sin declarar" : proc + " procesos al mes proyectados" + (e.getSoporteProcesos() == null ? ", sin soporte" : " con soporte"),
                "Faltan los procesos al mes: más de " + minP + " proyectados y su soporte (propuesta aceptada o proyección del contrato)", null));

        r.add(documento(RUT, "RUT", "el RUT", docs.get(TipoSoporte.RUT), null, hoy));
        r.add(documento(CAMARA_COMERCIO, "Cámara de Comercio", "la Cámara de Comercio", docs.get(TipoSoporte.CAMARA_COMERCIO), diasCamara, hoy));
        r.add(documento(DOCUMENTO_REPRESENTANTE, "Documento de identidad del representante legal", "el documento de identidad del representante legal",
                docs.get(TipoSoporte.CEDULA_150), null, hoy));

        String res = e != null ? e.getEstudioCreditoResultado() : null;
        boolean estudioCompleto = e != null && e.getEstudioCreditoArchivo() != null && e.getEstudioCreditoFecha() != null && res != null
                && e.getEstudioCreditoPor() != null && !e.getEstudioCreditoPor().isBlank();
        boolean estudioOk = estudioCompleto && "APROBADO".equals(res);
        String faltaEstudio = estudioCompleto && "RECHAZADO".equals(res) ? "El estudio de crédito fue rechazado" : "Falta el estudio de crédito";
        r.add(new Requisito(ESTUDIO_CREDITO, "Estudio de crédito", estudioOk, false,
                estudioCompleto ? "Resultado " + res + " del " + e.getEstudioCreditoFecha() + ", hecho por " + e.getEstudioCreditoPor() : "Sin registrar",
                faltaEstudio, null));

        boolean contrato = e != null && e.getContratoArchivo() != null;
        r.add(new Requisito(CONTRATO, "Contrato o propuesta aceptada", contrato, false, contrato ? "Adjunto" : "Sin adjuntar",
                "Falta el contrato o la propuesta aceptada", null));

        BigDecimal cupo; Integer corte;
        ClientePospago existente = c.getTipoCliente() == TipoCliente.POSPAGO ? clientePospagoRepository.findByIdCliente(c.getIdCliente()).orElse(null) : null;
        if (existente != null) { cupo = existente.getLimiteCredito(); corte = existente.getDiaCorte(); }
        else { cupo = e != null ? e.getCupoSolicitado() : null; corte = e != null ? e.getDiaCorte() : null; }
        boolean cupoOk = cupo != null && cupo.signum() > 0 && corte != null && corte >= 1 && corte <= 28;
        r.add(new Requisito(CUPO_CORTE, "Cupo y día de corte", cupoOk, false,
                cupoOk ? "Cupo " + cupo.toPlainString() + ", corte el día " + corte : "Sin definir",
                cupo == null || cupo.signum() <= 0 ? "Falta el cupo" + (corte == null ? " y el día de corte" : "") : "Falta el día de corte (1 a 28)", null));
        return r;
    }

    /** Un documento legal: cumple solo si está APROBADO en Documentos (y, para la Cámara, con máximo `diasMaximos` días). Nunca se carga aquí. */
    private Requisito documento(String codigo, String titulo, String conArticulo, SoporteCliente s, Integer diasMaximos, LocalDate hoy) {
        String estado;
        boolean ok = false;
        if (s == null || s.getArchivoAdjunto() == null) estado = "Sin cargar";
        else if (s.getEstado() == EstadoSoporte.RECHAZADO) estado = "Rechazado" + (s.getObservaciones() != null ? ": " + s.getObservaciones() : "");
        else if (s.getEstado() == EstadoSoporte.VENCIDO) estado = "Vencido";
        else if (s.getEstado() != EstadoSoporte.VALIDADO) estado = "Pendiente de aprobación";
        else if (diasMaximos != null && (s.getFechaEntrega() == null || s.getFechaEntrega().plusDays(diasMaximos).isBefore(hoy))) {
            estado = "Aprobado pero con más de " + diasMaximos + " días de expedido";
        } else { estado = "Aprobado"; ok = true; }
        String falta = "Falta " + conArticulo + " aprobado en Documentos (" + estado.toLowerCase() + ")";
        return new Requisito(codigo, titulo, ok, false, estado, falta, ok ? null : "DOCUMENTOS");
    }

    // ───────────────────────── expediente ─────────────────────────

    @Transactional
    public Verificacion guardarExpediente(Integer idCliente, ExpedienteRequest req, String usuario) {
        exigirActivo();
        Cliente c = convertible(idCliente);
        ExpedientePospago e = expedienteRepository.findById(idCliente).orElseGet(() -> ExpedientePospago.builder().idCliente(c.getIdCliente()).build());
        if (req.empleadosDeclarados() != null) {
            if (req.empleadosDeclarados() < 0) throw new ApiException("Los empleados no pueden ser negativos", HttpStatus.BAD_REQUEST);
            e.setEmpleadosDeclarados(req.empleadosDeclarados());
        }
        if (req.procesosProyectados() != null) {
            if (req.procesosProyectados() < 0) throw new ApiException("Los procesos al mes no pueden ser negativos", HttpStatus.BAD_REQUEST);
            e.setProcesosProyectados(req.procesosProyectados());
        }
        if (req.estudioCreditoResultado() != null) {
            String r = req.estudioCreditoResultado().trim().toUpperCase();
            if (!r.equals("APROBADO") && !r.equals("RECHAZADO")) throw new ApiException("El resultado del estudio de crédito es Aprobado o Rechazado", HttpStatus.BAD_REQUEST);
            e.setEstudioCreditoResultado(r);
        }
        if (req.estudioCreditoFecha() != null) e.setEstudioCreditoFecha(req.estudioCreditoFecha());
        if (req.estudioCreditoPor() != null) e.setEstudioCreditoPor(req.estudioCreditoPor().isBlank() ? null : req.estudioCreditoPor().trim());
        if (req.cupoSolicitado() != null) {
            if (req.cupoSolicitado().signum() <= 0) throw new ApiException("El cupo debe ser mayor que 0", HttpStatus.BAD_REQUEST);
            e.setCupoSolicitado(req.cupoSolicitado());
        }
        if (req.diaCorte() != null) {
            if (req.diaCorte() < 1 || req.diaCorte() > 28) throw new ApiException("El día de corte debe estar entre 1 y 28", HttpStatus.BAD_REQUEST);
            e.setDiaCorte(req.diaCorte());
        }
        e.setActualizadoEn(ahora());
        e.setActualizadoPor(usuario);
        expedienteRepository.save(e);
        return verificar(idCliente);
    }

    @Transactional
    public Verificacion subirSoporte(Integer idCliente, SoportePospago tipo, MultipartFile archivo, String usuario) {
        exigirActivo();
        Cliente c = convertible(idCliente);
        ExpedientePospago e = expedienteRepository.findById(idCliente).orElseGet(() -> ExpedientePospago.builder().idCliente(c.getIdCliente()).build());
        String carpeta = (c.getNit() != null && !c.getNit().isBlank() ? c.getNit() : "cliente-" + idCliente) + "/pospago";
        String anterior = rutaDe(e, tipo);
        String ruta = almacenamiento.guardar(archivo, "clientes", carpeta);
        switch (tipo) {
            case EMPLEADOS -> e.setSoporteEmpleados(ruta);
            case PROCESOS -> e.setSoporteProcesos(ruta);
            case ESTUDIO_CREDITO -> e.setEstudioCreditoArchivo(ruta);
            case CONTRATO -> e.setContratoArchivo(ruta);
        }
        e.setActualizadoEn(ahora());
        e.setActualizadoPor(usuario);
        expedienteRepository.save(e);
        if (anterior != null) almacenamiento.eliminar(anterior);
        return verificar(idCliente);
    }

    public record Descarga(Resource recurso, String nombre, String tipoContenido) {}

    @Transactional(readOnly = true)
    public Descarga descargar(Integer idCliente, SoportePospago tipo) {
        ExpedientePospago e = expedienteRepository.findById(idCliente).orElseThrow(() -> new ApiException("No hay soportes cargados", HttpStatus.NOT_FOUND));
        String ruta = rutaDe(e, tipo);
        if (ruta == null) throw new ApiException("Este soporte aún no se ha cargado", HttpStatus.NOT_FOUND);
        String nombre = nombreArchivo(ruta);
        return new Descarga(almacenamiento.cargarComoResource(ruta), nombre, almacenamiento.tipoContenido(nombre));
    }

    private static String rutaDe(ExpedientePospago e, SoportePospago tipo) {
        return switch (tipo) {
            case EMPLEADOS -> e.getSoporteEmpleados();
            case PROCESOS -> e.getSoporteProcesos();
            case ESTUDIO_CREDITO -> e.getEstudioCreditoArchivo();
            case CONTRATO -> e.getContratoArchivo();
        };
    }

    // ───────────────────────── aprobación ─────────────────────────

    /**
     * Aprueba el pospago. Sin faltantes: aprueba. Con faltantes solo de empleados/procesos: exige `excepcion` + motivo escrito y que quien aprueba sea el
     * administrador principal. Cualquier otro faltante (documentos de la empresa, estudio de crédito, contrato, cupo) no tiene excepción.
     */
    @Transactional
    public Verificacion aprobar(Integer idCliente, boolean excepcion, String motivo, String emailActor, Rol rolActor) {
        exigirActivo();
        Cliente c = convertible(idCliente);
        ExpedientePospago e = expedienteRepository.findById(idCliente).orElse(null);
        List<Requisito> reqs = requisitos(c, e);
        List<Requisito> duros = reqs.stream().filter(r -> !r.cumple() && !r.exceptuable()).toList();
        if (!duros.isEmpty()) {
            throw new ApiException("No se puede aprobar: " + duros.stream().map(Requisito::falta).collect(Collectors.joining(" · ")), HttpStatus.BAD_REQUEST);
        }
        List<Requisito> exceptuables = reqs.stream().filter(r -> !r.cumple()).toList();
        boolean conExcepcion = !exceptuables.isEmpty();
        if (conExcepcion) {
            if (rolActor != Rol.ADMIN_POLYGRAPH) {
                throw new ApiException("Solo el administrador principal puede aprobar una excepción.", HttpStatus.FORBIDDEN);
            }
            if (!excepcion) {
                throw new ApiException("No se puede aprobar: " + exceptuables.stream().map(Requisito::falta).collect(Collectors.joining(" · "))
                        + ". Si es un cliente estratégico, apruébalo como excepción con un motivo.", HttpStatus.BAD_REQUEST);
            }
            if (motivo == null || motivo.isBlank()) {
                throw new ApiException("El motivo de la excepción es obligatorio.", HttpStatus.BAD_REQUEST);
            }
        }

        BigDecimal cupo = e.getCupoSolicitado();
        Integer corte = e.getDiaCorte();
        c.setTipoCliente(TipoCliente.POSPAGO);
        ClientePospago pospago = clientePospagoRepository.findByIdCliente(idCliente).orElseGet(() -> ClientePospago.builder().cliente(c).build());
        pospago.setLimiteCredito(cupo);
        pospago.setCreditoDisponible(cupo);
        pospago.setEstadoMora(EstadoMora.NORMAL);
        pospago.setRequiereAprobacion(false);   // el estudio de crédito ya quedó registrado y aprobado en el expediente
        pospago.setDiaCorte(corte);
        pospago.setUltimaRevisionCredito(ahora().toLocalDate());
        clientePospagoRepository.save(pospago);
        clienteRepository.save(c);

        String copia = reqs.stream().map(r -> r.codigo() + "=" + (r.cumple() ? "CUMPLE" : "FALTA") + " (" + r.detalle() + ")").collect(Collectors.joining("; "));
        aprobacionRepository.save(AprobacionPospago.builder().idCliente(idCliente).aprobadoPor(emailActor).aprobadoEn(ahora()).cupo(cupo).diaCorte(corte)
                .excepcion(conExcepcion).motivoExcepcion(conExcepcion ? motivo.trim() : null).requisitos(copia).build());
        log.info("Pospago aprobado: cliente={}, cupo={}, corte={}, excepcion={}, por={}", idCliente, cupo, corte, conExcepcion, emailActor);

        notificacionService.crearParaCliente(idCliente, "POSPAGO_APROBADO", "Tu cuenta pospago fue aprobada",
                "Ya puedes solicitar servicios con tu cupo de crédito de " + cupo.toPlainString() + ".");
        if (c.getIdGestor() != null) {
            notificacionService.crearParaUsuario(c.getIdGestor(), "CLIENTE", "Cliente aprobado como pospago",
                    nombre(c) + " fue aprobado como pospago con cupo de " + cupo.toPlainString() + (conExcepcion ? " (por excepción)." : "."));
        }
        return verificar(idCliente);
    }

    // ───────────────────────── reporte de los pospago actuales ─────────────────────────

    /** Cada cliente pospago y lo que le falta de la lista. SOLO reporta: nunca quita ni cambia un cupo. */
    @Transactional(readOnly = true)
    public List<FilaReporte> reporte() {
        return clienteRepository.findAll().stream()
                .filter(c -> c.getTipoCliente() == TipoCliente.POSPAGO)
                .map(c -> {
                    List<Requisito> reqs = requisitos(c, expedienteRepository.findById(c.getIdCliente()).orElse(null));
                    ClientePospago p = clientePospagoRepository.findByIdCliente(c.getIdCliente()).orElse(null);
                    List<String> faltas = reqs.stream().filter(r -> !r.cumple()).map(Requisito::falta).toList();
                    return new FilaReporte(c.getIdCliente(), nombre(c), c.getNit(), p != null ? p.getLimiteCredito() : null,
                            p != null && p.getEstadoMora() != null ? p.getEstadoMora().name() : null, faltas, faltas.isEmpty());
                })
                .sorted(java.util.Comparator.comparing((FilaReporte f) -> f.faltantes().isEmpty()).thenComparing(FilaReporte::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ───────────────────────── auxiliares ─────────────────────────

    private void exigirActivo() {
        if (!parametros.activo(ParametrosTiendaService.ALTA_POSPAGO_CONTROLADA)) {
            throw new ApiException("El alta controlada de pospago no está habilitada", HttpStatus.CONFLICT);
        }
    }

    private Cliente cliente(Integer id) {
        return clienteRepository.findById(id).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
    }

    /** Solo personas jurídicas que hoy son prepago se convierten. */
    private Cliente convertible(Integer id) {
        Cliente c = cliente(id);
        if (c.getTipoPersona() != TipoPersona.JURIDICA) throw new ApiException("Solo las personas jurídicas pueden pasar a pospago", HttpStatus.BAD_REQUEST);
        if (c.getTipoCliente() != TipoCliente.PREPAGO) throw new ApiException("Este cliente ya es pospago", HttpStatus.CONFLICT);
        return c;
    }

    private static String nombre(Cliente c) {
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        return ((c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "")).trim();
    }

    private static String nombreArchivo(String ruta) {
        if (ruta == null) return null;
        int i = ruta.lastIndexOf('/');
        String a = i >= 0 ? ruta.substring(i + 1) : ruta;
        int g = a.indexOf('_');
        return g > 0 ? a.substring(g + 1) : a;
    }
}
