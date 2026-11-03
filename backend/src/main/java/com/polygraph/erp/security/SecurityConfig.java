package com.polygraph.erp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final RateLimitFilter rateLimitFilter;
    private final EnlaceProveedorFilter enlaceProveedorFilter;

    @Value("${app.cors.allowed-origins}")
    private String origenesPermitidos;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Despachos ASYNC/ERROR de una petición que YA pasó la seguridad (ej. el cierre de
                // una conexión de avisos en tiempo real, SseEmitter): el filtro JWT no corre en el
                // despacho async, así que sin esto terminaban en "acceso denegado" en el log.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/evaluees/link/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/ciudades", "/api/v1/ciudades/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/ciudades").permitAll()
                // Barrios/departamentos/países: mismo criterio que ciudades — los formularios sin
                // sesión (candidato vía link, registro de cliente) necesitan poder leerlos y, en el
                // caso de barrios, crear uno nuevo si no está en el catálogo. Antes faltaban acá
                // aunque sus propios controladores ya decían "Público" en el comentario — por eso
                // devolvían 401 para cualquiera sin sesión.
                .requestMatchers(HttpMethod.GET, "/api/v1/barrios", "/api/v1/barrios/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/barrios").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/departamentos").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/paises").permitAll()
                .requestMatchers(
                    "/api/v1/api-docs/**",
                    "/api/v1/swagger-ui/**",
                    "/api/v1/swagger-ui.html"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/services", "/api/v1/services/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/pagos/wompi/webhook", "/api/v1/webhooks/wompi").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
            // PF-09: el enlace del proveedor externo se resuelve antes que el JWT y solo abre la ruta de su evaluación.
            .addFilterBefore(enlaceProveedorFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origenesPermitidos.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
