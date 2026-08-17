package com.polygraph.erp.modules.evaluados.controller;

import com.polygraph.erp.modules.evaluados.dto.DocumentoEvaluadoResponse;
import com.polygraph.erp.modules.evaluados.dto.HojaVidaRequest;
import com.polygraph.erp.modules.evaluados.dto.LinkValidacionResponse;
import com.polygraph.erp.modules.evaluados.service.EvaluadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluees")
@RequiredArgsConstructor
public class EvaluadoController {

    private final EvaluadoService evaluadoService;

    @PostMapping("/link/{token}/validate")
    public ResponseEntity<LinkValidacionResponse> validarLink(@PathVariable String token) {
        return ResponseEntity.ok(evaluadoService.validarLink(token));
    }

    @GetMapping("/link/{token}")
    public ResponseEntity<HojaVidaRequest> obtenerFormulario(@PathVariable String token) {
        return ResponseEntity.ok(evaluadoService.obtenerFormulario(token));
    }

    @PostMapping("/link/{token}/progress")
    public ResponseEntity<Void> guardarProgreso(
            @PathVariable String token,
            @RequestBody HojaVidaRequest request) {
        evaluadoService.guardarProgreso(token, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/link/{token}/submit")
    public ResponseEntity<Void> enviarFormulario(
            @PathVariable String token,
            @Valid @RequestBody HojaVidaRequest request) {
        evaluadoService.enviarFormulario(token, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/link/{token}/documentos", consumes = "multipart/form-data")
    public ResponseEntity<DocumentoEvaluadoResponse> subirDocumento(
            @PathVariable String token,
            @RequestParam String tipoDocumento,
            @RequestParam MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evaluadoService.subirDocumento(token, tipoDocumento, archivo));
    }

    @GetMapping("/link/{token}/documentos")
    public ResponseEntity<List<DocumentoEvaluadoResponse>> listarDocumentos(@PathVariable String token) {
        return ResponseEntity.ok(evaluadoService.listarDocumentos(token));
    }

    @DeleteMapping("/link/{token}/documentos/{id}")
    public ResponseEntity<Void> eliminarDocumento(@PathVariable String token, @PathVariable Long id) {
        evaluadoService.eliminarDocumento(token, id);
        return ResponseEntity.noContent().build();
    }
}
