package com.polygraph.erp.modules.riesgo.dto;

import com.polygraph.erp.modules.riesgo.entity.CentralRiesgo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Centrales de riesgo y las bandas de su indicador de pago (Admin → Calificación de riesgo → Centrales de riesgo). */
public final class CentralRiesgoDtos {

    private CentralRiesgoDtos() {}

    public record BandaDto(@NotNull Integer desde, @NotNull Integer hasta, @NotBlank @Size(max = 50) String nombre) {}

    public record CentralRequest(@NotBlank @Size(max = 100) String nombre, Boolean activo,
                                 @NotNull @Size(min = 4, max = 4, message = "Son 4 bandas.") @Valid List<BandaDto> bandas) {}

    public record CentralResponse(Integer idCentral, String nombre, boolean activo, List<BandaDto> bandas) {
        public static CentralResponse de(CentralRiesgo c) {
            return new CentralResponse(c.getIdCentral(), c.getNombre(), Boolean.TRUE.equals(c.getActivo()),
                    c.getBandas().stream().map(b -> new BandaDto(b.getDesde(), b.getHasta(), b.getNombre())).toList());
        }
    }
}
