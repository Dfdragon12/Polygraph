package com.polygraph.erp.modules.formularios.dto;

/** Qué tan lleno está el formulario de un subproceso — solo cuenta los campos de secciones tipo
 *  CAMPOS (las de tipo TABLA no tienen un total fijo, el analista agrega filas libremente). */
public record ProgresoFormularioResponse(Integer camposLlenos, Integer camposTotal) {}
