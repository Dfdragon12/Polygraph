package com.polygraph.erp.modules.pagos.event;

import java.util.UUID;

/**
 * Solo lleva identificadores (no la entidad JPA): el listener se ejecuta en otro hilo
 * después del commit, así que debe volver a cargar los datos frescos desde la BD.
 */
public record PagoAprobadoEvent(UUID idPago, Integer idCliente) {
}
