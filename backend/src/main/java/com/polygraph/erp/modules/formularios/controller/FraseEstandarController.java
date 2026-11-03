package com.polygraph.erp.modules.formularios.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Frases estándar de un formato (PF-03): lo que se escribe solo en una pregunta cuando se responde «No». Las ven los roles internos
 *  al diligenciar; las cambia el administrador y cada cambio sube la versión. La pantalla COPIA el texto al campo, así que cambiar
 *  una frase no altera lo ya diligenciado ni los informes enviados. */
@Slf4j
@RestController
@RequestMapping("/api/v1/catalogo/frases-estandar")
@RequiredArgsConstructor
public class FraseEstandarController {

    public record FraseResponse(String clave, String descripcion, String texto, int version) {}

    public record FraseRequest(String texto) {}

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_POLYGRAPH','GESTOR','POLIGRAFISTA','PROGRAMADOR')")
    public ResponseEntity<List<FraseResponse>> listar(@RequestParam(defaultValue = "POLIGRAFIA_PRE_EMPLEO") String formato) {
        return ResponseEntity.ok(jdbc.query(
                "SELECT clave, descripcion, texto, version FROM frases_estandar_formato WHERE formato = ? AND activo ORDER BY clave",
                (rs, n) -> new FraseResponse(rs.getString("clave"), rs.getString("descripcion"), rs.getString("texto"), rs.getInt("version")),
                formato));
    }

    @PutMapping("/{clave}")
    @PreAuthorize("hasRole('ADMIN_POLYGRAPH')")
    @Transactional
    public ResponseEntity<FraseResponse> cambiar(@PathVariable String clave, @RequestBody FraseRequest req, Authentication auth) {
        String texto = req.texto() == null ? "" : req.texto().trim();
        if (texto.isEmpty() || texto.length() > 500) {
            throw new ApiException("La frase debe tener entre 1 y 500 caracteres.", HttpStatus.BAD_REQUEST);
        }
        Usuario actor = usuarioRepository.findByEmail(auth.getName()).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        int filas = jdbc.update("UPDATE frases_estandar_formato SET texto = ?, version = version + 1, actualizado_en = NOW(), actualizado_por = ? WHERE clave = ?",
                texto, actor.getIdUsuario(), clave);
        if (filas == 0) throw new ApiException("Frase no encontrada.", HttpStatus.NOT_FOUND);
        log.info("Frase estándar {} cambiada por {}", clave, actor.getEmail());
        return ResponseEntity.ok(jdbc.queryForObject("SELECT clave, descripcion, texto, version FROM frases_estandar_formato WHERE clave = ?",
                (rs, n) -> new FraseResponse(rs.getString("clave"), rs.getString("descripcion"), rs.getString("texto"), rs.getInt("version")), clave));
    }
}
