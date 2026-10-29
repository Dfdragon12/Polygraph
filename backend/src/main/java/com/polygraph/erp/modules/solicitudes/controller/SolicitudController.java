package com.polygraph.erp.modules.solicitudes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.solicitudes.dto.*;
import com.polygraph.erp.modules.solicitudes.service.CargaMasivaProcesador;
import com.polygraph.erp.modules.solicitudes.service.InformePdfProcesador;
import com.polygraph.erp.modules.solicitudes.service.SolicitudService;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;
    private final com.polygraph.erp.modules.solicitudes.service.CancelacionSolicitudService cancelacionSolicitudService;
    private final UsuarioRepository usuarioRepository;
    private final InformePdfProcesador informePdfProcesador;
    private final CargaMasivaProcesador cargaMasivaProcesador;
    private final com.polygraph.erp.modules.solicitudes.service.EntregaEstimadaService entregaEstimadaService;
    private final com.polygraph.erp.modules.solicitudes.service.ResumenCobroService resumenCobroService;

    /** NS-06: fecha estimada de entrega con el mismo cálculo con el que se crea la solicitud. */
    @GetMapping("/entrega-estimada")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    public ResponseEntity<com.polygraph.erp.modules.solicitudes.dto.EntregaEstimadaResponse> entregaEstimada(@RequestParam java.util.List<Integer> procesos) {
        return ResponseEntity.ok(entregaEstimadaService.estimar(procesos));
    }

    /** NS-11: qué se usa y qué queda, para el resumen del paso 3 (calculado en el servidor). */
    @GetMapping("/resumen-cobro")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    public ResponseEntity<com.polygraph.erp.modules.solicitudes.dto.ResumenCobroResponse> resumenCobro(
            @RequestParam Integer idProceso, @RequestParam(required = false) Integer idCiudad, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(resumenCobroService.resumir(userDetails.getUsername(), idProceso, idCiudad));
    }

    /** NS-05: ¿está en mora? Mismo texto con el que el servidor rechaza el envío. */
    @GetMapping("/mora")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    public ResponseEntity<com.polygraph.erp.modules.pagos.mora.MoraCliente.Resultado> mora(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(solicitudService.moraDelUsuario(userDetails.getUsername()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<List<SolicitudDetalleResponse>> crear(
            @Valid @RequestBody SolicitudRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solicitudService.crearSolicitud(request, userDetails.getUsername(), claveIdempotencia));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<Page<SolicitudResponse>> listar(
            @RequestParam(required = false) Integer idCliente,
            @RequestParam(required = false) String estado,
            // uno o varios, separados por coma (ej. ENTREGADA_A_TIEMPO,ENTREGADA_TARDE)
            @RequestParam(required = false) java.util.List<com.polygraph.erp.shared.enums.EstadoTiempo> estadoTiempo,
            @RequestParam(required = false) String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @AuthenticationPrincipal UserDetails userDetails) {

        Integer clienteId = resolverIdCliente(idCliente, userDetails);
        Pageable pageable = PageRequest.of(pagina, tamano);
        return ResponseEntity.ok(solicitudService.listarSolicitudes(clienteId, estado, estadoTiempo, orden, pageable));
    }

    @GetMapping("/conteo")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<ConteoEstadosResponse> conteo(
            @RequestParam(required = false) Integer idCliente,
            @AuthenticationPrincipal UserDetails userDetails) {
        Integer clienteId = resolverIdCliente(idCliente, userDetails);
        return ResponseEntity.ok(solicitudService.conteoPorEstado(clienteId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<SolicitudDetalleResponse> detalle(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(solicitudService.obtenerDetalle(id, userDetails));
    }

    /** PDF del informe de un subproceso aprobado — solo con la solicitud PUBLICADA. Responde
     *  202 con el id del trabajo: el frontend consulta GET /api/v1/trabajos/{id} y, cuando está
     *  COMPLETADO, descarga GET /api/v1/trabajos/{id}/resultado. Si el PDF ya estaba generado, el
     *  trabajo llega COMPLETADO en esta misma respuesta. Los errores de acceso (403/404/409) se
     *  responden aquí mismo, no en el trabajo. */
    @PostMapping("/{id}/informes/{idServicioSubproceso}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<TrabajoResponse> informePdf(
            @PathVariable Integer id,
            @PathVariable Long idServicioSubproceso,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.accepted().body(informePdfProcesador.solicitar(id, idServicioSubproceso, userDetails));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH', 'ANALISTA_INTERNO')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Integer id,
            @Valid @RequestBody CambioEstadoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        cancelacionSolicitudService.cambiarEstado(id, request, userDetails);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<TrabajoResponse> cargaMasiva(
            @RequestParam("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        // 202: el Excel queda en la cola (CARGA_MASIVA); el resumen (BulkUploadResponse) llega
        // en el campo "resultado" del trabajo cuando termina.
        return ResponseEntity.accepted().body(cargaMasivaProcesador.solicitar(archivo, userDetails));
    }

    @GetMapping("/bulk/template")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'GESTOR', 'ADMIN_POLYGRAPH')")
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
            // NS-12: mismas columnas y reglas que Nueva solicitud. NIT y razón social ya no van: el cliente se conoce por la sesión.
            String[] columnas = {"cedula", "nombres", "apellidos", "cargo", "criticidad_cargo",
                                  "celular", "email", "ciudad", "direccion", "servicio"};
            for (int i = 0; i < columnas.length; i++) {
                encabezado.createCell(i).setCellValue(columnas[i]);
            }
            // Fila de ejemplo
            var ejemplo = hoja.createRow(1);
            String[] valores = {"1234567890", "Juan", "Pérez", "Analista", "ESTANDAR",
                                 "3001234567", "juan.perez@correo.com", "Bogotá", "", "Antecedentes"};
            for (int i = 0; i < valores.length; i++) {
                ejemplo.createCell(i).setCellValue(valores[i]);
            }
            var ayuda = workbook.createSheet("Instrucciones");
            String[] reglas = {
                "Todo el archivo se revisa antes de crear nada: si una fila tiene un error, no se crea ninguna solicitud y se te dice qué corregir en cada fila.",
                "cedula: solo números. celular: 10 dígitos y empieza por 3. email: con formato válido.",
                "criticidad_cargo: ESTANDAR, SENSIBLE o CRITICO.",
                "ciudad: una ciudad confirmada de la plataforma (puedes escribir «Ciudad, Departamento» si el nombre se repite).",
                "direccion: obligatoria solo cuando el servicio incluye una visita domiciliaria.",
                "servicio: el nombre del servicio tal como aparece en Nueva solicitud. Los servicios de poligrafía con formato adicional se piden desde Nueva solicitud.",
                "Se descuenta 1 unidad (prepago) o el valor del servicio del cupo (pospago) por cada fila; si el archivo pide más de lo que tienes, no se crea nada."
            };
            for (int i = 0; i < reglas.length; i++) ayuda.createRow(i).createCell(0).setCellValue(reglas[i]);
            ayuda.setColumnWidth(0, 120 * 256);
            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
