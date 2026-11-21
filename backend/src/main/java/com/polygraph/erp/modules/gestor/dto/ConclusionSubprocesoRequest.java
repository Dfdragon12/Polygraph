package com.polygraph.erp.modules.gestor.dto;

import jakarta.validation.constraints.Size;

/** PB-02: la conclusión que escribe quien ejecuta el subproceso (la ve el gestor al publicar). */
public record ConclusionSubprocesoRequest(@Size(max = 4000) String conclusion) {}
