package com.polygraph.erp.modules.gestor.dto;

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
        Integer versionPlantillaActual
) {}
