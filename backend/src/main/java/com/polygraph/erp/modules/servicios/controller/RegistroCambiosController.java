package com.polygraph.erp.modules.servicios.controller;

import com.polygraph.erp.modules.servicios.dto.RegistroCambioResponse;
import com.polygraph.erp.modules.servicios.service.RegistroCambiosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta del registro de cambios de estado (solo lectura, administrador). Las fechas salen en hora de Colombia. */
@RestController
@RequestMapping("/api/v1/admin/registro-cambios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class RegistroCambiosController {

    private final RegistroCambiosService service;

    @GetMapping
    public ResponseEntity<List<RegistroCambioResponse>> listar(@RequestParam(required = false) Integer idServicio,
                                                               @RequestParam(required = false) Long idSubproceso) {
        var filas = idSubproceso != null ? service.porSubproceso(idSubproceso)
                : idServicio != null ? service.porServicio(idServicio) : List.<com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso>of();
        return ResponseEntity.ok(filas.stream().map(RegistroCambioResponse::de).toList());
    }
}
