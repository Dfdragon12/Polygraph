package com.polygraph.erp.modules.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remitente;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async
    public void enviarActivacion(String destinatario, String nombre, String token) {
        String enlace = frontendUrl + "/activate?token=" + token;
        String html = cargarPlantilla("templates/email/activacion.html")
                .replace("{{nombre}}", nombre)
                .replace("{{enlace}}", enlace);
        enviar(destinatario, "Activa tu cuenta - Polygraph Service", html);
    }

    @Async
    public void enviarResetPassword(String destinatario, String nombre, String token) {
        String enlace = frontendUrl + "/reset-password?token=" + token;
        String html = cargarPlantilla("templates/email/reset_password.html")
                .replace("{{nombre}}", nombre)
                .replace("{{enlace}}", enlace);
        enviar(destinatario, "Restablecer contraseña - Polygraph Service", html);
    }

    private void enviar(String destinatario, String asunto, String html) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            mailSender.send(mensaje);
            log.info("Email enviado a: {} | Asunto: {}", destinatario, asunto);
        } catch (MessagingException e) {
            log.error("Error enviando email a {}: {}", destinatario, e.getMessage());
        }
    }

    private String cargarPlantilla(String ruta) {
        try {
            ClassPathResource recurso = new ClassPathResource(ruta);
            return recurso.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("No se pudo cargar la plantilla de email: {}", ruta);
            return "<p>Error cargando plantilla.</p>";
        }
    }
}
