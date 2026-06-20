package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.shared.enums.Rol;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UsuarioInternoResponse(
        Long idUsuario,
        Integer idEmpleado,
        String nombre,
        String apellido,
        String email,
        Rol rol,
        String telefono,
        String tipoDocumento,
        String documento,
        String ciudadResidencia,
        String salaEncargada,
        String novedadesSala,
        String zonasVisita,
        Boolean activo,
        LocalDate fechaIngreso,
        LocalDateTime fechaCreacion,
        LocalDateTime ultimoAcceso,
        Boolean emailVerificado,
        Boolean requiere2fa,
        Boolean requiereCambioPassword,
        LocalDateTime fechaUltimoCambioPassword
) {
    public static UsuarioInternoResponse from(Usuario u, UsuariosInternos emp) {
        return new UsuarioInternoResponse(
                u.getIdUsuario(),
                emp != null ? emp.getIdEmpleado() : null,
                u.getNombre(),
                u.getApellido(),
                u.getEmail(),
                u.getRol(),
                emp != null ? emp.getTelefono() : null,
                emp != null ? emp.getTipoDocumento() : null,
                emp != null ? emp.getDocumento() : null,
                emp != null ? emp.getCiudadResidencia() : null,
                emp != null ? emp.getSalaEncargada() : null,
                emp != null ? emp.getNovedadesSala() : null,
                emp != null ? emp.getZonasVisita() : null,
                u.getActivo(),
                emp != null ? emp.getFechaIngreso() : null,
                u.getFechaCreacion(),
                u.getUltimoAcceso(),
                u.getEmailVerificado(),
                u.getRequiere2fa(),
                u.getRequiereCambioPassword(),
                u.getFechaUltimoCambioPassword()
        );
    }
}
