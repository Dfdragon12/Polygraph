package com.polygraph.erp.modules.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProcesoPublicoResponse(
        Integer    idProceso,
        String     nombreProceso,
        String     descripcion,
        String     clasificacion,
        String     codigoClasificacion,
        BigDecimal valor,
        Integer    diasHabilesEntrega,
        List<TramoPrecioResponse> tramosPrecio,
        List<String> puntosClave,
        Boolean aplicaPrecioCiudad,
        List<PrecioCiudadResponse> preciosCiudad,
        // Formato adicional que se diligencia al solicitar este proceso (PRE_EMPLEO, RUTINA, ESPECIFICA) o null
        String formatoSolicitud,
        // El servicio es de poligrafía: el cliente puede elegir hasta 4 temas a evaluar
        boolean admitePreguntasPoligrafia,
        // NS-08: qué pide Nueva solicitud según el servicio
        boolean requiereDireccion,
        boolean ciudadEsDePrueba,
        // TP-01: textos comerciales (sin costoDirecto: nunca llega al cliente)
        String tituloComercial,
        String frasePrincipal,
        String descripcionCorta,
        List<String> incluye,
        String idealPara,
        String unidadSingular,
        String unidadPlural,
        boolean visibleEnTienda,
        boolean aplicaIva,
        // Planes con su precio por unidad y del paquete, SIN IVA
        List<com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse> planes,
        // Mayor descuento por volumen del servicio (Hasta X % menos); 0 si no hay
        java.math.BigDecimal descuentoMaximoPorcentaje
) {}
