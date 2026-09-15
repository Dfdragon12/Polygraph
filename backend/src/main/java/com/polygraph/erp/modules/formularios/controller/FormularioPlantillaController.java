package com.polygraph.erp.modules.formularios.controller;

import com.polygraph.erp.modules.formularios.dto.AnalisisPlantillaWordResponse;
import com.polygraph.erp.modules.formularios.dto.ArchivoDescarga;
import com.polygraph.erp.modules.formularios.dto.CampoArchivadoResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.dto.ImpactoPlantillaResponse;
import com.polygraph.erp.modules.formularios.dto.PlantillaConfigResponse;
import com.polygraph.erp.modules.formularios.dto.PlantillaVersionResponse;
import com.polygraph.erp.modules.formularios.dto.SeccionPlantillaResponse;
import com.polygraph.erp.modules.formularios.service.FormularioPlantillaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** admin/catálogo/plantilla/:idTipoProgreso — estructura del documento (secciones, columnas,
 *  filas sugeridas) de CUALQUIER subproceso, no solo Antecedentes. */
@RestController
@RequestMapping("/api/v1/catalogo/formularios/{idTipoProgreso}")
@RequiredArgsConstructor
public class FormularioPlantillaController {

    private final FormularioPlantillaService service;

    /** También la usa el formulario de quien lo diligencia (GESTOR/ANALISTA_INTERNO/POLIGRAFISTA/
     *  VISITADOR) para saber qué secciones mostrar. */
    @GetMapping("/estructura")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR')")
    public ResponseEntity<List<SeccionPlantillaResponse>> estructura(@PathVariable Integer idTipoProgreso) {
        return ResponseEntity.ok(service.listarEstructura(idTipoProgreso));
    }

    @PutMapping("/estructura")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<SeccionPlantillaResponse>> guardar(
            @PathVariable Integer idTipoProgreso, @Valid @RequestBody GuardarPlantillaRequest req) {
        return ResponseEntity.ok(service.guardar(idTipoProgreso, req));
    }

    /** Qué secciones/columnas con respuestas ya guardadas se archivarían si se confirma este
     *  guardado — para mostrar una alerta ANTES de guardar de verdad. */
    @PostMapping("/verificar-cambios")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<ImpactoPlantillaResponse> verificarCambios(
            @PathVariable Integer idTipoProgreso, @Valid @RequestBody GuardarPlantillaRequest req) {
        return ResponseEntity.ok(service.verificarImpacto(idTipoProgreso, req));
    }

    /** Secciones/columnas archivadas de este subproceso — para el panel "Campos antiguos" del
     *  administrador. */
    @GetMapping("/archivados")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<CampoArchivadoResponse>> archivados(@PathVariable Integer idTipoProgreso) {
        return ResponseEntity.ok(service.listarArchivados(idTipoProgreso));
    }

    @PutMapping("/secciones/{idSeccion}/restaurar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> restaurarSeccion(
            @PathVariable Integer idTipoProgreso, @PathVariable Integer idSeccion) {
        service.restaurarSeccion(idTipoProgreso, idSeccion);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/columnas/{idColumna}/restaurar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> restaurarColumna(
            @PathVariable Integer idTipoProgreso, @PathVariable Integer idColumna) {
        service.restaurarColumna(idTipoProgreso, idColumna);
        return ResponseEntity.noContent().build();
    }

    /** Vista previa de cómo quedaría el documento — con datos de ejemplo, a partir de la
     *  estructura que se está editando (aunque todavía no se haya guardado). */
    @PostMapping("/vista-previa")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<byte[]> vistaPrevia(
            @PathVariable Integer idTipoProgreso, @Valid @RequestBody GuardarPlantillaRequest req) {
        byte[] documento = service.vistaPrevia(idTipoProgreso, req);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vista-previa.docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(documento);
    }

    /** Ruta del .docx con marcadores subido para este subproceso, si hay uno. */
    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH', 'GESTOR', 'ANALISTA_INTERNO', 'POLIGRAFISTA', 'VISITADOR', 'PROGRAMADOR')")
    public ResponseEntity<PlantillaConfigResponse> config(@PathVariable Integer idTipoProgreso) {
        return ResponseEntity.ok(service.obtenerConfig(idTipoProgreso));
    }

    @PostMapping(value = "/plantilla-word", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<PlantillaConfigResponse> subirPlantillaWord(
            @PathVariable Integer idTipoProgreso, @RequestParam MultipartFile archivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.subirPlantillaWord(idTipoProgreso, archivo, userDetails.getUsername()));
    }

    /** Todas las versiones del .docx subidas para este subproceso — la activa y las archivadas,
     *  con fecha de carga y quién la subió. */
    @GetMapping("/plantilla-word/historial")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<PlantillaVersionResponse>> historialPlantillaWord(@PathVariable Integer idTipoProgreso) {
        return ResponseEntity.ok(service.listarHistorialPlantillaWord(idTipoProgreso));
    }

    /** Descarga una versión ARCHIVADA (no la activa — para esa está GET .../plantilla-word). */
    @GetMapping("/plantilla-word/historial/{idHistorial}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Resource> descargarVersionHistorial(
            @PathVariable Integer idTipoProgreso, @PathVariable Long idHistorial) {
        ArchivoDescarga descarga = service.descargarVersionHistorial(idTipoProgreso, idHistorial);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(descarga.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .body(descarga.recurso());
    }

    /** Borra una versión ARCHIVADA del historial — el registro y el .docx en disco. No afecta la
     *  versión activa (para esa está DELETE .../plantilla-word). */
    @DeleteMapping("/plantilla-word/historial/{idHistorial}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> eliminarVersionHistorial(
            @PathVariable Integer idTipoProgreso, @PathVariable Long idHistorial) {
        service.eliminarVersionHistorial(idTipoProgreso, idHistorial);
        return ResponseEntity.noContent().build();
    }

    /** El .docx tal cual se subió, con su nombre original — para que el admin pueda recuperarlo
     *  si perdió su copia local, editarlo en Word y volver a subirlo. */
    @GetMapping("/plantilla-word")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Resource> descargarPlantillaWord(@PathVariable Integer idTipoProgreso) {
        ArchivoDescarga descarga = service.descargarPlantillaWord(idTipoProgreso);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(descarga.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .body(descarga.recurso());
    }

    @DeleteMapping("/plantilla-word")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<Void> quitarPlantillaWord(@PathVariable Integer idTipoProgreso) {
        service.quitarPlantillaWord(idTipoProgreso);
        return ResponseEntity.noContent().build();
    }

    /** Compara los marcadores del .docx subido contra las columnas/secciones definidas en el
     *  constructor: qué marcadores del documento no corresponden a ningún campo actual, y qué
     *  campos actuales no se están usando en ningún marcador del documento. */
    @PostMapping("/plantilla-word/analisis")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<AnalisisPlantillaWordResponse> analizarPlantillaWord(
            @PathVariable Integer idTipoProgreso, @Valid @RequestBody GuardarPlantillaRequest req) {
        return ResponseEntity.ok(service.analizarPlantillaWord(idTipoProgreso, req));
    }
}
