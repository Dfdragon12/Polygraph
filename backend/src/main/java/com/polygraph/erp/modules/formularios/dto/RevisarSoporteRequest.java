package com.polygraph.erp.modules.formularios.dto;

import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Aprobar (APROBADO) o devolver (DEVUELTO, con observación obligatoria) un soporte del candidato. */
public record RevisarSoporteRequest(
        @NotNull(message = "Indica si se aprueba o se devuelve") EstadoRevisionDocumento estado,
        @Size(max = 1000, message = "La observación puede tener hasta 1000 caracteres") String observacion
) {}
