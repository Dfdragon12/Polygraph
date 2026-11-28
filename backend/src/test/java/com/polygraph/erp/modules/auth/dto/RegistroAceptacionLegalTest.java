package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistroAceptacionLegalTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private RegistroNaturalRequest con(Boolean terminos, Boolean privacidad, Boolean marketing) {
        return new RegistroNaturalRequest("Ana", "Pérez", "ana@test.com", "Password1!", null,
                terminos, privacidad, marketing, "1.0");
    }

    @Test
    @DisplayName("Sin aceptar términos o privacidad el registro se rechaza")
    void sin_aceptar_se_rechaza() {
        assertThat(validador.validate(con(false, true, null))).hasSize(1);
        assertThat(validador.validate(con(true, false, null))).hasSize(1);
        assertThat(validador.validate(con(null, null, null))).hasSize(2);
    }

    @Test
    @DisplayName("Aceptando términos y privacidad es válido; el marketing es opcional")
    void aceptando_es_valido() {
        assertThat(validador.validate(con(true, true, null))).isEmpty();
        assertThat(validador.validate(con(true, true, false))).isEmpty();
    }
}
