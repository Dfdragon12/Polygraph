package com.polygraph.erp.modules.evaluados.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.evaluados.dto.CandidatoHojaVidaResponse;
import com.polygraph.erp.modules.evaluados.dto.GuardarTablaFijaRequest;
import com.polygraph.erp.modules.evaluados.dto.TablaFijaEditableResponse;
import com.polygraph.erp.modules.evaluados.service.CandidatoHojaVidaService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Hoja de vida REAL del candidato (referencias, educación, experiencia laboral, inactividades)
 *  — analista/poligrafista/visitador la ven y la corrigen/amplían aquí; gestor/admin en modo
 *  supervisión. Un cambio aquí es visible en cualquier otro servicio de esa misma persona — no es
 *  una copia por servicio (ver FormularioService, que solo la MUESTRA intercalada). */
@RestController
@RequestMapping("/api/v1/gestor/candidatos/{idCandidato}/hoja-vida")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR', 'GESTOR', 'PROGRAMADOR', 'ADMIN_POLYGRAPH')")
public class CandidatoHojaVidaController {

    private final CandidatoHojaVidaService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<CandidatoHojaVidaResponse> obtener(@PathVariable Long idCandidato, Authentication auth) {
        return ResponseEntity.ok(service.obtener(idCandidato, resolverUsuario(auth)));
    }

    @PutMapping("/{clave}")
    public ResponseEntity<TablaFijaEditableResponse> guardarTabla(
            @PathVariable Long idCandidato, @PathVariable String clave,
            @Valid @RequestBody GuardarTablaFijaRequest req, Authentication auth) {
        return ResponseEntity.ok(service.guardarTabla(idCandidato, clave, req, resolverUsuario(auth)));
    }

    private Usuario resolverUsuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
