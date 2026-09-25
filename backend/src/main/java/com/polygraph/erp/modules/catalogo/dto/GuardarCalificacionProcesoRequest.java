package com.polygraph.erp.modules.catalogo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Se guarda todo junto (pesos + obligatorios + cobertura + criticidad) para que la suma se
 *  valide de una vez y nunca quede un estado a medias. peso null = sin configurar. */
public record GuardarCalificacionProcesoRequest(
        @NotBlank(message = "Elige la criticidad por defecto.") String criticidadDefecto,
        @NotNull(message = "Indica la cobertura mínima.")
        @DecimalMin(value = "0", message = "La cobertura mínima no puede ser negativa.")
        @DecimalMax(value = "100", message = "La cobertura mínima no puede pasar de 100 %.") BigDecimal coberturaMinima,
        @NotNull @Valid List<PesoSubproceso> subprocesos
) {
    public record PesoSubproceso(
            @NotNull Integer idPaso,
            @DecimalMin(value = "0", message = "El peso no puede ser negativo.")
            @DecimalMax(value = "100", message = "Un peso no puede pasar de 100 %.")
            @Digits(integer = 3, fraction = 2, message = "El peso admite máximo 2 decimales.") BigDecimal peso,
            Boolean obligatorio) {}
}
