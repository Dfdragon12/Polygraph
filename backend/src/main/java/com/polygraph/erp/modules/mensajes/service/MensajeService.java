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

    @Transactional
    public List<MensajeResponse> listarParaCliente(Integer idCliente) {
        mensajeRepository.marcarLeidos(idCliente, OrigenMensaje.GESTOR);
        return obtenerHilo(idCliente);
    }

    @Transactional(readOnly = true)
    public NoLeidosResponse noLeidosParaCliente(Integer idCliente) {
        long total = mensajeRepository.countByCliente_IdClienteAndOrigenAndLeidoFalseAndFechaExpiracionAfter(
                idCliente, OrigenMensaje.GESTOR, LocalDateTime.now());
        return new NoLeidosResponse(total);
    }

    @Transactional
    public MensajeResponse enviarComoCliente(Integer idCliente, String emailEmisor, String texto) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (cliente.getIdGestor() == null) {
            throw new ApiException("Tu empresa aún no tiene un gestor asignado.", HttpStatus.CONFLICT);
        }
        Usuario emisor = obtenerUsuario(emailEmisor);
        return guardar(cliente, emisor, OrigenMensaje.CLIENTE, texto);
    }

    // ---------- Lado GESTOR ----------

    @Transactional(readOnly = true)
    public List<ConversacionResumenResponse> misConversaciones(Long idGestor) {
        LocalDateTime ahora = LocalDateTime.now();
        return clienteRepository.findByIdGestor(idGestor).stream()
                .map(c -> {
                    var ultimo = mensajeRepository
                            .findTopByCliente_IdClienteAndFechaExpiracionAfterOrderByFechaEnvioDesc(c.getIdCliente(), ahora)
                            .orElse(null);
                    long noLeidos = mensajeRepository.countByCliente_IdClienteAndOrigenAndLeidoFalseAndFechaExpiracionAfter(
                            c.getIdCliente(), OrigenMensaje.CLIENTE, ahora);
                    return new ConversacionResumenResponse(
                            c.getIdCliente(),
                            nombreCliente(c),
                            ultimo != null ? ultimo.getMensaje() : null,
                            ultimo != null ? ultimo.getFechaEnvio() : null,
                            noLeidos);
                })
                .sorted(Comparator.comparing(
                        ConversacionResumenResponse::fechaUltimoMensaje,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public List<MensajeResponse> listarParaGestor(Integer idCliente, Long idGestor) {
        validarPertenencia(idCliente, idGestor);
        mensajeRepository.marcarLeidos(idCliente, OrigenMensaje.CLIENTE);
        return obtenerHilo(idCliente);
    }

    @Transactional
    public MensajeResponse enviarComoGestor(Integer idCliente, Long idGestor, String emailEmisor, String texto) {
        Cliente cliente = validarPertenencia(idCliente, idGestor);
        Usuario emisor = obtenerUsuario(emailEmisor);
        return guardar(cliente, emisor, OrigenMensaje.GESTOR, texto);
    }

    // ---------- Comunes ----------

    private List<MensajeResponse> obtenerHilo(Integer idCliente) {
        return mensajeRepository
                .findByCliente_IdClienteAndFechaExpiracionAfterOrderByFechaEnvioAsc(idCliente, LocalDateTime.now())
                .stream().map(this::toResponse).toList();
    }

    private Cliente validarPertenencia(Integer idCliente, Long idGestor) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (!idGestor.equals(cliente.getIdGestor())) {
            throw new ApiException("No tienes acceso a las conversaciones de este cliente.", HttpStatus.FORBIDDEN);
        }
        return cliente;
    }

    private MensajeResponse guardar(Cliente cliente, Usuario emisor, OrigenMensaje origen, String texto) {
        LocalDateTime ahora = LocalDateTime.now();
        MensajeClienteGestor mensaje = MensajeClienteGestor.builder()
                .cliente(cliente)
                .emisor(emisor)
                .origen(origen)
                .mensaje(texto.trim())
                .fechaEnvio(ahora)
                .fechaExpiracion(ahora.plusDays(retencionDias))
                .leido(false)
                .build();
        mensaje = mensajeRepository.save(mensaje);
        log.info("Mensaje {} enviado por {} (origen={}) en conversación cliente={}",
                mensaje.getId(), emisor.getEmail(), origen, cliente.getIdCliente());
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
                nombreEmisor
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
