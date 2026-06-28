package com.polygraph.erp.modules.notificaciones.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.notificaciones.dto.NotificacionResponse;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    @Autowired
    @Lazy
    private NotificacionService self;

    // Qué roles reciben cada tipo de notificación
    private static final List<Rol> TODOS_INTERNOS = List.of(
            Rol.ADMIN_POLYGRAPH, Rol.GESTOR,
            Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.PROGRAMADOR, Rol.VISITADOR
    );
    private static final Map<String, List<Rol>> ROLES_POR_TIPO = Map.of(
            "CATALOGO", TODOS_INTERNOS,
            "CLIENTE",  TODOS_INTERNOS,
            "USUARIO",  List.of(Rol.ADMIN_POLYGRAPH, Rol.GESTOR)
    );
    private static final List<Rol> ROLES_DEFAULT = List.of(Rol.ADMIN_POLYGRAPH);

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(String email) {
        return notificacionRepository.findTop15ByUsuario_EmailOrderByFechaCreacionDesc(email)
                .stream()
                .map(NotificacionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(String email) {
        return notificacionRepository.countByUsuario_EmailAndLeidaFalse(email);
    }

    @Transactional
    public void marcarTodasLeidas(String email) {
        notificacionRepository.marcarTodasLeidas(email, LocalDateTime.now());
    }

    @Transactional
    public void marcarLeida(Long id, String email) {
        Notificacion n = notificacionRepository.findByIdAndUsuario_Email(id, email)
                .orElseThrow(() -> new ApiException("Notificación no encontrada", HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(n.getLeida())) {
            n.setLeida(true);
            n.setFechaLectura(LocalDateTime.now());
        }
    }

    @Transactional
    public void eliminar(Long id, String email) {
        Notificacion n = notificacionRepository.findByIdAndUsuario_Email(id, email)
                .orElseThrow(() -> new ApiException("Notificación no encontrada", HttpStatus.NOT_FOUND));
        notificacionRepository.delete(n);
    }

    public void crearParaAdmins(String tipo, String titulo, String mensaje) {
        // Resolver el ID del actor en el hilo del caller (SecurityContext disponible).
        // Pasar solo el ID — no la entidad — para evitar problemas de detached object en @Async.
        self.crearParaAdmins(tipo, titulo, mensaje, resolverActorIdActual());
    }

    @Async
    @Transactional
    public void crearParaAdmins(String tipo, String titulo, String mensaje, Long realizadoPorId) {
        // Recargar el actor dentro de esta transacción para que sea una entidad managed.
        Usuario actor = realizadoPorId != null
                ? usuarioRepository.findById(realizadoPorId).orElse(null)
                : null;

        List<Rol> rolesDestino = ROLES_POR_TIPO.getOrDefault(tipo, ROLES_DEFAULT);

        List<Usuario> destinatarios = usuarioRepository.findByRolIn(rolesDestino)
                .stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .toList();

        destinatarios.forEach(dest -> notificacionRepository.save(
                Notificacion.builder()
                        .usuario(dest)
                        .tipo(tipo)
                        .titulo(titulo)
                        .mensaje(mensaje)
                        .leida(false)
                        .fechaCreacion(LocalDateTime.now())
                        .realizadoPor(actor)
                        .build()
        ));
        log.info("Notificación '{}' enviada a {} usuario(s) [roles: {}] — actor: {}",
                titulo, destinatarios.size(), rolesDestino, actor != null ? actor.getEmail() : "sistema");
    }

    private Long resolverActorIdActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return usuarioRepository.findByEmail(auth.getName())
                .map(Usuario::getIdUsuario)
                .orElse(null);
    }
}
