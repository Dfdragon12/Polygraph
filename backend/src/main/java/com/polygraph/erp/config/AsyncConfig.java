package com.polygraph.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Ejecutor ACOTADO para todo lo @Async (correos, notificaciones, aplicación de pagos).
 *
 *  Antes no había ninguno configurado y se usaba el de Spring Boot por defecto, cuya cola es
 *  ILIMITADA: si el SMTP se ponía lento, las tareas se acumulaban en RAM sin tope hasta un
 *  OutOfMemoryError. Ahora la cola tiene capacidad fija y, cuando se llena, CallerRunsPolicy
 *  hace que el hilo que la pidió ejecute la tarea él mismo: el request que manda el correo tarda
 *  un poco más (backpressure natural sobre quien produce), pero no se pierde ningún correo ni se
 *  llena la memoria.
 *
 *  Se llama applicationTaskExecutor para reemplazar al de Spring Boot (que se retira solo cuando
 *  ya existe un Executor). Los pools de la cola persistente (módulo trabajos) son aparte. */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // ThreadPoolExecutor solo crece de "mínimos" a "máximos" cuando la cola ya está llena, así
    // que en la práctica trabajan los mínimos y los máximos son el colchón ante un pico.
    @Value("${app.async.hilos-minimos:4}")
    private int hilosMinimos;

    @Value("${app.async.hilos-maximos:8}")
    private int hilosMaximos;

    @Value("${app.async.capacidad-cola:500}")
    private int capacidadCola;

    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setThreadNamePrefix("async-");
        ejecutor.setCorePoolSize(hilosMinimos);
        ejecutor.setMaxPoolSize(Math.max(hilosMinimos, hilosMaximos));
        ejecutor.setQueueCapacity(capacidadCola);
        ejecutor.setKeepAliveSeconds(60);
        ejecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(20);
        return ejecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    /** Un @Async void que falla no tiene a quién avisarle: al menos queda en el log. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, metodo, parametros) ->
                log.error("Error en tarea asíncrona {}.{}: {}", metodo.getDeclaringClass().getSimpleName(),
                        metodo.getName(), error.getMessage(), error);
    }
}
