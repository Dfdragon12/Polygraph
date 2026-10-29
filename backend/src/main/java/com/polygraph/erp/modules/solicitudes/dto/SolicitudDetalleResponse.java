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
        List<SubprocesoResponse> subprocesos,
        // Solo cuando la solicitud está PUBLICADA y el riesgo quedó calculado; null si no.
        ResultadoRiesgoResponse riesgo,
        // Novedades de tipos marcados "visible para el cliente", la más reciente primero.
        List<NovedadClienteResponse> novedades,
        EstadoCandidatoResponse estadoCandidato,
        // Qué documentos pide el estudio y cuáles ya cargó el candidato — sin ruta ni descarga.
        List<DocumentoCandidatoResponse> documentosCandidato,
        // El cliente puede cancelar solo antes de que empiece la ejecución (PENDIENTE/PROGRAMANDO).
        boolean puedeCancelar,
        CobroResponse cobro,
        // Estado de tiempo en días hábiles (null si está cancelada o sin fecha).
        com.polygraph.erp.shared.dto.TiempoServicio tiempo
) {
    /** Cuánto le vale la solicitud al cliente.
     *  POSPAGO: valores exactos del cargo de esta solicitud (movimientos_credito_pospago) y su factura.
     *  PREPAGO: valorReferencia = true — la solicitud descontó 1 unidad de la bolsa; el valor es el
     *  unitario de la última compra aprobada de ese servicio (con su descuento, si tuvo). Los
     *  recargos por ciudad van aparte (pagado y pendiente). */
    public record CobroResponse(
            String modalidad,
            BigDecimal valorServicio,
            BigDecimal valorSinDescuento,
            String descuento,
            BigDecimal recargoPagado,
            BigDecimal recargoPendiente,
            BigDecimal total,
            boolean valorReferencia,
            String referencia,          // factura (pospago) u orden de compra (prepago)
            String estadoPago,          // PENDIENTE/PAGADA/VENCIDA (factura) · APROBADA (compra) · SIN_FACTURAR
            LocalDate fechaVencimiento,
            LocalDateTime fechaPago) {}

    /** estadoRevision: PENDIENTE · APROBADO · DENEGADO · ILEGIBLE · VENCIDO · INCOMPLETO · NO_CORRESPONDE
     *  (null si no lo ha cargado). observacionRevision: la misma que ve el candidato en su portal. */
    public record DocumentoCandidatoResponse(String nombre, boolean requerido, boolean obligatorio,
                                             boolean cargado, LocalDateTime fechaCarga,
                                             String estadoRevision, String observacionRevision) {}

    /** Lo que ve el cliente de una novedad: tipo, subproceso, estado y la respuesta de quien la
     *  resolvió (y la nueva fecha si se reprogramó). Sin la descripción del ejecutor. */
    public record NovedadClienteResponse(
            Long id,
            String subproceso,
            String tipo,
            String categoria,
            String color,
            String estado,
            // Solo en novedades automáticas de documento (DOCUMENTO_*): qué documento y la observación
            // que también ve el candidato. En las demás es null (la descripción del ejecutor es interna).
            String detalle,
            LocalDateTime fechaRegistro,
            String respuesta,
            LocalDateTime fechaResolucion,
            LocalDateTime reprogramadoPara) {}

    /** ¿El candidato ya diligenció su hoja de vida? COMPLETADO | PENDIENTE (link vigente, vence
     *  en venceEl) | VENCIDO (link vencido sin terminar) | SIN_LINK. */
    public record EstadoCandidatoResponse(String estado, LocalDateTime venceEl, Integer porcentaje, Integer pasoActual) {}

    /** Lo único del motor de riesgo que ve el cliente (docs/modelo-calificacion-riesgo.md §7):
     *  el nivel que vale (automático o cambiado por el gestor) y el puntaje final. */
    public record ResultadoRiesgoResponse(String nivel, BigDecimal puntaje) {}

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
    // informeDisponible: la solicitud está PUBLICADA, el subproceso aprobado y tiene documento —
    // el cliente puede descargar su PDF (GET /requests/{id}/informes/{idSubproceso}/pdf).
    public record SubprocesoResponse(
            Long id,
            String nombre,
            String estado,
            LocalDateTime fechaProgramada,
            boolean informeDisponible
    ) {}
}
