package com.polygraph.erp.modules.solicitudes.controller;

import com.polygraph.erp.modules.catalogo.dto.PreguntaPoligrafiaResponse;
import com.polygraph.erp.modules.catalogo.service.PreguntaPoligrafiaService;
import com.polygraph.erp.modules.solicitudes.dto.FormatoSolicitudResponse;
import com.polygraph.erp.modules.solicitudes.formato.FormatoSolicitud;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Lo que el cliente necesita para dibujar el formulario de una poligrafía: los formatos adicionales
 *  con sus campos y los temas activos que puede pedir evaluar. */
@RestController
@RequestMapping("/api/v1/solicitudes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
public class FormatoSolicitudController {

    private final PreguntaPoligrafiaService preguntaService;

    @GetMapping("/formatos")
    public ResponseEntity<List<FormatoSolicitudResponse>> formatos() {
        return ResponseEntity.ok(Arrays.stream(FormatoSolicitud.values()).map(FormatoSolicitudResponse::de).toList());
    }

    @GetMapping("/temas-poligrafia")
    public ResponseEntity<List<PreguntaPoligrafiaResponse>> temas() {
        return ResponseEntity.ok(preguntaService.listarActivas());
    }
}
