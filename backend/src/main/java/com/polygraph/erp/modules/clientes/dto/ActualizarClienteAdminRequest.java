package com.polygraph.erp.modules.clientes.dto;

import jakarta.validation.constraints.Size;

public record ActualizarClienteAdminRequest(
        // Si viene y es distinto al actual, ClienteAdminService.actualizar cambia el tipo de
        // persona del cliente ("NATURAL" o "JURIDICA") — corrige un registro hecho con el tipo
        // equivocado. Null: no se toca.
        String tipoPersona,
        // Igual que tipoPersona pero para "PREPAGO"/"POSPAGO" — ver ClienteAdminService.
        // cambiarTipoCliente. Null: no se toca.
        String tipoCliente,
        // Persona natural
        @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        // Persona jurídica
        @Size(max = 200) String razonSocial,
        @Size(max = 200) String nombreComercial,
        @Size(max = 150) String representanteLegal,
        @Size(max = 20) String nit,
        @Size(max = 1) String dv,
        // Común
        @Size(max = 20) String telefono,
        String direccion,
        String observaciones,
        Integer idCiudad,
        // País y barrio — opcionales, para clientes fuera de Colombia o para precisar la
        // dirección (ver PaisController / BarrioController).
        Integer idPais,
        Integer idBarrio,
        Long idGestor
) {}
