package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ActualizarUsuarioInternoRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 150) String email,
        // Opcional: vacío = no cambia la contraseña
        @Size(min = 8, max = 50) String password,
        @NotNull Rol rol,
        @Size(max = 20) String telefono,
        @Size(max = 20) String tipoDocumento,
        @Size(max = 20) String documento,
        Integer idCiudadResidencia,
        String novedadesSala,
        // Ciudades reales del catálogo que cubre este empleado — con numeroSala para POLIGRAFISTA,
        // sin sala para VISITADOR. Null/vacío = sin ciudades (el servicio valida obligatoriedad por rol).
        @Valid List<CiudadAsignadaRequest> ciudadesAsignadas,
        // Subprocesos del catálogo asignados (uso principal: ANALISTA_INTERNO)
        List<Integer> idsSubprocesos
) {}
