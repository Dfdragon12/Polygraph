package com.polygraph.erp.modules.evaluados.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una cita (poligrafía o visita) tal como la ve el CANDIDATO en su portal. Nunca lleva datos de
 *  contacto del empleado interno (correo, teléfono), solo su nombre.
 *
 *  estado: POR_PROGRAMAR · PROGRAMADA · CONFIRMADA · POR_REPROGRAMAR · REALIZADA · CANCELADA
 *  tipo:   POLIGRAFIA · VISITA */
public record CitaCandidatoResponse(
        Long id,
        String tipo,
        String nombre,
        String estado,
        LocalDateTime fechaProgramada,
        Integer duracionMinutos,
        String profesional,
        String lugar,
        boolean puedeSugerir,
        Sugerencia sugerenciaPendiente
) {
    public record Sugerencia(String comentario, LocalDateTime fechaCreacion, List<Opcion> opciones) {}

    public record Opcion(LocalDate fecha, String franja) {}
}
