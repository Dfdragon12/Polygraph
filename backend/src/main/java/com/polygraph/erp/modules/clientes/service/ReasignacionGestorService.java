package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoPersona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cuando un gestor deja de atender (lo desactivan o le cambian el rol), sus clientes pasan solos a
 * los gestores que siguen activos — antes quedaban apuntando a un gestor que ya no entra, hasta
 * que un admin los movía uno por uno desde la ficha de cada cliente.
 *
 * Reparto: cada cliente va al gestor activo con MENOS clientes en ese momento (la misma regla de
 * AuthService para los clientes que se registran solos), así la cartera del que sale no le cae
 * toda a uno. Todo lo demás (solicitudes, mensajes, novedades, links, avisos en tiempo real) sale
 * de clientes.id_gestor, así que con cambiar ese campo el gestor nuevo ya ve todo lo del cliente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasignacionGestorService {

    private static final int MAXIMO_NOMBRES_EN_NOTIFICACION = 5;

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    /** Pasa todos los clientes de este gestor a los demás gestores activos y avisa a cada cliente,
     *  a cada gestor que recibe y a los administradores. Devuelve cuántos clientes se movieron.
     *  Si no queda ningún otro gestor activo no mueve nada (los clientes conservan su gestor para
     *  no perder el vínculo) y avisa a los administradores para que lo resuelvan a mano. */
    @Transactional
    public int reasignarClientesDe(Usuario gestorSaliente) {
        List<Cliente> clientes = clienteRepository.findByIdGestor(gestorSaliente.getIdUsuario());
        if (clientes.isEmpty()) return 0;

        List<Usuario> disponibles = usuarioRepository.findByRolIn(Set.of(Rol.GESTOR)).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .filter(u -> !u.getIdUsuario().equals(gestorSaliente.getIdUsuario()))
                .toList();
        String nombreSaliente = nombreCompleto(gestorSaliente);

        if (disponibles.isEmpty()) {
            log.warn("Gestor {} sale con {} cliente(s) y no hay otro gestor activo — quedan sin reasignar",
                    gestorSaliente.getEmail(), clientes.size());
            notificacionService.crearParaAdmins("USUARIO", "Clientes sin gestor activo",
                    nombreSaliente + " ya no atiende y tenía " + clientes.size() + " cliente(s). No hay otro gestor "
                    + "activo para recibirlos: asígnalos desde la ficha de cada cliente cuando haya uno.");
            return 0;
        }

        Map<Long, Long> carga = new HashMap<>();
        disponibles.forEach(g -> carga.put(g.getIdUsuario(), clienteRepository.countByIdGestor(g.getIdUsuario())));
        Map<Usuario, List<Cliente>> recibidos = new LinkedHashMap<>();

        for (Cliente cliente : clientes) {
            Usuario destino = disponibles.stream()
                    .min(Comparator.comparingLong(g -> carga.get(g.getIdUsuario())))
                    .orElseThrow();
            cliente.setIdGestor(destino.getIdUsuario());
            carga.merge(destino.getIdUsuario(), 1L, Long::sum);
            recibidos.computeIfAbsent(destino, g -> new ArrayList<>()).add(cliente);
            avisarClienteNuevoGestor(cliente, destino);
        }
        clienteRepository.saveAll(clientes);

        recibidos.forEach((destino, lista) -> avisarGestorQueRecibe(destino, lista, nombreSaliente));

        String reparto = recibidos.entrySet().stream()
                .map(e -> nombreCompleto(e.getKey()) + " (" + e.getValue().size() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("");
        log.info("{} cliente(s) de {} reasignados: {}", clientes.size(), gestorSaliente.getEmail(), reparto);
        notificacionService.crearParaAdmins("USUARIO", "Clientes reasignados",
                "Los " + clientes.size() + " cliente(s) de " + nombreSaliente + " pasaron a: " + reparto + ".");
        return clientes.size();
    }

    /** Le avisa a todos los usuarios del cliente quién lo atiende ahora — también lo usa el cambio
     *  manual de gestor desde la ficha del cliente (ClienteAdminService.actualizar). */
    public void avisarClienteNuevoGestor(Cliente cliente, Usuario gestorNuevo) {
        notificacionService.crearParaCliente(cliente.getIdCliente(), "GESTOR_ASIGNADO",
                "Tienes un nuevo gestor",
                "Desde ahora " + nombreCompleto(gestorNuevo) + " atiende tu cuenta. Puedes escribirle desde Mensajes.");
    }

    private void avisarGestorQueRecibe(Usuario destino, List<Cliente> lista, String nombreSaliente) {
        if (lista.size() == 1) {
            Cliente unico = lista.get(0);
            notificacionService.crearParaUsuario(destino.getIdUsuario(), "CLIENTE_ASIGNADO",
                    "Te asignaron un cliente nuevo",
                    "Ahora eres el gestor de " + nombreCliente(unico) + " (lo atendía " + nombreSaliente + ").",
                    "CLIENTE", unico.getIdCliente().longValue());
            return;
        }
        String nombres = lista.stream().limit(MAXIMO_NOMBRES_EN_NOTIFICACION)
                .map(this::nombreCliente).reduce((a, b) -> a + ", " + b).orElse("");
        int resto = lista.size() - MAXIMO_NOMBRES_EN_NOTIFICACION;
        notificacionService.crearParaUsuario(destino.getIdUsuario(), "CLIENTE_ASIGNADO",
                "Te asignaron " + lista.size() + " clientes",
                "Ahora eres el gestor de " + nombres + (resto > 0 ? " y " + resto + " más" : "")
                + " (los atendía " + nombreSaliente + ").");
    }

    private String nombreCliente(Cliente c) {
        if (c.getTipoPersona() == TipoPersona.NATURAL) {
            return ((c.getNombre() != null ? c.getNombre() : "")
                    + (c.getApellido() != null ? " " + c.getApellido() : "")).trim();
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }

    private String nombreCompleto(Usuario u) {
        return ((u.getNombre() != null ? u.getNombre() : "")
                + (u.getApellido() != null ? " " + u.getApellido() : "")).trim();
    }
}
