package com.polygraph.erp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

// idToken: el credential JWT que entrega Google Identity Services en el navegador — se verifica
// contra Google del lado del servidor, nunca se confía en el email que mande el frontend directo.
public record GoogleAuthRequest(@NotBlank String idToken) {}
