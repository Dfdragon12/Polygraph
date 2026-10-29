package com.polygraph.erp.modules.evaluados.controller;

import com.polygraph.erp.modules.evaluados.dto.CitaCandidatoResponse;
import com.polygraph.erp.modules.evaluados.dto.DocumentoEvaluadoResponse;
import com.polygraph.erp.modules.evaluados.dto.SugerenciaDisponibilidadRequest;
import com.polygraph.erp.modules.evaluados.service.PortalCandidatoService;
import com.polygraph.erp.modules.evaluados.dto.HojaVidaRequest;
import com.polygraph.erp.modules.evaluados.dto.LinkValidacionResponse;
import com.polygraph.erp.modules.evaluados.service.EvaluadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluees")
@RequiredArgsConstructor
public class EvaluadoController {

    private final EvaluadoService evaluadoService;

    private static String ipDe(jakarta.servlet.http.HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) return reenviada.split(",")[0].trim();
        return http.getRemoteAddr();
    }
    private final PortalCandidatoService portalCandidatoService;
    private final com.polygraph.erp.modules.tiemporeal.CanalCambios canalCambios;
    private final com.polygraph.erp.modules.catalogo.service.ItemHojaVidaService itemHojaVidaService;
    private final com.polygraph.erp.modules.evaluados.service.AccesoCandidatoService accesoCandidatoService;

    public record VerificarDocumentoRequest(@jakarta.validation.constraints.NotBlank(message = "Escribe tu número de documento.")
                                            @jakarta.validation.constraints.Size(max = 30) String documento) {}

    /** Antes de entrar al portal el candidato escribe su número de documento (AccesoCandidatoService):
     *  responde la llave que va en X-Acceso-Candidato. Es lo único del portal que no la exige. */
    @PostMapping("/link/{token}/verificar")
    public ResponseEntity<com.polygraph.erp.modules.evaluados.service.AccesoCandidatoService.Verificacion> verificarDocumento(
            @PathVariable String token, @Valid @RequestBody VerificarDocumentoRequest req) {
        return ResponseEntity.ok(accesoCandidatoService.verificar(token, req.documento()));
    }

    @PostMapping("/link/{token}/validate")
    public ResponseEntity<LinkValidacionResponse> validarLink(@PathVariable String token) {
        return ResponseEntity.ok(evaluadoService.validarLink(token));
    }

    @GetMapping("/link/{token}")
    public ResponseEntity<HojaVidaRequest> obtenerFormulario(@PathVariable String token) {
        return ResponseEntity.ok(evaluadoService.obtenerFormulario(token));
    }

    /** Listas (libreta militar, EPS, fondo de pensiones, tipo de contrato) y preguntas de
     *  declaración que ve el candidato — solo lo activo, en el orden que definió el admin. */
    @GetMapping("/link/{token}/catalogo")
    public ResponseEntity<java.util.Map<com.polygraph.erp.modules.catalogo.entity.ListaHojaVida,
            List<com.polygraph.erp.modules.catalogo.dto.ItemHojaVidaResponse>>> catalogo(@PathVariable String token) {
        evaluadoService.servicioConAcceso(token);
        return ResponseEntity.ok(itemHojaVidaService.activosPorLista());
    }

    /** Primera pantalla del portal: el candidato acepta el tratamiento de sus datos (Ley 1581). */
    @PostMapping("/link/{token}/autorizacion")
    public ResponseEntity<Void> autorizarTratamientoDatos(@PathVariable String token,
                                                           jakarta.servlet.http.HttpServletRequest http) {
        evaluadoService.autorizarTratamientoDatos(token, ipDe(http));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/link/{token}/progress")
    public ResponseEntity<Void> guardarProgreso(
            @PathVariable String token,
            @RequestBody HojaVidaRequest request) {
        evaluadoService.guardarProgreso(token, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/link/{token}/submit")
    public ResponseEntity<Void> enviarFormulario(
            @PathVariable String token,
            @Valid @RequestBody HojaVidaRequest request) {
        evaluadoService.enviarFormulario(token, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/link/{token}/documentos", consumes = "multipart/form-data")
    public ResponseEntity<DocumentoEvaluadoResponse> subirDocumento(
            @PathVariable String token,
            @RequestParam String tipoDocumento,
            @RequestParam MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evaluadoService.subirDocumento(token, tipoDocumento, archivo));
    }

    @GetMapping("/link/{token}/documentos")
    public ResponseEntity<List<DocumentoEvaluadoResponse>> listarDocumentos(@PathVariable String token) {
        return ResponseEntity.ok(evaluadoService.listarDocumentos(token));
    }

    @DeleteMapping("/link/{token}/documentos/{id}")
    public ResponseEntity<Void> eliminarDocumento(@PathVariable String token, @PathVariable Long id) {
        evaluadoService.eliminarDocumento(token, id);
        return ResponseEntity.noContent().build();
    }

    /** "Mis citas": poligrafías y visitas del servicio de este link. */
    /** Avisos en tiempo real del portal del candidato: cuando un interno agenda una cita, revisa
     *  un documento o cambia el servicio, el portal recarga solo (solo trae el idServicio). */
    @GetMapping(value = "/link/{token}/eventos", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter eventos(
            @PathVariable String token, jakarta.servlet.http.HttpServletResponse respuesta) {
        Integer idServicio = evaluadoService.servicioConAcceso(token).getIdServicio();
        com.polygraph.erp.modules.tiemporeal.TiempoRealController.sinBuffer(respuesta);
        return canalCambios.suscribirCandidato(idServicio);
    }

    @GetMapping("/link/{token}/citas")
    public ResponseEntity<List<CitaCandidatoResponse>> citas(@PathVariable String token) {
        return ResponseEntity.ok(portalCandidatoService.citas(token));
    }

    /** El candidato le propone al programador cuándo tiene tiempo para esa cita. */
    @PostMapping("/link/{token}/citas/{idServicioSubproceso}/sugerencias")
    public ResponseEntity<CitaCandidatoResponse.Sugerencia> sugerir(
            @PathVariable String token,
            @PathVariable Long idServicioSubproceso,
            @Valid @RequestBody SugerenciaDisponibilidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portalCandidatoService.sugerir(token, idServicioSubproceso, request));
    }
}
