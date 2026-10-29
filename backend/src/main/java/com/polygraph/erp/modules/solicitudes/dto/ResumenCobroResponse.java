package com.polygraph.erp.modules.solicitudes.dto;

import java.math.BigDecimal;

/**
 * NS-11: lo que el paso 3 de Nueva solicitud dice sobre el cobro, calculado en el servidor con las mismas reglas con las que luego se descuenta
 * (prepago: {@code BolsaPrepagoService}, NS-01/NS-03; pospago: precio del servicio + recargo por ciudad contra el cupo). La pantalla solo lo muestra.
 *
 * Prepago: disponiblesAhora / quedaran (unidades). Pospago: valor, recargoCiudad, cupoDisponible, cupoQuedara (pesos). alcanza = false si no hay con qué.
 */
public record ResumenCobroResponse(
        String tipoCliente,
        String servicio,
        String categoria,
        boolean alcanza,
        String mensaje,
        Integer disponiblesAhora,
        Integer quedaran,
        BigDecimal valor,
        BigDecimal recargoCiudad,
        BigDecimal cupoDisponible,
        BigDecimal cupoQuedara
) {}
