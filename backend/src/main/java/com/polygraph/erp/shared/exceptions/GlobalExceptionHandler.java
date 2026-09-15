package com.polygraph.erp.shared.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> manejarApiException(ApiException ex) {
        log.warn("ApiException [{}]: {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(construirRespuesta(ex.getMessage(), ex.getStatus()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Campo inválido",
                        (e1, e2) -> e1
                ));
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("status", HttpStatus.BAD_REQUEST.value());
        cuerpo.put("mensaje", "Errores de validación");
        cuerpo.put("errores", errores);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> manejarCredencialesInvalidas(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(construirRespuesta("Email o contraseña incorrectos", HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> manejarCuentaDeshabilitada(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(construirRespuesta("Cuenta no activada. Revisa tu correo electrónico.", HttpStatus.FORBIDDEN));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, Object>> manejarCuentaBloqueada(LockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(construirRespuesta("Cuenta bloqueada. Contacta al administrador.", HttpStatus.FORBIDDEN));
    }

    // Respaldo genérico ante CUALQUIER violación de integridad en BD (unique, not-null, check,
    // foreign key...) — cubre, por ejemplo, el caso raro de dos solicitudes simultáneas que pasan
    // la validación de "email disponible" antes de que cualquiera de las dos inserte. El mensaje
    // al usuario se queda neutral a propósito: este handler dispara para CUALQUIER tabla del
    // sistema, no solo usuarios/email, así que un texto fijo tipo "el correo ya está en uso"
    // confunde en cualquier otro contexto (ej. admin/catálogo → Plantilla). La causa real sí se
    // deja completa en el log (getMostSpecificCause trae el mensaje nativo del driver de
    // Postgres — qué constraint/columna exactamente) para poder diagnosticar sin adivinar.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarViolacionIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(construirRespuesta("Ya existe un registro con esos datos, o falta completar un campo obligatorio.", HttpStatus.CONFLICT));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(construirRespuesta("El archivo supera el tamaño máximo permitido (10MB)", HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarErrorGeneral(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(construirRespuesta("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private Map<String, Object> construirRespuesta(String mensaje, HttpStatus status) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("mensaje", mensaje);
        return cuerpo;
    }
}
