package com.polygraph.erp.modules.gestor.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.DiaNoHabil;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.DiaPersona;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.EquipoHoy;
import com.polygraph.erp.modules.gestor.dto.AsignacionResponse;
import com.polygraph.erp.modules.gestor.service.AgendaEquipoService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** La Agenda del equipo (SE-08): equipo hoy, el día de una persona, lo atrasado en agenda y los días que no se trabaja. */
@RestController
@RequestMapping("/api/v1/gestor/agenda")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR','PROGRAMADOR','ADMIN_POLYGRAPH')")
public class AgendaEquipoController {

    private final AgendaEquipoService service;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/equipo")
    public ResponseEntity<EquipoHoy> equipo(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                            @RequestParam(required = false) Rol rol, Authentication auth) {
        return ResponseEntity.ok(service.equipoHoy(usuario(auth), fecha, rol));
    }

    @GetMapping("/equipo/{idUsuario}/dia")
    public ResponseEntity<DiaPersona> dia(@PathVariable Long idUsuario,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, Authentication auth) {
        return ResponseEntity.ok(service.diaDePersona(usuario(auth), idUsuario, fecha));
    }

    /** La bandeja «Para reprogramar»: atrasado en agenda. */
    @GetMapping("/para-reprogramar")
    public ResponseEntity<List<AsignacionResponse>> paraReprogramar(Authentication auth) {
        return ResponseEntity.ok(service.paraReprogramar(usuario(auth)));
    }

    @GetMapping("/dias-no-habiles")
    public ResponseEntity<List<DiaNoHabil>> diasNoHabiles(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(service.diasNoHabiles(desde, hasta));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
