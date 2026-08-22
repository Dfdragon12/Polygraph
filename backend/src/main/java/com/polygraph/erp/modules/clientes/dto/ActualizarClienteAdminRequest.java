package com.polygraph.erp.modules.clientes.dto;

import jakarta.validation.constraints.Size;

public record ActualizarClienteAdminRequest(
        // Persona natural
        @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        // Persona jurídica
        @Size(max = 200) String razonSocial,
        @Size(max = 200) String nombreComercial,
        @Size(max = 150) String representanteLegal,
        // Común
        @Size(max = 20) String telefono,
        String direccion,
        String observaciones,
        Integer idCiudad,
        Long idGestor
) {}
