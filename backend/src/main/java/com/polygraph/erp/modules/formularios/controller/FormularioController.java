package com.polygraph.erp.modules.formularios.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.formularios.dto.ArchivoDescarga;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest;
import com.polygraph.erp.modules.formularios.dto.ProgresoFormularioResponse;
import com.polygraph.erp.modules.formularios.service.FormularioDocumentoService;
import com.polygraph.erp.modules.formularios.service.FormularioService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Llenado del formulario de un subproceso — el dueño del subproceso (ANALISTA_INTERNO,
 *  POLIGRAFISTA o VISITADOR, según a quién le corresponda) lo diligencia; GESTOR/PROGRAMADOR/
 *  ADMIN_POLYGRAPH entran en modo supervisión (PROGRAMADOR coordina la agenda de poligrafista/
 *  visitador, así que también necesita ver cómo va el llenado). Sirve para cualquier tipo de
 *  subproceso. */
@RestController
@RequestMapping("/api/v1/gestor/formularios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR', 'GESTOR', 'PROGRAMADOR', 'ADMIN_POLYGRAPH')")
public class FormularioController {

    private final FormularioService service;
    private final FormularioDocumentoService documentoService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/subproceso/{idServicioSubproceso}")
    public ResponseEntity<FormularioResponse> obtener(
            @PathVariable Long idServicioSubproceso, Authentication auth) {
        return ResponseEntity.ok(service.obtener(idServicioSubproceso, resolverUsuario(auth)));
    }

    @PutMapping("/subproceso/{idServicioSubproceso}")
    public ResponseEntity<FormularioResponse> guardar(
            @PathVariable Long idServicioSubproceso, @Valid @RequestBody GuardarFormularioRequest req, Authentication auth) {
        return ResponseEntity.ok(service.guardar(idServicioSubproceso, req, resolverUsuario(auth)));
    }

    /** Qué tan lleno está el formulario — para que GESTOR vea de un vistazo cómo va el llenado
     *  sin tener que abrirlo completo. */
    @GetMapping("/subproceso/{idServicioSubproceso}/progreso")
    public ResponseEntity<ProgresoFormularioResponse> progreso(
            @PathVariable Long idServicioSubproceso, Authentication auth) {
        return ResponseEntity.ok(service.progreso(idServicioSubproceso, resolverUsuario(auth)));
    }

    // Solo gestor/admin — es el archivo "de trabajo" para editar fuera de la plataforma; el resto
    // de roles se queda con el PDF (mismo contenido, ver /documento-pdf).
    @GetMapping("/subproceso/{idServicioSubproceso}/documento")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<byte[]> descargarDocumento(@PathVariable Long idServicioSubproceso, Authentication auth) {
        byte[] documento = documentoService.generar(idServicioSubproceso, resolverUsuario(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=formulario-" + idServicioSubproceso + ".docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(documento);
    }

    /** Mismo documento que /documento (mismo .docx con los marcadores ya resueltos), convertido a
     *  PDF — mismo diseño que armó el administrador en Word, ver DocumentoPdfConverter. */
    @GetMapping("/subproceso/{idServicioSubproceso}/documento-pdf")
    public ResponseEntity<byte[]> descargarDocumentoPdf(@PathVariable Long idServicioSubproceso, Authentication auth) {
        byte[] documento = documentoService.generarPdf(idServicioSubproceso, resolverUsuario(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=formulario-" + idServicioSubproceso + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(documento);
    }

    /** El frontend sube el archivo apenas se selecciona y guarda la "ruta" devuelta como el
     *  valor de la columna tipo ARCHIVO — se persiste junto con el resto del formulario al
     *  guardar, igual que cualquier otro dato. */
    @PostMapping(value = "/subproceso/{idServicioSubproceso}/archivo", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> subirArchivo(
            @PathVariable Long idServicioSubproceso, @RequestParam MultipartFile archivo, Authentication auth) {
        String ruta = service.subirArchivo(idServicioSubproceso, archivo, resolverUsuario(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ruta", ruta));
    }

    @GetMapping("/subproceso/{idServicioSubproceso}/archivo")
    public ResponseEntity<Resource> descargarArchivo(
            @PathVariable Long idServicioSubproceso, @RequestParam String ruta, Authentication auth) {
        ArchivoDescarga descarga = service.descargarArchivo(idServicioSubproceso, ruta, resolverUsuario(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(descarga.recurso());
    }

    private Usuario resolverUsuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
