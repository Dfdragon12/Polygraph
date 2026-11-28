package com.polygraph.erp.modules.solicitudes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudClienteResponse;
import com.polygraph.erp.modules.solicitudes.dto.TemaPoligrafiaRequest;
import com.polygraph.erp.modules.solicitudes.service.SolicitudExtraService;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin o gestor del cliente cambian los temas a evaluar de una poligrafía ya solicitada (por ejemplo, al escoger un grupo completo del catálogo).
 *  Se llega por el subproceso porque es lo que tiene a mano el formulario. */
@Slf4j
@RestController
@RequestMapping("/api/v1/gestor/asignaciones")
@RequiredArgsConstructor
public class TemasSolicitudController {

    private final SolicitudExtraService solicitudExtraService;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final UsuarioRepository usuarioRepository;

    @PutMapping("/{id}/temas-poligrafia")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH','GESTOR')")
    public ResponseEntity<SolicitudClienteResponse> reemplazar(@PathVariable Long id, @RequestBody List<TemaPoligrafiaRequest> temas, Authentication auth) {
        Usuario actor = usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        ServicioSubproceso subproceso = subprocesoRepository.findById(id).orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        Servicio servicio = subproceso.getServicio();
        AccesoGestor.validar(servicio, actor);
        log.info("Temas de poligrafía del servicio {} reemplazados por {} ({} temas)", servicio.getIdServicio(), actor.getEmail(), temas == null ? 0 : temas.size());
        return ResponseEntity.ok(solicitudExtraService.reemplazarTemas(servicio, temas));
    }
}
