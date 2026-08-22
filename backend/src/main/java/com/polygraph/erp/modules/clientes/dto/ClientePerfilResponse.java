package com.polygraph.erp.modules.clientes.dto;

/** Datos básicos de la empresa/cliente que el propio cliente puede ver y actualizar. */
public record ClientePerfilResponse(
        Integer idCliente,
        String tipoPersona,
        String tipoCliente,
        String nit,
        String dv,
        String razonSocial,
        String nombreComercial,
        String representanteLegal,
        String nombre,
        String apellido,
        String emailPrincipal,
        String telefono,
        String direccion,
        Integer idCiudad,
        String nombreCiudad
) {}
