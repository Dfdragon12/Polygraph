package com.polygraph.erp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Limita intentos por IP a /auth/login y /auth/forgot-password — sin esto, esas rutas quedan
 *  permitAll() en SecurityConfig sin ningún freno, abiertas a fuerza bruta de contraseñas o a
 *  spam de correos de recuperación. Ventana deslizante simple en memoria — pensada para un solo
 *  nodo backend; si el día de mañana esto se escala a varias instancias, hay que mover el conteo
 *  a algo compartido (Redis) porque cada instancia tendría su propio contador.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_LIMITADAS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/forgot-password");
    private static final int MAX_INTENTOS = 10;
    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> intentosPorClave = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !RUTAS_LIMITADAS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clave = request.getRequestURI() + "|" + resolverIp(request);
        Instant ahora = Instant.now();
        CopyOnWriteArrayList<Instant> intentos = intentosPorClave.computeIfAbsent(clave, k -> new CopyOnWriteArrayList<>());
        intentos.removeIf(instante -> instante.isBefore(ahora.minus(VENTANA)));

        if (intentos.size() >= MAX_INTENTOS) {
            response.setStatus(429); // Too Many Requests (RFC 6585) — no está entre las constantes SC_* del Servlet API
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"mensaje\":\"Demasiados intentos. Espera un minuto e intenta de nuevo.\"}");
            return;
        }

        intentos.add(ahora);
        filterChain.doFilter(request, response);
    }

    private String resolverIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Sin esto, cada IP/ruta que alguna vez pegó acá se queda para siempre en el mapa (aunque su
    // lista de intentos ya haya expirado) — en un proceso de larga duración es una fuga de memoria
    // lenta pero real.
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void limpiarClavesInactivas() {
        Instant limite = Instant.now().minus(VENTANA);
        intentosPorClave.entrySet().removeIf(entrada -> {
            entrada.getValue().removeIf(instante -> instante.isBefore(limite));
            return entrada.getValue().isEmpty();
        });
    }
}
