package com.polygraph.erp.modules.riesgo.service;

/** Evento: algo cambió en un caso (se guardó un formulario, cambió el estado de un subproceso) y
 *  hay que recalcular su riesgo. Se atiende DESPUÉS del commit (ver CalificacionRiesgoService),
 *  así un error en el recálculo nunca revierte lo que el usuario guardó. */
public record RecalculoRiesgoSolicitado(Integer idServicio, Long idActor) {}
