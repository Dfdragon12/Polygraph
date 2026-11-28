package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// idToken: el credential JWT que entrega Google Identity Services en el navegador — se verifica
// contra Google del lado del servidor, nunca se confía en el email que mande el frontend directo.
// Los campos de aceptación solo se exigen si la cuenta es NUEVA (al iniciar sesión no aplican).
public record GoogleAuthRequest(
        @NotBlank String idToken,
        Boolean aceptaTerminos,
        Boolean aceptaPrivacidad,
        Boolean aceptaMarketing,
        @Size(max = 20) String versionLegal
) {
    public GoogleAuthRequest(String idToken) {
        this(idToken, null, null, null, null);
    }
}
