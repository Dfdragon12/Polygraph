package com.polygraph.erp.modules.gestor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AsignacionResponse(
        Long id,
        Integer idServicio,
        String nombresEvaluado,
        String apellidosEvaluado,
        String cedulaEvaluado,
        String cargo,
        String proceso,
        Integer idTipoProgreso,
        String subproceso,
        // Si este subproceso tiene formulario/documento configurado — controla si el frontend
        // muestra el botón de "Verificación" en Mis Asignaciones. Ver TipoProgreso.requiereDocumento.
        Boolean requiereDocumento,
        // ANALISTA_INTERNO, POLIGRAFISTA o VISITADOR — de qué rol es este subproceso. Lo usa el
        // frontend para, por ejemplo, mostrarle a GESTOR en modo solo-lectura los subprocesos de
        // poligrafía/visita de sus clientes (esos los asigna PROGRAMADOR, no él).
        String rolResponsable,
        Integer duracionMinutos,
        String estado,
        Long idUsuarioAsignado,
        String nombreAsignado,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaAsignacion,
        LocalDateTime fechaCompletado,
        // Cuándo se aprobó (pasó a PUBLICADO) — distinto de fechaCompletado, casi nunca el mismo
        // momento. Ver ServicioSubproceso.fechaPublicado.
        LocalDateTime fechaPublicado,
        // Fecha de entrega prometida al cliente para TODO el servicio (calculada una vez al crear
        // la solicitud, ver DiasHabilesService) — igual para todos los subprocesos de un mismo
        // Servicio, no es específica de este subproceso puntual.
        LocalDate fechaEntregaEstimada,
        String observaciones,
        // Ciudad de residencia del evaluado — determina dónde debe atenderse la poligrafía/visita
        // y con qué poligrafista/visitador calza según sus ciudades asignadas.
        Integer idCiudad,
        String nombreCiudad,
        // Confirmación de asistencia (solo aplica a poligrafía/visita) — SIN_LLAMAR, CONFIRMADO,
        // NO_CONTESTA o REPROGRAMAR. Ver ServicioSubproceso.estadoConfirmacion.
        String estadoConfirmacion,
        String notasConfirmacion,
        // A qué PROGRAMADOR le encargó el GESTOR coordinar este subproceso (poligrafía/visita) —
        // ver ServicioSubproceso.programadorResponsable. Null si aún no se le ha asignado a nadie.
        Long idProgramadorResponsable,
        String nombreProgramadorResponsable,
        // Versión de la plantilla con la que se creó este subproceso vs. la versión actual — si
        // no coinciden, admin cambió la plantilla (agregó/quitó campos) después de creado. Ver
        // TipoProgreso.versionPlantilla.
        Integer versionPlantilla,
        Integer versionPlantillaActual,
        // Dónde es la cita (poligrafía/visita) — lo escribe el programador al agendar (V197).
        String lugar,
        // Solo en "Por hacer" de quien ejecuta (analista, poligrafista, visitador); 0 en el resto:
        // documentos del candidato por revisar, devueltos sin reemplazar y novedades propias sin respuesta.
        int documentosPorRevisar,
        int documentosDevueltos,
        int novedadesAbiertas,
        // Solo en "Por revisar" y "Aprobadas" de quien ejecuta: riesgo de ESTE subproceso (el mismo
        // que ve arriba del formulario). califica = la plantilla suma puntos; si no, no hay nivel.
        boolean riesgoCalifica,
        String riesgoNivel,
        BigDecimal riesgoPuntaje,
        boolean riesgoCritico,
        // Marcas del subproceso (SE-02): el trabajo ya empezó, lo devolvió el gestor, y cuántas veces se reprogramó la cita.
        boolean iniciado,
        boolean devuelto,
        int reprogramaciones,
        // El estado de tiempo del SERVICIO (días hábiles, el mismo en cliente, gestor, analista y programador): «Vencida · 17 días hábiles».
        com.polygraph.erp.shared.dto.TiempoServicio tiempo,
        // Citas (SE-07), calculado en el servidor con la hora de Colombia: el profesional ya avisó «no ha llegado»; qué acciones tiene ahora
        // (no ha llegado: pasada la hora de la cita; no se presentó: pasados 15 min) y si la cita está en la bandeja «Por confirmar».
        boolean evaluadoAvisado,
        boolean noHaLlegadoDisponible,
        boolean noSePresentoDisponible,
        boolean porConfirmar,
        // «Atrasado en agenda» (SE-08): el día en que estaba programado ya pasó sin hacerse; se cuenta en días hábiles. No es la «Vencida» de la entrega al cliente.
        boolean atrasadoAgenda,
        int diasHabilesAtrasoAgenda
) {
    public AsignacionResponse conRiesgo(boolean califica, String nivel, BigDecimal puntaje, boolean critico) {
        return new AsignacionResponse(id, idServicio, nombresEvaluado, apellidosEvaluado, cedulaEvaluado, cargo, proceso,
                idTipoProgreso, subproceso, requiereDocumento, rolResponsable, duracionMinutos, estado, idUsuarioAsignado,
                nombreAsignado, fechaProgramada, fechaAsignacion, fechaCompletado, fechaPublicado, fechaEntregaEstimada,
                observaciones, idCiudad, nombreCiudad, estadoConfirmacion, notasConfirmacion, idProgramadorResponsable,
                nombreProgramadorResponsable, versionPlantilla, versionPlantillaActual, lugar,
                documentosPorRevisar, documentosDevueltos, novedadesAbiertas, califica, nivel, puntaje, critico,
                iniciado, devuelto, reprogramaciones, tiempo, evaluadoAvisado, noHaLlegadoDisponible, noSePresentoDisponible, porConfirmar, atrasadoAgenda, diasHabilesAtrasoAgenda);
    }

    public AsignacionResponse conIndicadores(int documentosPorRevisar, int documentosDevueltos, int novedadesAbiertas) {
        return new AsignacionResponse(id, idServicio, nombresEvaluado, apellidosEvaluado, cedulaEvaluado, cargo, proceso,
                idTipoProgreso, subproceso, requiereDocumento, rolResponsable, duracionMinutos, estado, idUsuarioAsignado,
                nombreAsignado, fechaProgramada, fechaAsignacion, fechaCompletado, fechaPublicado, fechaEntregaEstimada,
                observaciones, idCiudad, nombreCiudad, estadoConfirmacion, notasConfirmacion, idProgramadorResponsable,
                nombreProgramadorResponsable, versionPlantilla, versionPlantillaActual, lugar,
                documentosPorRevisar, documentosDevueltos, novedadesAbiertas,
                riesgoCalifica, riesgoNivel, riesgoPuntaje, riesgoCritico,
                iniciado, devuelto, reprogramaciones, tiempo, evaluadoAvisado, noHaLlegadoDisponible, noSePresentoDisponible, porConfirmar, atrasadoAgenda, diasHabilesAtrasoAgenda);
    }
}
