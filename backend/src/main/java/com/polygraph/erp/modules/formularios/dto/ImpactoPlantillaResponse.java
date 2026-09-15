package com.polygraph.erp.modules.formularios.dto;

import java.util.List;

/** Qué secciones/columnas con datos guardados se archivarían si se confirma este guardado —
 *  para mostrarle una alerta al administrador ANTES de guardar de verdad. Vacío = no hay
 *  ningún riesgo, se puede guardar directo. */
public record ImpactoPlantillaResponse(List<String> secciones, List<String> columnas) {}
