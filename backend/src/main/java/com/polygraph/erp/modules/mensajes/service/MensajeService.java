package com.polygraph.erp.modules.mensajes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.mensajes.dto.ConversacionResumenResponse;
import com.polygraph.erp.modules.mensajes.dto.MensajeResponse;
import com.polygraph.erp.modules.mensajes.dto.NoLeidosResponse;
import com.polygraph.erp.modules.mensajes.entity.MensajeClienteGestor;
import com.polygraph.erp.modules.mensajes.repository.MensajeClienteGestorRepository;
import com.polygraph.erp.shared.enums.OrigenMensaje;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensajeService {

    private final MensajeClienteGestorRepository mensajeRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.mensajes.retencion-dias:30}")
    private int retencionDias;

    // ---------- Lado CLIENTE ----------
    // Cada usuario (ADMIN_CLIENTE o ANALISTA_CLIENTE) tiene su PROPIO hilo con el gestor — ya no
    // es una sola conversación compartida por toda la empresa.

    @Transactional
    public List<MensajeResponse> listarParaCliente(Long idUsuarioCliente) {
        mensajeRepository.marcarLeidos(idUsuarioCliente, OrigenMensaje.GESTOR);
        return obtenerHilo(idUsuarioCliente);
    }

    @Transactional(readOnly = true)
    public NoLeidosResponse noLeidosParaCliente(Long idUsuarioCliente) {
        long total = mensajeRepository.countByUsuarioCliente_IdUsuarioAndOrigenAndLeidoFalseAndFechaExpiracionAfter(
                idUsuarioCliente, OrigenMensaje.GESTOR, LocalDateTime.now());
        return new NoLeidosResponse(total);
    }

    @Transactional
    public MensajeResponse enviarComoCliente(Usuario usuarioCliente, String texto) {
        Cliente cliente = clienteDe(usuarioCliente);
        if (cliente.getIdGestor() == null) {
            throw new ApiException("Tu empresa aún no tiene un gestor asignado.", HttpStatus.CONFLICT);
        }
        return guardar(cliente, usuarioCliente, usuarioCliente, OrigenMensaje.CLIENTE, texto);
    }

    // ---------- Lado GESTOR ----------

    /** Un hilo por cada usuario (ADMIN_CLIENTE + cada ANALISTA_CLIENTE) de los clientes asignados
     *  a este gestor — incluye a los que todavía no han escrito nada (para que el gestor pueda
     *  iniciar la conversación), igual que antes se veían todos los clientes asignados aunque no
     *  hubiera mensajes todavía. */
    @Transactional(readOnly = true)
    public List<ConversacionResumenResponse> misConversaciones(Long idGestor) {
        LocalDateTime ahora = LocalDateTime.now();
        return clienteRepository.findByIdGestor(idGestor).stream()
                .flatMap(c -> usuarioRepository.findByIdCliente(c.getIdCliente()).stream()
                        .filter(u -> u.getRol() == Rol.ADMIN_CLIENTE || u.getRol() == Rol.ANALISTA_CLIENTE)
                        .map(u -> resumenDe(c, u, ahora)))
                .sorted(Comparator.comparing(
                        ConversacionResumenResponse::fechaUltimoMensaje,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ConversacionResumenResponse resumenDe(Cliente cliente, Usuario usuarioCliente, LocalDateTime ahora) {
        var ultimo = mensajeRepository
                .findTopByUsuarioCliente_IdUsuarioAndFechaExpiracionAfterOrderByFechaEnvioDesc(usuarioCliente.getIdUsuario(), ahora)
                .orElse(null);
        long noLeidos = mensajeRepository.countByUsuarioCliente_IdUsuarioAndOrigenAndLeidoFalseAndFechaExpiracionAfter(
                usuarioCliente.getIdUsuario(), OrigenMensaje.CLIENTE, ahora);
        return new ConversacionResumenResponse(
                cliente.getIdCliente(),
                nombreCliente(cliente),
                usuarioCliente.getIdUsuario(),
                nombreCompleto(usuarioCliente),
                usuarioCliente.getRol().name(),
                ultimo != null ? ultimo.getMensaje() : null,
                ultimo != null ? ultimo.getFechaEnvio() : null,
                noLeidos);
    }

    @Transactional
    public List<MensajeResponse> listarParaGestor(Long idUsuarioCliente, Long idGestor) {
        validarPertenencia(idUsuarioCliente, idGestor);
        mensajeRepository.marcarLeidos(idUsuarioCliente, OrigenMensaje.CLIENTE);
        return obtenerHilo(idUsuarioCliente);
    }

    @Transactional
    public MensajeResponse enviarComoGestor(Long idUsuarioCliente, Long idGestor, String emailEmisor, String texto) {
        Usuario usuarioCliente = validarPertenencia(idUsuarioCliente, idGestor);
        Usuario emisor = obtenerUsuario(emailEmisor);
        return guardar(clienteDe(usuarioCliente), usuarioCliente, emisor, OrigenMensaje.GESTOR, texto);
    }

    // ---------- Comunes ----------

    private List<MensajeResponse> obtenerHilo(Long idUsuarioCliente) {
        return mensajeRepository
                .findByUsuarioCliente_IdUsuarioAndFechaExpiracionAfterOrderByFechaEnvioAsc(idUsuarioCliente, LocalDateTime.now())
                .stream().map(this::toResponse).toList();
    }

    private Cliente clienteDe(Usuario usuarioCliente) {
        if (usuarioCliente.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        return clienteRepository.findById(usuarioCliente.getIdCliente())
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
    }

    private Usuario validarPertenencia(Long idUsuarioCliente, Long idGestor) {
        Usuario usuarioCliente = usuarioRepository.findById(idUsuarioCliente)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Cliente cliente = clienteDe(usuarioCliente);
        if (!idGestor.equals(cliente.getIdGestor())) {
            throw new ApiException("No tienes acceso a las conversaciones de este cliente.", HttpStatus.FORBIDDEN);
        }
        return usuarioCliente;
    }

    private MensajeResponse guardar(Cliente cliente, Usuario usuarioCliente, Usuario emisor, OrigenMensaje origen, String texto) {
        LocalDateTime ahora = LocalDateTime.now();
        MensajeClienteGestor mensaje = MensajeClienteGestor.builder()
                .cliente(cliente)
                .usuarioCliente(usuarioCliente)
                .emisor(emisor)
                .origen(origen)
                .mensaje(texto.trim())
                .fechaEnvio(ahora)
                .fechaExpiracion(ahora.plusDays(retencionDias))
                .leido(false)
                .build();
        mensaje = mensajeRepository.save(mensaje);
        log.info("Mensaje {} enviado por {} (origen={}) en el hilo de usuario={} (cliente={})",
                mensaje.getId(), emisor.getEmail(), origen, usuarioCliente.getIdUsuario(), cliente.getIdCliente());
        return toResponse(mensaje);
    }

    private Usuario obtenerUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private String nombreCliente(Cliente c) {
        if (c.getRazonSocial() != null) return c.getRazonSocial();
        String nombre = c.getNombre() != null ? c.getNombre() : "";
        String apellido = c.getApellido() != null ? " " + c.getApellido() : "";
        return (nombre + apellido).trim();
    }

    private String nombreCompleto(Usuario u) {
        return (u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }

    private MensajeResponse toResponse(MensajeClienteGestor m) {
        Usuario emisor = m.getEmisor();
        String nombreEmisor = emisor != null
                ? (emisor.getNombre() + (emisor.getApellido() != null ? " " + emisor.getApellido() : "")).trim()
                : null;
        return new MensajeResponse(
                m.getId(),
                m.getOrigen().name(),
                m.getMensaje(),
                m.getFechaEnvio(),
                m.getFechaExpiracion(),
                m.getLeido(),
                nombreEmisor,
                emisor != null ? emisor.getIdUsuario() : null
        );
    }

    // ---------- Limpieza automática: los mensajes son temporales, no un historial permanente ----------

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limpiarExpirados() {
        int eliminados = mensajeRepository.deleteByFechaExpiracionBefore(LocalDateTime.now());
        if (eliminados > 0) {
            log.info("Limpieza de mensajes: {} mensajes expirados eliminados", eliminados);
        }
    }
}
