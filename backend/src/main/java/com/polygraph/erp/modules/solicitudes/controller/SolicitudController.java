package com.polygraph.erp.modules.solicitudes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.solicitudes.dto.*;
import com.polygraph.erp.modules.solicitudes.service.SolicitudService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<SolicitudDetalleResponse> crear(
            @Valid @RequestBody SolicitudRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solicitudService.crearSolicitud(request, userDetails.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<Page<SolicitudResponse>> listar(
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @AuthenticationPrincipal UserDetails userDetails) {

        Integer clienteId = resolverIdCliente(idCliente, userDetails);
        Pageable pageable = PageRequest.of(pagina, tamano);
        return ResponseEntity.ok(solicitudService.listarSolicitudes(clienteId, estado, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<SolicitudDetalleResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(solicitudService.obtenerDetalle(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        solicitudService.cambiarEstado(id, request, userDetails);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<BulkUploadResponse> cargaMasiva(
            @RequestParam("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(solicitudService.procesarCargaMasiva(archivo, userDetails.getUsername()));
    }

    @GetMapping("/bulk/template")
    public ResponseEntity<byte[]> descargarPlantilla() throws IOException {
        byte[] plantilla = generarPlantillaExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=plantilla-carga-masiva.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(plantilla);
    }

    private Integer resolverIdCliente(Integer idClienteParam, UserDetails userDetails) {
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getRol() == Rol.ADMIN_CLIENTE || usuario.getRol() == Rol.ANALISTA_CLIENTE) {
            return usuario.getIdCliente();
        }
        return idClienteParam;
    }

    private byte[] generarPlantillaExcel() throws IOException {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var hoja = workbook.createSheet("Carga Masiva");
            var encabezado = hoja.createRow(0);
            String[] columnas = {"nit", "razon_social", "cedula", "nombre", "apellido",
                                  "telefono", "ciudad", "cargo", "tipo_servicio"};
            for (int i = 0; i < columnas.length; i++) {
                encabezado.createCell(i).setCellValue(columnas[i]);
            }
            // Fila de ejemplo
            var ejemplo = hoja.createRow(1);
            String[] valores = {"900123456", "Empresa S.A.S.", "1234567890", "Juan", "Pérez",
                                 "3001234567", "Bogotá", "Analista", "Estudio Básico"};
            for (int i = 0; i < valores.length; i++) {
                ejemplo.createCell(i).setCellValue(valores[i]);
            }
            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
