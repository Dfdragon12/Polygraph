package com.polygraph.erp.modules.notificaciones.dto;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.entity.Notificacion;

import java.time.LocalDateTime;

public record NotificacionResponse(
        Long id,
        String tipo,
        String titulo,
        String mensaje,
        Boolean leida,
        LocalDateTime fechaCreacion,
        String realizadoPorNombre
) {
    public static NotificacionResponse from(Notificacion n) {
        String actor = null;
        if (n.getRealizadoPor() != null) {
            Usuario u = n.getRealizadoPor();
            actor = u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "");
        }
        return new NotificacionResponse(
                n.getId(), n.getTipo(), n.getTitulo(),
                n.getMensaje(), n.getLeida(), n.getFechaCreacion(),
                actor
        );
    }
}
