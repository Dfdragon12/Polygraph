package com.polygraph.erp.modules.tienda.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService.Fila;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService.FilaCliente;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Lista de precios pospago (TP-11). Visibilidad:
 * <ul>
 *   <li>Cliente pospago: su lista (sin origen ni margen). Un cliente prepago recibe 403.</li>
 *   <li>Gestor: la lista solo para SUS clientes pospago.</li>
 *   <li>Administrador: la lista completa (origen, margen) y su exportación a Excel.</li>
 * </ul>
 * Los endpoints de la tienda prepago nunca devuelven el precio pospago.
 */
@RestController
@RequestMapping("/api/v1/catalogo/pospago")
@RequiredArgsConstructor
public class PospagoPreciosController {

    private final PrecioPospagoService service;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    @GetMapping("/precios")
    @PreAuthorize("hasAnyRole('ADMIN_CLIENTE', 'ANALISTA_CLIENTE')")
    public ResponseEntity<List<FilaCliente>> precios(Authentication auth) {
        Usuario u = usuario(auth);
        if (u.getIdCliente() == null) throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        Cliente c = clienteRepository.findById(u.getIdCliente()).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(service.listaParaCliente(c));
    }

    @GetMapping("/precios/cliente/{idCliente}")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN_POLYGRAPH')")
    public ResponseEntity<List<FilaCliente>> preciosDeUnCliente(@PathVariable Integer idCliente, Authentication auth) {
        Usuario u = usuario(auth);
        Cliente c = clienteRepository.findById(idCliente).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (u.getRol() == Rol.GESTOR && !Objects.equals(c.getIdGestor(), u.getIdUsuario())) {
            throw new ApiException("Este cliente no es de tu cartera.", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(service.listaParaCliente(c));
    }

    @GetMapping("/lista")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<List<Fila>> lista() {
        return ResponseEntity.ok(service.lista());
    }

    /** Margen en vivo para el formulario del servicio (no guarda nada). */
    @GetMapping("/margen")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<PrecioPospagoService.Margen> margen(@org.springframework.web.bind.annotation.RequestParam java.math.BigDecimal precio,
                                                              @org.springframework.web.bind.annotation.RequestParam(required = false) java.math.BigDecimal costo) {
        return ResponseEntity.ok(service.analizarMargen(precio, costo));
    }

    @GetMapping("/lista/exportar")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    public ResponseEntity<byte[]> exportar() throws IOException {
        List<Fila> filas = service.lista();
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Lista pospago");
            String[] titulos = {"Servicio", "Precio pospago", "Origen", "IVA", "Precio con IVA", "Ofrecido", "Margen %", "Observación"};
            Row cab = hoja.createRow(0);
            for (int i = 0; i < titulos.length; i++) cab.createCell(i).setCellValue(titulos[i]);
            int r = 1;
            for (Fila f : filas) {
                Row row = hoja.createRow(r++);
                row.createCell(0).setCellValue(f.servicio());
                numero(row.createCell(1), f.precio());
                row.createCell(2).setCellValue(f.origen());
                numero(row.createCell(3), f.iva());
                numero(row.createCell(4), f.precioConIva());
                row.createCell(5).setCellValue(f.ofrecido() ? "Sí" : "No");
                numero(row.createCell(6), f.margenPorcentaje());
                row.createCell(7).setCellValue(f.problema() != null ? f.problema() : (f.margenBajo() ? "Margen bajo el mínimo" : ""));
            }
            for (int i = 0; i < titulos.length; i++) hoja.autoSizeColumn(i);
            libro.write(salida);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lista-pospago.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(salida.toByteArray());
        }
    }

    private static void numero(Cell c, java.math.BigDecimal v) {
        if (v != null) c.setCellValue(v.doubleValue());
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
