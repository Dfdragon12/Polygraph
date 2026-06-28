package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.Rol;

import java.time.LocalDateTime;

public record UsuarioClienteResponse(
        Long idUsuario,
        Integer idCliente,
        String nombre,
        String apellido,
        String email,
        Rol rol,
        Boolean activo,
        LocalDateTime fechaCreacion,
        LocalDateTime ultimoAcceso
) {
    public static UsuarioClienteResponse from(Usuario u) {
        return new UsuarioClienteResponse(
                u.getIdUsuario(),
                u.getIdCliente(),
                u.getNombre(),
                u.getApellido(),
                u.getEmail(),
                u.getRol(),
                u.getActivo(),
                u.getFechaCreacion(),
                u.getUltimoAcceso()
        );
    }
}
