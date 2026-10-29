package com.polygraph.erp.modules.servicios.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.dto.SemaforoResponse;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Filtros;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Solicitante;
import com.polygraph.erp.modules.servicios.service.SemaforoEstadoService;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El semáforo de estado de todos los perfiles (SE-04): los contadores y la lista filtrada salen de aquí. Mismos filtros en los dos:
 * tiempo, cliente, responsable, rol y tipo de subproceso (cliente y responsable solo los usan gestor y administrador).
 */
@RestController
@RequestMapping("/api/v1/semaforo")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_CLIENTE','ANALISTA_CLIENTE','GESTOR','ADMIN_POLYGRAPH','ANALISTA_INTERNO','POLIGRAFISTA','VISITADOR','PROGRAMADOR')")
public class SemaforoController {

    private static final int MAX_POR_PAGINA = 100;

    private final SemaforoEstadoService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<SemaforoResponse> resumen(
            @RequestParam(required = false) EstadoTiempo tiempo,
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) Long idResponsable,
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) Integer idTipoProgreso,
            Authentication auth) {
        return ResponseEntity.ok(service.resumen(solicitante(auth), new Filtros(tiempo, idCliente, idResponsable, rol, idTipoProgreso)));
    }

    @GetMapping("/alertas")
    public ResponseEntity<com.polygraph.erp.modules.servicios.dto.AlertasResponse> alertas(Authentication auth) {
        return ResponseEntity.ok(service.alertas(solicitante(auth), Filtros.ninguno()));
    }

    @GetMapping("/servicios")
    public ResponseEntity<Page<ServicioSemaforoFila>> servicios(
            @RequestParam(required = false) EstadoSubproceso estado,
            @RequestParam(required = false) EstadoTiempo tiempo,
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) Long idResponsable,
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) Integer idTipoProgreso,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        int tamano = Math.max(1, Math.min(size, MAX_POR_PAGINA));
        return ResponseEntity.ok(service.servicios(solicitante(auth),
                new Filtros(tiempo, idCliente, idResponsable, rol, idTipoProgreso), estado, PageRequest.of(Math.max(0, page), tamano)));
    }

    private Solicitante solicitante(Authentication auth) {
        Usuario u = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return new Solicitante(u.getRol(), u.getIdUsuario(), u.getIdCliente());
    }
}
