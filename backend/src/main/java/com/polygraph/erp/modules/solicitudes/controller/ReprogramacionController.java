package com.polygraph.erp.modules.solicitudes.controller;

import com.polygraph.erp.modules.servicios.entity.SolicitudReprogramacion;
import com.polygraph.erp.modules.solicitudes.service.ReprogramacionService;
import com.polygraph.erp.shared.enums.ImputableA;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Reprogramar la fecha de compromiso de una solicitud. Solo el gestor del cliente o el administrador. */
@RestController
@RequestMapping("/api/v1/solicitudes")
@RequiredArgsConstructor
public class ReprogramacionController {

    private final ReprogramacionService servicio;

    public record ReprogramarRequest(@NotNull(message = "Indica la nueva fecha de compromiso.") LocalDate fechaNueva,
                                     @NotBlank(message = "Cuéntanos el motivo de la reprogramación.") @Size(max = 500, message = "El motivo no puede superar 500 caracteres.") String motivo,
                                     @NotNull(message = "Indica de quién es la causa.") ImputableA responsable) {}

    public record ReprogramacionResponse(Long id, Integer idServicio, LocalDate fechaAnterior, LocalDate fechaNueva, String motivo, ImputableA responsable,
                                         String usuario, LocalDateTime fecha) {}

    @PostMapping("/{idServicio}/reprogramar")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<ReprogramacionResponse> reprogramar(@PathVariable Integer idServicio, @Valid @RequestBody ReprogramarRequest body, Authentication auth) {
        SolicitudReprogramacion r = servicio.reprogramar(idServicio, body.fechaNueva(), body.motivo(), body.responsable(), auth.getName());
        return ResponseEntity.ok(new ReprogramacionResponse(r.getId(), idServicio, r.getFechaAnterior(), r.getFechaNueva(), r.getMotivo(), r.getResponsable(),
                r.getUsuario(), r.getFecha()));
    }
}
