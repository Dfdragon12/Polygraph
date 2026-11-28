package com.polygraph.erp.modules.auth.controller;

import com.polygraph.erp.modules.auth.dto.*;
import com.polygraph.erp.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** IP del cliente (detrás de proxy, la primera de X-Forwarded-For); solo se guarda su hash. */
    private static String ipDe(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) return reenviada.split(",")[0].trim();
        return http.getRemoteAddr();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> continuarConGoogle(@Valid @RequestBody GoogleAuthRequest request,
                                                         HttpServletRequest http) {
        return ResponseEntity.ok(authService.continuarConGoogle(request, ipDe(http)));
    }

    @PostMapping("/register/natural")
    public ResponseEntity<Void> registrarNatural(@Valid @RequestBody RegistroNaturalRequest request,
                                                 HttpServletRequest http) {
        authService.registrarNatural(request, ipDe(http));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/register/juridica")
    public ResponseEntity<Void> registrarJuridica(@Valid @RequestBody RegistroJuridicaRequest request,
                                                   HttpServletRequest http) {
        authService.registrarJuridica(request, ipDe(http));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cambiar-password")
    public ResponseEntity<Void> cambiarPassword(
            @Valid @RequestBody CambiarPasswordRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.cambiarPassword(auth.getName(), request);
        return ResponseEntity.ok().build();
    }
}
