package com.polygraph.erp.modules.tiemporeal;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Avisos en tiempo real para usuarios con sesión (internos y clientes). El navegador abre esta
 *  conexión una vez y la mantiene; cada "cambio" trae solo los idServicio que le incumben. */
@RestController
@RequestMapping("/api/v1/tiempo-real")
@RequiredArgsConstructor
public class TiempoRealController {

    private final CanalCambios canal;
    private final UsuarioRepository usuarioRepository;

    @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(Authentication auth, HttpServletResponse respuesta) {
        Usuario u = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.UNAUTHORIZED));
        sinBuffer(respuesta);
        return canal.suscribirUsuario(u.getIdUsuario(), u.getRol(), u.getIdCliente());
    }

    /** Que ningún proxy (nginx, ngrok) acumule los eventos antes de entregarlos. */
    public static void sinBuffer(HttpServletResponse respuesta) {
        respuesta.setHeader("Cache-Control", "no-cache, no-transform");
        respuesta.setHeader("X-Accel-Buffering", "no");
    }
}
