package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.auth.service.EmailService;
import com.polygraph.erp.modules.clientes.dto.SoporteClienteGestorResponse;
import com.polygraph.erp.modules.clientes.dto.SoporteClienteResponse;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.SoporteCliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.SoporteClienteRepository;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.enums.TipoSoporte;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Documentos legales del cliente (cédula ampliada, RUT, cámara de comercio, habeas data).
 * Un "slot" por tipo — se reemplaza al resubir, no se acumula historial.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoporteClienteService {

    private final SoporteClienteRepository soporteRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlmacenamientoDocumentosService almacenamientoService;
    private final NotificacionService notificacionService;
    private final EmailService emailService;

    private static final int DIAS_AVISO_ANTICIPADO = 15;

    private record ConfigTipo(String nombre, boolean soloJuridica, Integer vigenciaDias) {}

    private static final Map<TipoSoporte, ConfigTipo> CONFIG = Map.of(
            TipoSoporte.CEDULA_150,
            new ConfigTipo("Copia del documento de identidad ampliada al 150%", false, null),
            TipoSoporte.RUT,
            new ConfigTipo("RUT actualizado", true, 365),
            TipoSoporte.CAMARA_COMERCIO,
            new ConfigTipo("Certificado de Cámara y Comercio", true, 90),
            TipoSoporte.HABEAS_DATA,
            new ConfigTipo("Autorización de Tratamiento de Datos Personales (Habeas Data)", false, null)
    );

    // Orden fijo de despliegue, igual en las 4 pantallas.
    private static final List<TipoSoporte> ORDEN = List.of(
            TipoSoporte.CEDULA_150, TipoSoporte.RUT, TipoSoporte.CAMARA_COMERCIO, TipoSoporte.HABEAS_DATA);

    // ---------- Lado CLIENTE ----------

    @Transactional(readOnly = true)
    public List<SoporteClienteResponse> listarParaCliente(Integer idCliente) {
        Cliente cliente = obtenerCliente(idCliente);
        Map<TipoSoporte, SoporteCliente> existentes = soporteRepository.findByCliente_IdCliente(idCliente).stream()
                .collect(Collectors.toMap(SoporteCliente::getTipoSoporte, s -> s));

        return tiposAplicables(cliente).stream()
                .map(tipo -> {
                    SoporteCliente existente = existentes.get(tipo);
                    return existente != null ? toResponse(existente) : slotVacio(tipo);
                })
                .toList();
    }

    @Transactional
    public SoporteClienteResponse subir(Integer idCliente, TipoSoporte tipo, MultipartFile archivo, String emailUsuario) {
        Cliente cliente = obtenerCliente(idCliente);
        if (!tiposAplicables(cliente).contains(tipo)) {
            throw new ApiException("Este documento no aplica para tu tipo de cliente", HttpStatus.BAD_REQUEST);
        }

        SoporteCliente soporte = soporteRepository.findByCliente_IdClienteAndTipoSoporte(idCliente, tipo)
                .orElseGet(() -> SoporteCliente.builder().cliente(cliente).tipoSoporte(tipo).build());

        if (soporte.getArchivoAdjunto() != null) {
            almacenamientoService.eliminar(soporte.getArchivoAdjunto());
        }

        String ruta = almacenamientoService.guardar(archivo, "clientes", carpetaCliente(cliente));
        ConfigTipo config = CONFIG.get(tipo);
        LocalDate hoy = LocalDate.now();

        soporte.setArchivoAdjunto(ruta);
        soporte.setFechaSolicitud(hoy);
        soporte.setFechaEntrega(hoy);
        soporte.setFechaVencimiento(config.vigenciaDias() != null ? hoy.plusDays(config.vigenciaDias()) : null);
        soporte.setEstado(EstadoSoporte.PENDIENTE);
        soporte.setObservaciones(null);
        soporte.setValidadoPor(null);
        soporte.setFechaValidacion(null);
        soporte.setUsuarioRegistra(emailUsuario);
        soporte = soporteRepository.save(soporte);

        log.info("Soporte '{}' cargado por {} — cliente={}", tipo, emailUsuario, idCliente);

        if (cliente.getIdGestor() != null) {
            notificacionService.crearParaUsuario(cliente.getIdGestor(), "SOPORTE",
                    "Documento para revisar",
                    nombreCliente(cliente) + " cargó \"" + config.nombre() + "\" — pendiente de validación.");
        }

        return toResponse(soporte);
    }

    @Transactional
    public void eliminarParaCliente(Integer idSoporte, Integer idClienteActor) {
        SoporteCliente soporte = obtenerSoporte(idSoporte);
        if (!soporte.getCliente().getIdCliente().equals(idClienteActor)) {
            throw new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND);
        }
        if (soporte.getArchivoAdjunto() == null) {
            throw new ApiException("Este documento aún no ha sido cargado", HttpStatus.NOT_FOUND);
        }

        almacenamientoService.eliminar(soporte.getArchivoAdjunto());
        soporte.setArchivoAdjunto(null);
        soporte.setFechaSolicitud(null);
        soporte.setFechaEntrega(null);
        soporte.setFechaVencimiento(null);
        soporte.setEstado(null);
        soporte.setObservaciones(null);
        soporte.setValidadoPor(null);
        soporte.setFechaValidacion(null);
        soporte.setUsuarioRegistra(null);
        soporteRepository.save(soporte);

        log.info("Soporte '{}' eliminado por el cliente — cliente={}", soporte.getTipoSoporte(), idClienteActor);
    }

    public record DescargaDocumento(Resource recurso, String nombreArchivo, String tipoContenido) {}

    @Transactional(readOnly = true)
    public DescargaDocumento descargarParaCliente(Integer idSoporte, Integer idClienteActor) {
        SoporteCliente soporte = obtenerSoporte(idSoporte);
        if (!soporte.getCliente().getIdCliente().equals(idClienteActor)) {
            throw new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND);
        }
        return descarga(soporte);
    }

    @Transactional(readOnly = true)
    public DescargaDocumento descargarParaRevisor(Integer idSoporte, Long idGestorActor, boolean esAdmin) {
        SoporteCliente soporte = obtenerSoporte(idSoporte);
        if (!esAdmin && !idGestorActor.equals(soporte.getCliente().getIdGestor())) {
            throw new ApiException("No tienes acceso a este documento", HttpStatus.FORBIDDEN);
        }
        return descarga(soporte);
    }

    private DescargaDocumento descarga(SoporteCliente soporte) {
        if (soporte.getArchivoAdjunto() == null) {
            throw new ApiException("Este documento aún no ha sido cargado", HttpStatus.NOT_FOUND);
        }
        Resource recurso = almacenamientoService.cargarComoResource(soporte.getArchivoAdjunto());
        String nombreArchivo = nombreDesdeRuta(soporte.getArchivoAdjunto());
        return new DescargaDocumento(recurso, nombreArchivo, almacenamientoService.tipoContenido(nombreArchivo));
    }

    // ---------- Revisión (por ahora ADMIN_POLYGRAPH; ver nota de clase) ----------

    @Transactional
    public SoporteClienteGestorResponse validar(Integer idSoporte, Long idGestorActor, boolean esAdmin,
                                                 String emailActor, boolean aprobado, String observaciones) {
        SoporteCliente soporte = obtenerSoporte(idSoporte);
        Cliente cliente = soporte.getCliente();
        if (!esAdmin && !idGestorActor.equals(cliente.getIdGestor())) {
            throw new ApiException("No tienes acceso a este documento", HttpStatus.FORBIDDEN);
        }
        if (!aprobado && (observaciones == null || observaciones.isBlank())) {
            throw new ApiException("Debes indicar el motivo del rechazo", HttpStatus.BAD_REQUEST);
        }

        Usuario actor = usuarioRepository.findByEmail(emailActor).orElse(null);
        String nombreActor = actor != null
                ? (actor.getNombre() + (actor.getApellido() != null ? " " + actor.getApellido() : "")).trim()
                : emailActor;

        soporte.setEstado(aprobado ? EstadoSoporte.VALIDADO : EstadoSoporte.RECHAZADO);
        soporte.setObservaciones(observaciones);
        soporte.setValidadoPor(nombreActor);
        soporte.setFechaValidacion(LocalDateTime.now());
        soporte = soporteRepository.save(soporte);

        String nombreTipo = CONFIG.get(soporte.getTipoSoporte()).nombre();
        String titulo = aprobado ? "Documento validado" : "Documento rechazado";
        avisarCliente(cliente, titulo,
                aprobado
                        ? "Tu documento \"" + nombreTipo + "\" fue validado."
                        : "Tu documento \"" + nombreTipo + "\" fue rechazado: " + observaciones);
        if (cliente.getIdGestor() != null) {
            notificacionService.crearParaUsuario(cliente.getIdGestor(), "SOPORTE", titulo,
                    nombreCliente(cliente) + ": \"" + nombreTipo + "\" fue " + (aprobado ? "validado" : "rechazado") + ".");
        }

        log.info("Soporte {} {} por {} — cliente={}", idSoporte, aprobado ? "validado" : "rechazado", emailActor, cliente.getIdCliente());
        return toGestorResponse(soporte, cliente);
    }

    // ---------- Vencimientos: aviso a 15 días y el día en que vence ----------

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void revisarVencimientos() {
        LocalDate hoy = LocalDate.now();
        LocalDate limiteAviso = hoy.plusDays(DIAS_AVISO_ANTICIPADO);
        List<SoporteCliente> vigentes = soporteRepository.findByEstadoAndFechaVencimientoIsNotNull(EstadoSoporte.VALIDADO);

        for (SoporteCliente soporte : vigentes) {
            try {
                if (soporte.getFechaVencimiento().isEqual(hoy)) {
                    soporte.setEstado(EstadoSoporte.VENCIDO);
                    soporteRepository.save(soporte);
                    avisarVencimiento(soporte, true);
                } else if (soporte.getFechaVencimiento().isEqual(limiteAviso)) {
                    avisarVencimiento(soporte, false);
                }
            } catch (Exception e) {
                log.error("Error revisando vencimiento del soporte {}: {}", soporte.getIdSoporte(), e.getMessage(), e);
            }
        }
    }

    private void avisarVencimiento(SoporteCliente soporte, boolean vencido) {
        Cliente cliente = soporte.getCliente();
        String nombreTipo = CONFIG.get(soporte.getTipoSoporte()).nombre();
        String titulo = vencido ? "Documento vencido" : "Documento por vencer";
        String mensaje = "\"" + nombreTipo + "\" " + (vencido ? "venció hoy" : "vence en " + DIAS_AVISO_ANTICIPADO + " días") + ".";

        avisarCliente(cliente, titulo, mensaje);
        if (cliente.getIdGestor() != null) {
            notificacionService.crearParaUsuario(cliente.getIdGestor(), "SOPORTE", titulo,
                    nombreCliente(cliente) + ": " + mensaje);
        }

        usuarioRepository.findFirstByIdClienteAndRol(cliente.getIdCliente(), Rol.ADMIN_CLIENTE)
                .filter(u -> u.getEmail() != null)
                .ifPresent(u -> emailService.enviarSoporteVencimiento(
                        u.getEmail(), nombreCliente(cliente), nombreTipo, vencido));
    }

    private void avisarCliente(Cliente cliente, String titulo, String mensaje) {
        usuarioRepository.findFirstByIdClienteAndRol(cliente.getIdCliente(), Rol.ADMIN_CLIENTE)
                .ifPresent(u -> notificacionService.crearParaUsuario(u.getIdUsuario(), "SOPORTE", titulo, mensaje));
    }

    // ---------- Comunes ----------

    private List<TipoSoporte> tiposAplicables(Cliente cliente) {
        boolean juridica = cliente.getTipoPersona() == TipoPersona.JURIDICA;
        return ORDEN.stream().filter(t -> juridica || !CONFIG.get(t).soloJuridica()).toList();
    }

    private Cliente obtenerCliente(Integer idCliente) {
        return clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
    }

    private SoporteCliente obtenerSoporte(Integer idSoporte) {
        return soporteRepository.findById(idSoporte)
                .orElseThrow(() -> new ApiException("Documento no encontrado", HttpStatus.NOT_FOUND));
    }

    private SoporteClienteResponse slotVacio(TipoSoporte tipo) {
        ConfigTipo config = CONFIG.get(tipo);
        return new SoporteClienteResponse(null, tipo.name(), config.nombre(), config.vigenciaDias(),
                null, null, null, null, null, null, null);
    }

    private SoporteClienteResponse toResponse(SoporteCliente s) {
        ConfigTipo config = CONFIG.get(s.getTipoSoporte());
        return new SoporteClienteResponse(
                s.getIdSoporte(), s.getTipoSoporte().name(), config.nombre(), config.vigenciaDias(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaEntrega(), s.getFechaVencimiento(),
                s.getArchivoAdjunto() != null ? nombreDesdeRuta(s.getArchivoAdjunto()) : null,
                s.getObservaciones(), s.getValidadoPor(), s.getFechaValidacion());
    }

    private SoporteClienteGestorResponse toGestorResponse(SoporteCliente s, Cliente cliente) {
        ConfigTipo config = CONFIG.get(s.getTipoSoporte());
        return new SoporteClienteGestorResponse(
                s.getIdSoporte(), cliente.getIdCliente(), nombreCliente(cliente),
                s.getTipoSoporte().name(), config.nombre(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getFechaEntrega(), s.getFechaVencimiento(),
                s.getArchivoAdjunto() != null ? nombreDesdeRuta(s.getArchivoAdjunto()) : null,
                s.getObservaciones(), s.getValidadoPor(), s.getFechaValidacion());
    }

    private String nombreCliente(Cliente c) {
        if (c.getRazonSocial() != null) return c.getRazonSocial();
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return (nombre + apellido).trim();
    }

    /** El NIT solo existe para persona jurídica; para persona natural (sin NIT) se usa un id de respaldo. */
    private String carpetaCliente(Cliente c) {
        return (c.getNit() != null && !c.getNit().isBlank()) ? c.getNit() : "cliente-" + c.getIdCliente();
    }

    private String nombreDesdeRuta(String ruta) {
        int i = ruta.lastIndexOf('/');
        String archivo = i >= 0 ? ruta.substring(i + 1) : ruta;
        // {uuid}_{nombreOriginal} -> nos quedamos con el nombre original
        int guion = archivo.indexOf('_');
        return guion > 0 ? archivo.substring(guion + 1) : archivo;
    }
}
