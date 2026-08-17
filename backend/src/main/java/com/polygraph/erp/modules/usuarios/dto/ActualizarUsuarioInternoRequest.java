package com.polygraph.erp.modules.usuarios.dto;

import com.polygraph.erp.shared.enums.Rol;
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
        @Size(max = 100) String salaEncargada,
        String novedadesSala,
        @Size(max = 200) String zonasVisita,
        // Subprocesos del catálogo asignados (uso principal: ANALISTA_INTERNO)
        List<Integer> idsSubprocesos
) {}
