package com.polygraph.erp.modules.servicios.dto;

import com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso;

import java.time.LocalDateTime;

/** Una fila del registro de cambios. {@code fecha} es la hora de pared de Colombia (America/Bogota). */
public record RegistroCambioResponse(Long id, Integer idServicio, Long idServicioSubproceso, String tipoSubproceso,
                                     String estadoAnterior, String estadoNuevo, String marca, String motivo, String textoAdicional,
                                     Long idResponsable, Long idUsuario, LocalDateTime fecha, String origen) {
    public static RegistroCambioResponse de(RegistroCambioSubproceso r) {
        return new RegistroCambioResponse(r.getId(), r.getIdServicio(), r.getIdServicioSubproceso(), r.getTipoSubproceso(),
                r.getEstadoAnterior(), r.getEstadoNuevo(), r.getMarca(), r.getMotivo(), r.getTextoAdicional(),
                r.getIdResponsable(), r.getIdUsuario(), r.getFechaBogota(), r.getOrigen());
    }
}
