package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.pagos.entity.OrdenCompra;

/** Abstrae la pasarela de pago usada para cobrar una orden de compra. */
public interface PasarelaPago {

    IniciarPagoResultado iniciarPago(OrdenCompra orden);

    record IniciarPagoResultado(String urlCheckout, boolean simulado) {}
}
