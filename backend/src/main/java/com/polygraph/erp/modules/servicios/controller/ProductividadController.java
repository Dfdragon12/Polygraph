package com.polygraph.erp.modules.servicios.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.dto.ProductividadResponse;
import com.polygraph.erp.modules.servicios.service.ProductividadExcel;
import com.polygraph.erp.modules.servicios.service.ProductividadService;
import com.polygraph.erp.modules.servicios.service.ProductividadService.Filtros;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reporte diario de productividad (SE-11). Administrador: todo; gestor: los clientes que atiende; analista, poligrafista, visitador y programador: lo suyo.
 * Filtros: fecha o rango (máximo {@value ProductividadService#MAX_DIAS} días), perfil, persona, tipo de subproceso y cliente. Exporta a Excel.
 */
@RestController
@RequestMapping("/api/v1/reportes/productividad")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH','GESTOR','ANALISTA_INTERNO','POLIGRAFISTA','VISITADOR','PROGRAMADOR')")
public class ProductividadController {

    private final ProductividadService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<ProductividadResponse> reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Rol perfil,
            @RequestParam(required = false) Long idPersona,
            @RequestParam(required = false) Integer idTipoProgreso,
            @RequestParam(required = false) Integer idCliente,
            Authentication auth) {
        return ResponseEntity.ok(service.reporte(usuario(auth), new Filtros(desde, hasta, perfil, idPersona, idTipoProgreso, idCliente)));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Rol perfil,
            @RequestParam(required = false) Long idPersona,
            @RequestParam(required = false) Integer idTipoProgreso,
            @RequestParam(required = false) Integer idCliente,
            Authentication auth) {
        ProductividadResponse r = service.reporte(usuario(auth), new Filtros(desde, hasta, perfil, idPersona, idTipoProgreso, idCliente));
        String nombre = "productividad_" + r.desde() + (r.desde().equals(r.hasta()) ? "" : "_a_" + r.hasta()) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ProductividadExcel.generar(r));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
