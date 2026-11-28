package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.dto.CitaNoRealizadaRequest;
import com.polygraph.erp.modules.gestor.dto.ReasignarCitaRequest;
import com.polygraph.erp.modules.gestor.dto.ReprogramarCitaRequest;
import com.polygraph.erp.modules.gestor.dto.ResumenCitasResponse;
import com.polygraph.erp.modules.gestor.service.CitaSubprocesoService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Confirmación, no realización, reprogramación y reasignación de citas de poligrafía, visita y VerifEye (SE-07). */
@RestController
@RequestMapping("/api/v1/gestor/asignaciones")
@RequiredArgsConstructor
public class CitaSubprocesoController {

    private final CitaSubprocesoService service;
    private final UsuarioRepository usuarioRepository;

    /** El profesional inicia su informe: confirma que la cita se realizó. */
    @PatchMapping("/{id}/iniciar-informe")
    @PreAuthorize("hasAnyRole('POLIGRAFISTA','VISITADOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<AsignacionResponse> iniciarInforme(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.iniciarInforme(id, usuario(auth)));
    }

    /** «El evaluado no ha llegado»: avisa al programador. */
    @PatchMapping("/{id}/evaluado-no-ha-llegado")
    @PreAuthorize("hasAnyRole('POLIGRAFISTA','VISITADOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<AsignacionResponse> evaluadoNoHaLlegado(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.evaluadoNoHaLlegado(id, usuario(auth)));
    }

    /** La cita no se realizó: pasa a REPROGRAMADO. */
    @PatchMapping("/{id}/cita-no-realizada")
    @PreAuthorize("hasAnyRole('POLIGRAFISTA','VISITADOR','PROGRAMADOR','GESTOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<AsignacionResponse> citaNoRealizada(@PathVariable Long id, @Valid @RequestBody CitaNoRealizadaRequest request, Authentication auth) {
        return ResponseEntity.ok(service.citaNoRealizada(id, request, usuario(auth)));
    }

    /** Reprograma con el mismo profesional. */
    @PatchMapping("/{id}/reprogramar-cita")
    @PreAuthorize("hasAnyRole('PROGRAMADOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<AsignacionResponse> reprogramar(@PathVariable Long id, @Valid @RequestBody ReprogramarCitaRequest request, Authentication auth) {
        return ResponseEntity.ok(service.reprogramar(id, request, usuario(auth)));
    }

    /** Reasigna a otro profesional. */
    @PatchMapping("/{id}/reasignar-cita")
    @PreAuthorize("hasAnyRole('PROGRAMADOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<AsignacionResponse> reasignar(@PathVariable Long id, @Valid @RequestBody ReasignarCitaRequest request, Authentication auth) {
        return ResponseEntity.ok(service.reasignar(id, request, usuario(auth)));
    }

    /** La bandeja «Por confirmar». */
    @GetMapping("/citas/por-confirmar")
    @PreAuthorize("hasAnyRole('POLIGRAFISTA','VISITADOR','PROGRAMADOR','GESTOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<List<AsignacionResponse>> porConfirmar(Authentication auth) {
        return ResponseEntity.ok(service.porConfirmar(usuario(auth)));
    }

    /** Los contadores «Por confirmar» y «Por reprogramar». */
    @GetMapping("/citas/resumen")
    @PreAuthorize("hasAnyRole('POLIGRAFISTA','VISITADOR','PROGRAMADOR','GESTOR','ADMIN_POLYGRAPH')")
    public ResponseEntity<ResumenCitasResponse> resumen(Authentication auth) {
        return ResponseEntity.ok(service.resumen(usuario(auth)));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
