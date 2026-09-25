package com.polygraph.erp.modules.riesgo.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Riesgo de UN subproceso, para el encabezado del formulario. Quien lo diligencia ve solo su
 *  subproceso; el gestor del cliente y el admin ven además el estudio (reglas en el servicio). */
@RestController
@RequestMapping("/api/v1/riesgo/subprocesos/{idServicioSubproceso}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
public class RiesgoSubprocesoController {

    private final CalificacionRiesgoService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/resumen")
    public ResponseEntity<CalificacionRiesgoService.ResumenSubproceso> resumen(@PathVariable Long idServicioSubproceso,
                                                                               Authentication auth) {
        Usuario actor = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(service.resumenSubproceso(idServicioSubproceso, actor));
    }
}
