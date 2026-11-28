package com.polygraph.erp.modules.tiemporeal;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Conexiones abiertas de "avisos en tiempo real" (Server-Sent Events). Cada pantalla abierta de
 *  la plataforma (usuarios internos, clientes y el portal del candidato) mantiene una conexión; cuando
 *  algo de un servicio cambia, se le avisa SOLO a quien le incumbe y la pantalla recarga sus datos.
 *
 *  El aviso nunca lleva datos del servicio, solo sus números (idServicio): cada pantalla vuelve a
 *  pedir lo que le toca por los endpoints de siempre, con sus permisos de siempre. */
@Slf4j
@Component
public class CanalCambios {

    /** Media hora: luego el navegador reconecta solo. */
    private static final long DURACION_MS = 30 * 60 * 1000L;
    private static final int MAX_POR_USUARIO = 6;
    private static final int MAX_TOTAL = 2000;

    /** Quién está escuchando. Interno/cliente: idUsuario + rol + idCliente. Candidato: idServicio. */
    record Suscripcion(SseEmitter emisor, Long idUsuario, Rol rol, Integer idCliente, Integer idServicioCandidato, long desde) {}

    /** Un servicio que cambió y a quién le incumbe. */
    public record Cambio(Integer idServicio, Long idGestor, Integer idCliente, Set<Long> usuarios) {}

    private final Map<String, Suscripcion> suscripciones = new ConcurrentHashMap<>();

    public SseEmitter suscribirUsuario(Long idUsuario, Rol rol, Integer idCliente) {
        return registrar(new Suscripcion(new SseEmitter(DURACION_MS), idUsuario, rol, idCliente, null, System.currentTimeMillis()));
    }

    public SseEmitter suscribirCandidato(Integer idServicio) {
        return registrar(new Suscripcion(new SseEmitter(DURACION_MS), null, null, null, idServicio, System.currentTimeMillis()));
    }

    private SseEmitter registrar(Suscripcion s) {
        // Tope por usuario (varias pestañas) y total: se cierra la conexión más vieja.
        if (s.idUsuario() != null) {
            List<Map.Entry<String, Suscripcion>> suyas = suscripciones.entrySet().stream()
                    .filter(e -> s.idUsuario().equals(e.getValue().idUsuario()))
                    .sorted((a, b) -> Long.compare(a.getValue().desde(), b.getValue().desde()))
                    .toList();
            for (int i = 0; i <= suyas.size() - MAX_POR_USUARIO; i++) cerrar(suyas.get(i).getKey());
        }
        if (suscripciones.size() >= MAX_TOTAL) {
            suscripciones.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().desde(), b.getValue().desde()))
                    .ifPresent(e -> cerrar(e.getKey()));
        }

        String id = UUID.randomUUID().toString();
        SseEmitter emisor = s.emisor();
        emisor.onCompletion(() -> suscripciones.remove(id));
        emisor.onTimeout(() -> { suscripciones.remove(id); emisor.complete(); });
        emisor.onError(e -> suscripciones.remove(id));
        suscripciones.put(id, s);
        try {
            emisor.send(SseEmitter.event().name("conectado").data("ok"));
        } catch (IOException | IllegalStateException e) {
            suscripciones.remove(id);
        }
        return emisor;
    }

    /** Avisa a cada conexión los servicios que le incumben (un solo mensaje por conexión). */
    public void publicar(Collection<Cambio> cambios) {
        if (cambios.isEmpty() || suscripciones.isEmpty()) return;
        suscripciones.forEach((id, s) -> {
            List<Integer> suyos = cambios.stream().filter(c -> leIncumbe(s, c)).map(Cambio::idServicio).distinct().toList();
            if (suyos.isEmpty()) return;
            String json = suyos.stream().map(String::valueOf).collect(Collectors.joining(",", "{\"servicios\":[", "]}"));
            enviar(id, s, SseEmitter.event().name("cambio").data(json, MediaType.APPLICATION_JSON));
        });
    }

    /** Avisa SOLO a los administradores qué temas cambiaron (documentos, pagos, reversiones...): el
     *  tablero del administrador recarga y los demás ni se enteran. Sin datos, solo los nombres. */
    public void publicarAdmin(Collection<String> temas) {
        if (temas.isEmpty() || suscripciones.isEmpty()) return;
        String json = temas.stream().sorted().map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(",", "{\"servicios\":[],\"temas\":[", "]}"));
        suscripciones.forEach((id, s) -> {
            if (s.rol() == Rol.ADMIN_POLYGRAPH) {
                enviar(id, s, SseEmitter.event().name("cambio").data(json, MediaType.APPLICATION_JSON));
            }
        });
    }

    /** Quién está conectado AHORA (con la plataforma abierta): usuarios distintos por tipo. */
    public record Conectados(long internos, long clientes, long candidatos) {}

    public Conectados conectados() {
        Set<Long> internos = new HashSet<>();
        Set<Long> clientes = new HashSet<>();
        Set<Integer> candidatos = new HashSet<>();
        suscripciones.values().forEach(s -> {
            if (s.idServicioCandidato() != null) candidatos.add(s.idServicioCandidato());
            else if (s.idUsuario() != null && (s.rol() == Rol.ADMIN_CLIENTE || s.rol() == Rol.ANALISTA_CLIENTE)) clientes.add(s.idUsuario());
            else if (s.idUsuario() != null) internos.add(s.idUsuario());
        });
        return new Conectados(internos.size(), clientes.size(), candidatos.size());
    }

    private static boolean leIncumbe(Suscripcion s, Cambio c) {
        if (s.idServicioCandidato() != null) return s.idServicioCandidato().equals(c.idServicio());
        if (s.rol() == Rol.ADMIN_POLYGRAPH) return true;
        if (s.idUsuario() != null && (s.idUsuario().equals(c.idGestor()) || c.usuarios().contains(s.idUsuario()))) return true;
        return s.idCliente() != null && s.idCliente().equals(c.idCliente())
                && (s.rol() == Rol.ADMIN_CLIENTE || s.rol() == Rol.ANALISTA_CLIENTE);
    }

    /** Mantiene viva la conexión a través de proxies (ngrok, nginx) que cortan lo inactivo. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void latido() {
        suscripciones.forEach((id, s) -> enviar(id, s, SseEmitter.event().comment("ping")));
    }

    private void enviar(String id, Suscripcion s, SseEmitter.SseEventBuilder evento) {
        try {
            s.emisor().send(evento);
        } catch (IOException | IllegalStateException e) {
            suscripciones.remove(id);
        }
    }

    private void cerrar(String id) {
        Suscripcion s = suscripciones.remove(id);
        if (s != null) {
            try { s.emisor().complete(); } catch (Exception ignorada) { /* ya estaba cerrada */ }
        }
    }

    public int conexionesAbiertas() { return suscripciones.size(); }

    /** Al apagar, se cierran las conexiones para no demorar el apagado ordenado. */
    @PreDestroy
    public void cerrarTodas() {
        suscripciones.keySet().forEach(this::cerrar);
    }
}
