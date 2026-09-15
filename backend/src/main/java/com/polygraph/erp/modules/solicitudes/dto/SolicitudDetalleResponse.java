package com.polygraph.erp.modules.solicitudes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record SolicitudDetalleResponse(
        Integer idServicio,
        String cedulaEvaluado,
        String nombresEvaluado,
        String apellidosEvaluado,
        String celularEvaluado,
        String emailEvaluado,
        String cargo,
        String notas,
        Integer idProceso,
        String proceso,
        String clasificacion,
        String estado,
        LocalDate fechaSolicitud,
        LocalTime horaSolicitud,
        LocalDate fechaEntregaEstimada,
        String linkEvaluado,
        List<HistorialResponse> historial,
        String nombreCliente,
        String nitCliente,
        String telefonoCliente,
        String emailCliente,
        Integer idCiudad,
        String nombreCiudad,
        BigDecimal montoRecargoCiudad,
        String estadoRecargoCiudad,
        BigDecimal totalPagadoRecargoCiudad,
        BigDecimal totalPendienteRecargoCiudad,
        List<RecargoCiudadMovimientoResponse> historialRecargoCiudad,
        List<SubprocesoResponse> subprocesos
) {
    public record HistorialResponse(
            String estadoAnterior,
            String estadoNuevo,
            LocalDateTime fechaCambio,
            String usuario,
            String observacion
    ) {}

    public record RecargoCiudadMovimientoResponse(
            String ciudad,
            BigDecimal monto,
            String motivo,
            String estado,
            LocalDateTime fechaGenerado,
            LocalDateTime fechaPagado
    ) {}

    // Resumen de cada subproceso del servicio (antecedentes, poligrafía, visita, etc.) — solo lo
    // que le sirve al cliente para hacer seguimiento: nombre, estado y cuándo está programado. No
    // se expone quién lo tiene asignado ni observaciones internas (eso es operativo, no del cliente).
    public record SubprocesoResponse(
            String nombre,
            String estado,
            LocalDateTime fechaProgramada
    ) {}
}
