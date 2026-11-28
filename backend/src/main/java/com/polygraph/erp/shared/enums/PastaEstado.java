package com.polygraph.erp.shared.enums;

/**
 * Indicador (NO es un estado) de la pasta final del estudio (PB-01): PENDIENTE (aún no se emite), PRELIMINAR (emitida con pendientes) o EMITIDA
 * (cierre final). «Estudio completo publicado» solo se dice cuando todos los subprocesos están PUBLICADOS y la pasta está EMITIDA.
 */
public enum PastaEstado {
    PENDIENTE, PRELIMINAR, EMITIDA
}
