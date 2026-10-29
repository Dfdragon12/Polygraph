package com.polygraph.erp.config;

import com.polygraph.erp.modules.evaluados.service.AccesoCandidatoService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Todo el portal del candidato (/api/v1/evaluees/link/{token}/...) exige la llave que se obtiene
 *  escribiendo el número de documento (AccesoCandidatoService), menos la petición que la entrega. */
@Configuration
@RequiredArgsConstructor
public class AccesoCandidatoConfig implements WebMvcConfigurer {

    private static final String PREFIJO = "/api/v1/evaluees/link/";

    private final AccesoCandidatoService accesoCandidatoService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // El cierre de una conexión de eventos (despacho ASYNC/ERROR) ya pasó la revisión al abrirla.
                if (request.getDispatcherType() != DispatcherType.REQUEST) return true;
                String resto = request.getRequestURI().substring(request.getRequestURI().indexOf(PREFIJO) + PREFIJO.length());
                String token = resto.contains("/") ? resto.substring(0, resto.indexOf('/')) : resto;
                accesoCandidatoService.exigir(token, request.getHeader(AccesoCandidatoService.ENCABEZADO));
                return true;
            }
        }).addPathPatterns(PREFIJO + "**").excludePathPatterns(PREFIJO + "*/verificar");
    }
}
