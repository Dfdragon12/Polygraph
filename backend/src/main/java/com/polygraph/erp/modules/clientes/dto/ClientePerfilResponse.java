package com.polygraph.erp.modules.clientes.dto;

import java.time.LocalDateTime;

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
        String nombreCiudad,
        // Cuenta de quien consulta (el usuario del cliente, no la empresa): el controlador los completa
        // — el servicio de la empresa los deja vacíos.
        LocalDateTime miembroDesde,
        LocalDateTime ultimoAcceso,
        LocalDateTime fechaUltimoCambioPassword,
        // Días para que el sistema pida cambiar la contraseña (0 o menos = ya toca; null = sin fecha).
        Integer diasParaCambiarPassword,
        int diasVigenciaPassword,
        boolean requiereCambioPassword
) {
    /** Misma respuesta con los datos de la cuenta del usuario autenticado. */
    public ClientePerfilResponse conCuenta(LocalDateTime miembroDesde, LocalDateTime ultimoAcceso, LocalDateTime fechaUltimoCambioPassword,
                                           Integer diasParaCambiarPassword, int diasVigenciaPassword, boolean requiereCambioPassword) {
        return new ClientePerfilResponse(idCliente, tipoPersona, tipoCliente, nit, dv, razonSocial, nombreComercial,
                representanteLegal, nombre, apellido, emailPrincipal, telefono, direccion, idCiudad, nombreCiudad,
                miembroDesde, ultimoAcceso, fechaUltimoCambioPassword, diasParaCambiarPassword, diasVigenciaPassword,
                requiereCambioPassword);
    }
}
