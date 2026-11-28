package com.polygraph.erp.modules.gestor.dto;

/** PB-02: lo que el gestor confirma al publicar un subproceso. {@code revisado} debe ser true; {@code nivel} es BAJO/MEDIO/ALTO (null = la sugerida);
 *  si cambia la sugerida, {@code motivoNivel} es obligatorio; si cambia la conclusión, queda la original en el historial. */
public record PublicarSubprocesoRequest(String conclusion, String nivel, String motivoNivel, boolean revisado) {}
