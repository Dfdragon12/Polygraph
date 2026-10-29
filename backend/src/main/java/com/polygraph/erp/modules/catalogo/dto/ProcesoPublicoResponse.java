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
        boolean ciudadEsDePrueba
) {}
