package com.polygraph.erp.modules.clientes.dto;

import java.time.LocalDateTime;

/** "Mi perfil" del analista de un cliente (ANALISTA_CLIENTE): sus datos personales —que puede
 *  editar— y, de solo lectura, la empresa a la que pertenece y el gestor que lo atiende. */
public record MiPerfilAnalistaClienteResponse(
        Long idUsuario,
        String nombre,
        String apellido,
        String email,
        String nombreEmpresa,
        String nombreGestor,
        String emailGestor,
        LocalDateTime miembroDesde,
        LocalDateTime ultimoAcceso,
        // Contraseña: último cambio y días para que el sistema pida cambiarla (0 o menos = ya toca;
        // null = sin fecha registrada). Misma regla del login.
        LocalDateTime fechaUltimoCambioPassword,
        Integer diasParaCambiarPassword,
        int diasVigenciaPassword,
        boolean requiereCambioPassword
) {}
