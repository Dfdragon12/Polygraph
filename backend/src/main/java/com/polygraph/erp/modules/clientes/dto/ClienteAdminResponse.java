package com.polygraph.erp.modules.clientes.dto;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClienteAdminResponse(
        Integer idCliente,
        String tipoPersona,
        String tipoCliente,
        String nombreDisplay,
        String nombreComercial,
        String representanteLegal,
        String nit,
        String emailPrincipal,
        String telefono,
        String direccion,
        String estado,
        LocalDateTime fechaRegistro,
        // Usuario principal asociado
        Long idUsuario,
        String emailUsuario,
        Boolean usuarioActivo,
        LocalDateTime ultimoAcceso,
        // Info pospago (null si es prepago)
        PospagoInfo pospago
) {
    public record PospagoInfo(
            BigDecimal limiteCredito,
            BigDecimal creditoDisponible,
            String estadoMora,
            Integer diasMora,
            Boolean requiereAprobacion
    ) {}

    public static ClienteAdminResponse from(Cliente c, Usuario u, ClientePospago cp) {
        String nombreDisplay = "NATURAL".equals(c.getTipoPersona().name())
                ? (c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "")
                : (c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial());

        PospagoInfo pospagoInfo = null;
        if (cp != null) {
            pospagoInfo = new PospagoInfo(
                    cp.getLimiteCredito(),
                    cp.getCreditoDisponible(),
                    cp.getEstadoMora() != null ? cp.getEstadoMora().name() : null,
                    cp.getDiasMora(),
                    cp.getRequiereAprobacion()
            );
        }

        return new ClienteAdminResponse(
                c.getIdCliente(),
                c.getTipoPersona().name(),
                c.getTipoCliente().name(),
                nombreDisplay.trim(),
                c.getNombreComercial(),
                c.getRepresentanteLegal(),
                c.getNit(),
                c.getEmailPrincipal(),
                c.getTelefono(),
                c.getDireccion(),
                c.getEstado(),
                c.getFechaRegistro(),
                u != null ? u.getIdUsuario() : null,
                u != null ? u.getEmail() : null,
                u != null ? u.getActivo() : null,
                u != null ? u.getUltimoAcceso() : null,
                pospagoInfo
        );
    }
}
