package com.polygraph.erp.modules.auth.controller;

import com.polygraph.erp.modules.auth.dto.*;
import com.polygraph.erp.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register/natural")
    public ResponseEntity<Void> registrarNatural(@Valid @RequestBody RegistroNaturalRequest request) {
        authService.registrarNatural(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/register/juridica")
    public ResponseEntity<Void> registrarJuridica(@Valid @RequestBody RegistroJuridicaRequest request) {
        authService.registrarJuridica(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activar(@RequestParam String token) {
        authService.activarCuenta(token);
        return ResponseEntity.ok().build();
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
}
