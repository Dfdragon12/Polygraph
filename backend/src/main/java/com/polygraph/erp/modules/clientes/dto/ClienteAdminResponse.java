package com.polygraph.erp.modules.clientes.dto;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        Integer idCiudad,
        String nombreCiudad,
        String estado,
        LocalDateTime fechaRegistro,
        // Usuario principal asociado (ADMIN_CLIENTE)
        Long idUsuario,
        String emailUsuario,
        Boolean usuarioActivo,
        LocalDateTime ultimoAcceso,
        // Todos los usuarios (ADMIN_CLIENTE + ANALISTA_CLIENTE, etc.) anclados a este cliente
        List<UsuarioAsociado> usuariosAsociados,
        // Info pospago (null si es prepago)
        PospagoInfo pospago,
        // Gestor de Polygraph asignado como contacto (null si no tiene)
        GestorInfo gestorAsignado,
        // Estado de los documentos legales (soportes_clientes) — incluye los que ni se han cargado
        DocumentosResumen documentosResumen
) {
    public record DocumentosResumen(
            long sinCargar,
            long pendientes,
            long validados,
            long rechazados,
            long vencidos
    ) {}

    public record PospagoInfo(
            BigDecimal limiteCredito,
            BigDecimal creditoDisponible,
            String estadoMora,
            Integer diasMora,
            Boolean requiereAprobacion
    ) {}

    public record GestorInfo(
            Long idUsuario,
            String nombreCompleto,
            String telefono
    ) {}

    public record UsuarioAsociado(
            Long idUsuario,
            String nombre,
            String apellido,
            String email,
            String rol,
            Boolean activo,
            LocalDateTime ultimoAcceso
    ) {
        public static UsuarioAsociado from(Usuario u) {
            return new UsuarioAsociado(
                    u.getIdUsuario(),
                    u.getNombre(),
                    u.getApellido(),
                    u.getEmail(),
                    u.getRol().name(),
                    u.getActivo(),
                    u.getUltimoAcceso()
            );
        }
    }

    public static ClienteAdminResponse from(Cliente c, Usuario u, List<Usuario> usuariosCliente, ClientePospago cp,
                                             GestorInfo gestorAsignado, String nombreCiudad, DocumentosResumen documentosResumen) {
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
                c.getIdCiudad(),
                nombreCiudad,
                c.getEstado(),
                c.getFechaRegistro(),
                u != null ? u.getIdUsuario() : null,
                u != null ? u.getEmail() : null,
                u != null ? u.getActivo() : null,
                u != null ? u.getUltimoAcceso() : null,
                usuariosCliente.stream().map(UsuarioAsociado::from).toList(),
                pospagoInfo,
                gestorAsignado,
                documentosResumen
        );
    }
}
