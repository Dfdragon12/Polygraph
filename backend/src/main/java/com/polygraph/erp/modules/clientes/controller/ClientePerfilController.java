package com.polygraph.erp.modules.clientes.controller;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.auth.service.AuthService;
import com.polygraph.erp.modules.usuarios.service.PerfilInternoService;
import com.polygraph.erp.modules.clientes.dto.ActualizarClientePerfilRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarUsuarioPropioRequest;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.dto.ClientePerfilResponse;
import com.polygraph.erp.modules.clientes.dto.CrearUsuarioPropioRequest;
import com.polygraph.erp.modules.clientes.service.ClienteAdminService;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/client/perfil")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_CLIENTE')")
public class ClientePerfilController {

    private final ClienteAdminService clienteAdminService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<ClientePerfilResponse> obtener(Authentication auth) {
        Usuario usuario = usuarioAutenticado(auth);
        return ResponseEntity.ok(conCuenta(clienteAdminService.obtenerPerfilPropio(idClienteDe(usuario)), usuario));
    }

    @PutMapping
    public ResponseEntity<ClientePerfilResponse> actualizar(
            @Valid @RequestBody ActualizarClientePerfilRequest request, Authentication auth) {
        Usuario usuario = usuarioAutenticado(auth);
        return ResponseEntity.ok(conCuenta(clienteAdminService.actualizarPerfilPropio(idClienteDe(usuario), request), usuario));
    }

    // Autoservicio: el ADMIN_CLIENTE crea sus propios usuarios ANALISTA_CLIENTE (tope de
    // ClienteAdminService.MAX_ANALISTAS_CLIENTE) — ver "Mi equipo" en el portal cliente.
    @GetMapping("/usuarios")
    public ResponseEntity<List<ClienteAdminResponse.UsuarioAsociado>> misUsuarios(Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.listarUsuariosPropios(resolverIdCliente(auth)));
    }

    @PostMapping("/usuarios")
    public ResponseEntity<ClienteAdminResponse.UsuarioAsociado> crearUsuario(
            @Valid @RequestBody CrearUsuarioPropioRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clienteAdminService.crearUsuarioPropio(resolverIdCliente(auth), request));
    }

    @PutMapping("/usuarios/{idUsuario}")
    public ResponseEntity<ClienteAdminResponse.UsuarioAsociado> actualizarUsuario(
            @PathVariable Long idUsuario,
            @Valid @RequestBody ActualizarUsuarioPropioRequest request, Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.actualizarUsuarioPropio(resolverIdCliente(auth), idUsuario, request));
    }

    @PatchMapping("/usuarios/{idUsuario}/activar")
    public ResponseEntity<ClienteAdminResponse.UsuarioAsociado> activarUsuario(
            @PathVariable Long idUsuario, Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.cambiarEstadoUsuarioPropio(resolverIdCliente(auth), idUsuario, true));
    }

    @PatchMapping("/usuarios/{idUsuario}/desactivar")
    public ResponseEntity<ClienteAdminResponse.UsuarioAsociado> desactivarUsuario(
            @PathVariable Long idUsuario, Authentication auth) {
        return ResponseEntity.ok(clienteAdminService.cambiarEstadoUsuarioPropio(resolverIdCliente(auth), idUsuario, false));
    }

    private Integer resolverIdCliente(Authentication auth) {
        return idClienteDe(usuarioAutenticado(auth));
    }

    private Usuario usuarioAutenticado(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private static Integer idClienteDe(Usuario usuario) {
        if (usuario.getIdCliente() == null) {
            throw new ApiException("Usuario no asociado a ningún cliente", HttpStatus.FORBIDDEN);
        }
        return usuario.getIdCliente();
    }

    /** Agrega a la información de la empresa los datos de la cuenta de quien consulta (desde cuándo
     *  es miembro, último acceso y el estado de su contraseña, con la misma regla del login). */
    private static ClientePerfilResponse conCuenta(ClientePerfilResponse perfil, Usuario usuario) {
        return perfil.conCuenta(usuario.getFechaCreacion(), usuario.getUltimoAcceso(), usuario.getFechaUltimoCambioPassword(),
                PerfilInternoService.diasParaCambiarPassword(usuario, java.time.LocalDate.now()),
                AuthService.DIAS_VIGENCIA_PASSWORD, Boolean.TRUE.equals(usuario.getRequiereCambioPassword()));
    }
}
