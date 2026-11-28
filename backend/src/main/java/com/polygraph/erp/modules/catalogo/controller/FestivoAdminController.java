package com.polygraph.erp.modules.catalogo.controller;

import com.polygraph.erp.modules.catalogo.service.FestivoAdminService;
import com.polygraph.erp.modules.catalogo.service.FestivoAdminService.FestivoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Festivos de Colombia que usa el cálculo de días hábiles. Solo el administrador los ajusta. */
@RestController
@RequestMapping("/api/v1/admin/festivos")
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
@RequiredArgsConstructor
public class FestivoAdminController {

    private final FestivoAdminService servicio;

    @GetMapping
    public ResponseEntity<List<FestivoDto>> listar(@RequestParam(defaultValue = "0") int anio) {
        return ResponseEntity.ok(servicio.listar(anio == 0 ? LocalDate.now().getYear() : anio));
    }

    /** Body: { "activo": true|false, "descripcion": "..." } (la descripción solo se usa al agregar un día propio). */
    @PutMapping("/{fecha}")
    public ResponseEntity<FestivoDto> cambiar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                              @RequestBody Map<String, Object> body, Authentication auth) {
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        String descripcion = body.get("descripcion") != null ? String.valueOf(body.get("descripcion")) : null;
        return ResponseEntity.ok(servicio.cambiar(fecha, activo, descripcion, auth.getName()));
    }
}
