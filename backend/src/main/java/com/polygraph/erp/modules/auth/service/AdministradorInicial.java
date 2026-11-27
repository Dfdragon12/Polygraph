package com.polygraph.erp.modules.auth.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.shared.enums.Rol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * PC-08 · El primer administrador de una base NUEVA. Ninguna migración crea usuarios, así que sin esto un servidor recién instalado no tendría con quién entrar. Solo actúa si no existe
 * NINGÚN administrador y se definieron ADMIN_EMAIL y ADMIN_PASSWORD (no hay contraseña por defecto): lo crea activo y con el cambio de contraseña obligatorio en el primer ingreso.
 * Con un administrador ya creado no hace nada, aunque las variables sigan puestas (se pueden quitar del entorno). Nunca escribe la contraseña en el registro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdministradorInicial {

    public static final int MINIMO_CARACTERES = 8;   // la misma regla de CambiarPasswordRequest

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;

    @Value("${app.admin.email:}") private String email = "";
    @Value("${app.admin.password:}") private String password = "";
    @Value("${app.admin.nombre:Administrador}") private String nombre = "Administrador";

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            crearSiHaceFalta();
        } catch (RuntimeException e) {
            log.error("No se pudo revisar el administrador inicial: {}", e.getMessage(), e);
        }
    }

    /** Devuelve true si creó el administrador. */
    public boolean crearSiHaceFalta() {
        if (usuarios.existsByRol(Rol.ADMIN_POLYGRAPH)) return false;
        String correo = email == null ? "" : email.trim();
        if (correo.isEmpty() || password == null || password.isEmpty()) {
            log.warn("La base no tiene ningún administrador y no se definieron ADMIN_EMAIL y ADMIN_PASSWORD: nadie podrá iniciar sesión. Defínelas y reinicia (ver README, «Primer administrador»).");
            return false;
        }
        if (!correo.matches("^\\S+@\\S+\\.\\S+$")) {
            log.error("ADMIN_EMAIL no es un correo válido: no se creó el administrador inicial.");
            return false;
        }
        if (password.length() < MINIMO_CARACTERES) {
            log.error("ADMIN_PASSWORD debe tener al menos {} caracteres: no se creó el administrador inicial.", MINIMO_CARACTERES);
            return false;
        }
        if (usuarios.existsByEmail(correo)) {
            log.error("Ya existe un usuario con el correo {} y no es administrador: no se creó el administrador inicial.", correo);
            return false;
        }
        usuarios.save(Usuario.builder().nombre(nombre == null || nombre.isBlank() ? "Administrador" : nombre.trim()).email(correo).password(codificador.encode(password))
                .rol(Rol.ADMIN_POLYGRAPH).activo(true).emailVerificado(true).requiere2fa(false).requiereCambioPassword(true).fechaCreacion(LocalDateTime.now()).build());
        log.info("Administrador inicial creado ({}). Debe cambiar la contraseña al entrar por primera vez.", correo);
        return true;
    }
}
