package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.auth.service.AuthService;
import com.polygraph.erp.modules.clientes.dto.ActualizarMiPerfilAnalistaClienteRequest;
import com.polygraph.erp.modules.clientes.dto.MiPerfilAnalistaClienteResponse;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.usuarios.service.PerfilInternoService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** "Mi perfil" del analista de un cliente: todo sale del usuario autenticado (ningún método recibe
 *  un id), así nadie ve ni edita el perfil de otro. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfilAnalistaClienteService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public MiPerfilAnalistaClienteResponse obtener(String email) {
        return respuesta(analista(email));
    }

    @Transactional
    public MiPerfilAnalistaClienteResponse actualizar(String email, ActualizarMiPerfilAnalistaClienteRequest req) {
        Usuario usuario = analista(email);
        usuario.setNombre(req.nombre().trim());
        usuario.setApellido(req.apellido() == null || req.apellido().isBlank() ? null : req.apellido().trim());
        log.info("Analista de cliente actualizó su perfil: {}", usuario.getEmail());
        return respuesta(usuario);
    }

    private Usuario analista(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (usuario.getRol() != Rol.ANALISTA_CLIENTE) {
            throw new ApiException("Este perfil es solo para analistas de un cliente.", HttpStatus.FORBIDDEN);
        }
        return usuario;
    }

    private MiPerfilAnalistaClienteResponse respuesta(Usuario usuario) {
        Cliente cliente = usuario.getIdCliente() == null ? null : clienteRepository.findById(usuario.getIdCliente()).orElse(null);
        Usuario gestor = cliente == null || cliente.getIdGestor() == null ? null
                : usuarioRepository.findById(cliente.getIdGestor()).orElse(null);
        return new MiPerfilAnalistaClienteResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                cliente == null ? null : nombreEmpresa(cliente),
                gestor == null ? null : (gestor.getNombre() + (gestor.getApellido() != null ? " " + gestor.getApellido() : "")).trim(),
                gestor == null ? null : gestor.getEmail(),
                usuario.getFechaCreacion(),
                usuario.getUltimoAcceso(),
                usuario.getFechaUltimoCambioPassword(),
                PerfilInternoService.diasParaCambiarPassword(usuario, LocalDate.now()),
                AuthService.DIAS_VIGENCIA_PASSWORD,
                Boolean.TRUE.equals(usuario.getRequiereCambioPassword()));
    }

    private static String nombreEmpresa(Cliente c) {
        if ("NATURAL".equals(c.getTipoPersona().name())) {
            return ((c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "")).trim();
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }
}
