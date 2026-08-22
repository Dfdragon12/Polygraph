package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.shared.enums.Rol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        Integer idCiudadResidencia,
        String nombreCiudad,
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
        LocalDateTime fechaUltimoCambioPassword,
        List<SubprocesoAsignadoResponse> subprocesosAsignados
) {
    public record SubprocesoAsignadoResponse(Integer idTipoProgreso, String nombreProgreso) {
        public static SubprocesoAsignadoResponse from(TipoProgreso tp) {
            return new SubprocesoAsignadoResponse(tp.getIdTipoProgreso(), tp.getNombreProgreso());
        }
    }

    public static UsuarioInternoResponse from(Usuario u, UsuariosInternos emp) {
        return from(u, emp, null);
    }

    public static UsuarioInternoResponse from(Usuario u, UsuariosInternos emp, String nombreCiudad) {
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
                emp != null ? emp.getIdCiudadResidencia() : null,
                nombreCiudad,
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
                u.getFechaUltimoCambioPassword(),
                emp != null ? emp.getSubprocesosAsignados().stream().map(SubprocesoAsignadoResponse::from).toList() : List.of()
        );
    }
}
