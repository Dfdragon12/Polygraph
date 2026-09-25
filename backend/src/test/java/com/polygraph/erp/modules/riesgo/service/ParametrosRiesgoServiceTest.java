package com.polygraph.erp.modules.riesgo.service;

import com.polygraph.erp.modules.riesgo.dto.GuardarParametrosRiesgoRequest.Umbral;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ParametrosRiesgoService — coherencia de umbrales")
class ParametrosRiesgoServiceTest {

    @Test
    @DisplayName("Los umbrales de la especificación son válidos")
    void umbralesEspecificacion_validos() {
        assertThatCode(() -> ParametrosRiesgoService.validarUmbral(CriticidadCargo.ESTANDAR, u("30", "60", "31"))).doesNotThrowAnyException();
        assertThatCode(() -> ParametrosRiesgoService.validarUmbral(CriticidadCargo.SENSIBLE, u("25", "50", "26"))).doesNotThrowAnyException();
        assertThatCode(() -> ParametrosRiesgoService.validarUmbral(CriticidadCargo.CRITICO, u("20", "40", "21"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Límite de BAJO mayor o igual al de MEDIO se rechaza")
    void bajoMayorQueMedio_rechaza() {
        assertThatThrownBy(() -> ParametrosRiesgoService.validarUmbral(CriticidadCargo.ESTANDAR, u("60", "60", "61")))
                .isInstanceOf(ApiException.class).hasMessageContaining("Estandar");
    }

    @Test
    @DisplayName("Mínimo de MEDIO fuera del rango de MEDIO se rechaza")
    void minimoMedioFueraDeRango_rechaza() {
        assertThatThrownBy(() -> ParametrosRiesgoService.validarUmbral(CriticidadCargo.CRITICO, u("20", "40", "20")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ParametrosRiesgoService.validarUmbral(CriticidadCargo.CRITICO, u("20", "40", "41")))
                .isInstanceOf(ApiException.class);
    }

    private static Umbral u(String bajo, String medio, String minimo) {
        return new Umbral("X", new BigDecimal(bajo), new BigDecimal(medio), new BigDecimal(minimo));
    }
}
