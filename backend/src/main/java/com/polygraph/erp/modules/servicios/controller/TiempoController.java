package com.polygraph.erp.modules.servicios.controller;

import com.polygraph.erp.modules.servicios.service.ResumenTiempoService;
import com.polygraph.erp.modules.servicios.service.ResumenTiempoService.Resumen;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** El resumen de tiempos que usan TODOS los tableros. Cada rol ve solo lo suyo (ver ResumenTiempoService.resumirPara). */
@RestController
@RequestMapping("/api/v1/tiempos")
@RequiredArgsConstructor
public class TiempoController {

    private final ResumenTiempoService resumenService;

    /** idCliente / idGestor: filtros (un cliente o un gestor solo pueden pedir lo suyo). desde / hasta (AAAA-MM-DD): rango de fechas de la
     *  solicitud (y de la entrega real en el cumplimiento). top: cuántas de las más atrasadas (1–50, por defecto 10). */
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    public ResponseEntity<Resumen> resumen(
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) Long idGestor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer top,
            Authentication auth) {
        return ResponseEntity.ok(resumenService.resumirPara(auth.getName(), idCliente, idGestor, desde, hasta, top));
    }

    /** Las solicitudes detrás de un número del resumen (una tarjeta o una celda de la matriz): mismos filtros y misma autorización que el resumen.
     *  etapa: PENDIENTE, PROGRAMANDO...; estadoTiempo: uno o varios separados por coma. De la más atrasada a la menos. */
    @GetMapping("/solicitudes")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    public ResponseEntity<org.springframework.data.domain.Page<ResumenTiempoService.FilaSolicitud>> solicitudes(
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) Long idGestor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) com.polygraph.erp.shared.enums.EstadoServicio etapa,
            @RequestParam(required = false) java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> estadoTiempo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            Authentication auth) {
        return ResponseEntity.ok(resumenService.listarPara(auth.getName(), idCliente, idGestor, desde, hasta, etapa, estadoTiempo,
                org.springframework.data.domain.PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(50, tamano)))));
    }
}
