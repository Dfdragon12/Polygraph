package com.polygraph.erp.security;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService;
import com.polygraph.erp.modules.gestor.service.EnlaceProveedorService.Sesion;
import com.polygraph.erp.modules.gestor.service.ReglasEnlaceProveedor;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** PF-09 · Entrada del proveedor externo. Quien manda la cabecera {@code X-Acceso-Proveedor} con un enlace vigente trabaja
 *  a nombre del poligrafista asignado a ESA evaluación y solo puede llamar las rutas de ReglasEnlaceProveedor (la pantalla
 *  de la evaluación). Cualquier otra ruta responde 403 aunque el poligrafista sí la tuviera con su usuario. Un enlace
 *  revocado, enviado, vencido o inexistente responde 401 con un código que la pantalla muestra en palabras.
 *  Va ANTES del filtro JWT: si llega la cabecera, manda el enlace y se ignora cualquier Bearer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnlaceProveedorFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Acceso-Proveedor";
    public static final String ATRIBUTO = "enlaceProveedor";

    private static final int MAX_FALLIDOS = 20;
    private static final Duration VENTANA = Duration.ofMinutes(1);
    private static final Map<String, String> MENSAJES = Map.of(
            "ENLACE_ENVIADO", "Este informe ya se envió. El enlace dejó de funcionar.",
            "ENLACE_VENCIDO", "Este enlace venció. Pide uno nuevo al programador.",
            "ENLACE_REVOCADO", "Este enlace fue cancelado. Pide uno nuevo al programador.",
            "ENLACE_REEMPLAZADO", "Hay un enlace más reciente para esta evaluación. Usa el último que te enviaron.",
            "ENLACE_NO_DISPONIBLE", "Esta evaluación ya no está disponible para diligenciar. Consulta con el programador.",
            "ENLACE_DESCONOCIDO", "Este enlace no es válido. Revisa que lo copiaste completo o pide uno nuevo al programador.");

    private final EnlaceProveedorService enlaces;
    private final UsuarioRepository usuarioRepository;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> fallidosPorIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String cabecera = request.getHeader(CABECERA);
        if (cabecera == null || cabecera.isBlank() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String token = cabecera.trim();
        String ip = request.getRemoteAddr();

        if (demasiadosFallidos(ip)) {
            responder(response, 429, "ENLACE_DEMASIADOS_INTENTOS", "Demasiados intentos. Espera un minuto e intenta de nuevo.");
            return;
        }
        Optional<Sesion> sesion = enlaces.resolver(token);
        if (sesion.isEmpty()) {
            anotarFallido(ip);
            String codigo = enlaces.motivoInvalido(token);
            responder(response, 401, codigo, MENSAJES.getOrDefault(codigo, MENSAJES.get("ENLACE_DESCONOCIDO")));
            return;
        }
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (!ReglasEnlaceProveedor.rutaPermitida(request.getMethod(), ruta, sesion.get().idServicioSubproceso())) {
            log.warn("Enlace de proveedor {}: ruta fuera de su evaluación {} {}", sesion.get().idEnlace(), request.getMethod(), ruta);
            responder(response, 403, "ENLACE_RUTA_NO_PERMITIDA", "Este enlace solo da acceso a la evaluación asignada.");
            return;
        }
        Optional<Usuario> ejecutor = usuarioRepository.findById(sesion.get().idUsuarioEjecutor())
                .filter(u -> u.isEnabled() && u.isAccountNonLocked() && u.getRol() == Rol.POLIGRAFISTA);
        if (ejecutor.isEmpty()) {
            responder(response, 401, "ENLACE_NO_DISPONIBLE", MENSAJES.get("ENLACE_NO_DISPONIBLE"));
            return;
        }
        Usuario u = ejecutor.get();
        UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
        autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
        request.setAttribute(ATRIBUTO, sesion.get());
        try {
            enlaces.registrarUso(sesion.get().idEnlace());
        } catch (Exception e) {
            log.warn("No se pudo anotar el uso del enlace {}: {}", sesion.get().idEnlace(), e.getMessage());
        }
        chain.doFilter(request, response);
    }

    private boolean demasiadosFallidos(String ip) {
        CopyOnWriteArrayList<Instant> l = fallidosPorIp.get(ip);
        if (l == null) return false;
        l.removeIf(t -> t.isBefore(Instant.now().minus(VENTANA)));
        return l.size() >= MAX_FALLIDOS;
    }

    private void anotarFallido(String ip) {
        fallidosPorIp.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>()).add(Instant.now());
        if (fallidosPorIp.size() > 5000) fallidosPorIp.entrySet().removeIf(e -> e.getValue().stream().allMatch(t -> t.isBefore(Instant.now().minus(VENTANA))));
    }

    private void responder(HttpServletResponse response, int estado, String codigo, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje.replace("\"", "'") + "\"}");
    }
}
