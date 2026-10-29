package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.shared.enums.Rol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Lo que un usuario interno ve de sí mismo en "Mi perfil". Lo editable es nombre, apellido, teléfono y
 *  ciudad de residencia; el resto (correo, rol, documento, ciudades que cubre, subprocesos) lo
 *  administra ADMIN_POLYGRAPH y aquí es de solo lectura. */
public record PerfilInternoResponse(
        Long idUsuario,
        String nombre,
        String apellido,
        String email,
        Rol rol,
        String telefono,
        String tipoDocumento,
        String documento,
        Integer idCiudadResidencia,
        String nombreCiudad,
        List<UsuarioInternoResponse.CiudadAsignadaResponse> ciudadesAsignadas,
        List<UsuarioInternoResponse.SubprocesoAsignadoResponse> subprocesosAsignados,
        LocalDate fechaIngreso,
        LocalDateTime ultimoAcceso,
        LocalDateTime fechaCreacion,
        // Contraseña: cuándo se cambió por última vez y cuántos días faltan para que el sistema pida
        // cambiarla (0 o menos = ya toca cambiarla; null = sin fecha de cambio registrada).
        LocalDateTime fechaUltimoCambioPassword,
        Integer diasParaCambiarPassword,
        int diasVigenciaPassword,
        // El administrador la marcó para cambio: toca cambiarla aunque el plazo no haya vencido.
        boolean requiereCambioPassword
) {}
