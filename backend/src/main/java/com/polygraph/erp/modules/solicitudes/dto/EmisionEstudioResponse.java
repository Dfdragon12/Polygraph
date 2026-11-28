package com.polygraph.erp.modules.solicitudes.dto;

/** PB-03: la versión que se acaba de emitir y el estado de su PDF (GENERANDO hasta que la cola lo arma). */
public record EmisionEstudioResponse(Integer version, String tipo, String estadoPdf) {}
