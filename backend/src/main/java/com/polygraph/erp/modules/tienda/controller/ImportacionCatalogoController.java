package com.polygraph.erp.modules.tienda.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygraph.erp.modules.tienda.service.ImportacionCatalogoService;
import com.polygraph.erp.modules.tienda.service.ImportacionCatalogoService.Resultado;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Importa precios y textos del JSON que genera la página «Precios del catálogo Polygraph» (TP-05). Solo el administrador.
 * `modo=simulacion` (por defecto) no guarda nada; `modo=aplicar` guarda todo en una transacción o nada.
 */
@RestController
@RequestMapping("/api/v1/admin/catalogo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
public class ImportacionCatalogoController {

    private final ImportacionCatalogoService service;

    @PostMapping("/importar-precios")
    public ResponseEntity<Resultado> importar(@RequestBody JsonNode body,
                                              @RequestParam(defaultValue = "simulacion") String modo,
                                              Authentication auth) {
        boolean aplicar;
        if ("aplicar".equalsIgnoreCase(modo)) aplicar = true;
        else if ("simulacion".equalsIgnoreCase(modo)) aplicar = false;
        else throw new ApiException("modo debe ser «simulacion» o «aplicar»", HttpStatus.BAD_REQUEST);

        Resultado r = service.importar(body, aplicar, auth.getName());
        // Aplicar con errores: no se importó nada; 422 con el detalle de cuál falló y por qué.
        if (aplicar && !r.aplicado()) return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(r);
        return ResponseEntity.ok(r);
    }
}
