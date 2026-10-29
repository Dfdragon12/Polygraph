package com.polygraph.erp.modules.gestor.dto;

/** Los contadores de citas de quien las coordina o las hace: {@code porConfirmar} (bandeja «Por confirmar») y {@code porReprogramar} (subprocesos en REPROGRAMADO). */
public record ResumenCitasResponse(long porConfirmar, long porReprogramar) {}
