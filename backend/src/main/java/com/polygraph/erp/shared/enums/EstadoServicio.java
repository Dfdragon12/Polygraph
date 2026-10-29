package com.polygraph.erp.shared.enums;

public enum EstadoServicio {
    PENDIENTE,
    PROGRAMANDO,
    EN_EJECUCION,
    FINALIZADO,
    PUBLICADO,
    CANCELADO,
    REPROGRAMADO;

    /** Estados en los que la solicitud ya no cambia de estado de tiempo: se entregó o se canceló. Todo lo demás (incluido REPROGRAMADO,
     *  PENDIENTE —que espera al candidato— y FINALIZADO, que aún no se publica) sigue abierto. */
    public static final java.util.List<EstadoServicio> CERRADOS = java.util.List.of(PUBLICADO, CANCELADO);

    public boolean cerrado() {
        return CERRADOS.contains(this);
    }
}
