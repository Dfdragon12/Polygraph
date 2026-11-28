package com.polygraph.erp.modules.trabajos.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.modules.trabajos.service.ProcesadoresTrabajos;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/** Seguimiento de un trabajo por quien lo pidió (cualquier rol autenticado; solo ve los suyos).
 *
 *  GET /{id} es el endpoint de polling: respuesta pequeña, sin caché, sin retener nada en el
 *  servidor entre consultas. GET /{id}/resultado entrega el archivo en streaming desde disco
 *  (Resource): el PDF nunca se carga entero en memoria para enviarlo, y el cliente puede
 *  reanudar la descarga (Range) si se le corta la red. */
@Slf4j
@RestController
@RequestMapping("/api/v1/trabajos")
@RequiredArgsConstructor
public class TrabajoController {

    private final ColaTrabajosService cola;
    private final ProcesadoresTrabajos procesadores;
    private final AlmacenTrabajos almacen;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/{id}")
    public ResponseEntity<TrabajoResponse> consultar(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(cola.consultar(id, usuario(auth)));
    }

    @GetMapping("/{id}/resultado")
    public ResponseEntity<Resource> resultado(@PathVariable UUID id, Authentication auth) {
        Usuario usuario = usuario(auth);
        TrabajoAsincrono trabajo = cola.obtenerPropio(id, usuario);
        if (trabajo.getEstado() != EstadoTrabajo.COMPLETADO || trabajo.getResultadoRuta() == null) {
            throw new ApiException("Este trabajo no tiene un archivo para descargar.", HttpStatus.CONFLICT);
        }
        procesadores.de(trabajo.getTipo()).validarDescarga(trabajo, cola.parametros(trabajo), usuario);
        if (!almacen.existe(trabajo.getResultadoRuta())) {
            throw new ApiException("El archivo ya no está disponible; vuelve a solicitarlo.", HttpStatus.GONE);
        }
        Path archivo = almacen.resolver(trabajo.getResultadoRuta());
        String nombre = trabajo.getResultadoNombre() != null ? trabajo.getResultadoNombre() : "resultado";
        MediaType tipo = trabajo.getResultadoTipoContenido() != null
                ? MediaType.parseMediaType(trabajo.getResultadoTipoContenido())
                : MediaType.APPLICATION_OCTET_STREAM;
        log.info("Descarga de resultado: trabajo {} {} por {}", trabajo.getTipo(), id, usuario.getEmail());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .contentType(tipo)
                .body(new FileSystemResource(archivo));
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }
}
