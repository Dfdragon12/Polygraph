package com.polygraph.erp.modules.usuarios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.usuarios.dto.ActualizarUsuarioInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.CrearUsuarioInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.UsuarioClienteResponse;
import com.polygraph.erp.modules.usuarios.dto.UsuarioInternoResponse;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UsuariosInternosService {

    private static final Set<Rol> ROLES_INTERNOS = Set.of(
            Rol.ADMIN_POLYGRAPH, Rol.GESTOR, Rol.ANALISTA_INTERNO,
            Rol.PROGRAMADOR, Rol.POLIGRAFISTA, Rol.VISITADOR
    );

    private static final Set<Rol> ROLES_CLIENTES = Set.of(
            Rol.ADMIN_CLIENTE, Rol.ANALISTA_CLIENTE
    );

    private final UsuarioRepository usuarioRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<UsuarioInternoResponse> listarTodos() {
        return usuarioRepository.findByRolIn(ROLES_INTERNOS).stream()
                .map(u -> {
                    UsuariosInternos emp = u.getIdEmpleado() != null
                            ? empleadoRepository.findById(u.getIdEmpleado()).orElse(null)
                            : null;
                    return UsuarioInternoResponse.from(u, emp);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioClienteResponse> listarClientes() {
        return usuarioRepository.findByRolIn(ROLES_CLIENTES).stream()
                .map(UsuarioClienteResponse::from)
                .toList();
    }

    public UsuarioInternoResponse crear(CrearUsuarioInternoRequest req) {
        if (!ROLES_INTERNOS.contains(req.rol())) {
            throw new ApiException("Rol inválido para usuario interno", HttpStatus.BAD_REQUEST);
        }
        if (usuarioRepository.existsByEmail(req.email())) {
            throw new ApiException("El email ya está registrado", HttpStatus.CONFLICT);
        }

        UsuariosInternos empleado = UsuariosInternos.builder()
                .tipoEmpleado(req.rol().name())
                .nombre(req.nombre())
                .apellido(req.apellido())
                .email(req.email())
                .telefono(req.telefono())
                .tipoDocumento(req.tipoDocumento())
                .documento(req.documento())
                .ciudadResidencia(req.ciudadResidencia())
                .salaEncargada(req.salaEncargada())
                .novedadesSala(req.novedadesSala())
                .zonasVisita(req.zonasVisita())
                .activo(true)
                .fechaIngreso(LocalDate.now())
                .build();
        empleadoRepository.save(empleado);

        // Si no se envía contraseña, se usa el número de documento como contraseña inicial
        String pwd = (req.password() != null && !req.password().isBlank())
                ? req.password()
                : req.documento();
        if (pwd == null || pwd.isBlank()) {
            throw new ApiException("Debe indicar un número de documento o una contraseña inicial", HttpStatus.BAD_REQUEST);
        }

        Usuario usuario = Usuario.builder()
                .nombre(req.nombre())
                .apellido(req.apellido())
                .email(req.email())
                .password(passwordEncoder.encode(pwd))
                .rol(req.rol())
                .activo(true)
                .requiere2fa(false)
                .emailVerificado(true)
                .idEmpleado(empleado.getIdEmpleado())
                .fechaCreacion(LocalDateTime.now())
                .requiereCambioPassword(true)
                .fechaUltimoCambioPassword(null)
                .build();
        usuarioRepository.save(usuario);

        log.info("Usuario interno creado: {} - {}", req.email(), req.rol());
        notificacionService.crearParaAdmins("USUARIO", "Nuevo usuario interno creado",
                "Se creó el usuario " + req.nombre() + " " + (req.apellido() != null ? req.apellido() : "")
                + " — Rol: " + req.rol().name());
        return UsuarioInternoResponse.from(usuario, empleado);
    }

    public UsuarioInternoResponse actualizar(Long idUsuario, ActualizarUsuarioInternoRequest req) {
        if (!ROLES_INTERNOS.contains(req.rol())) {
            throw new ApiException("Rol inválido para usuario interno", HttpStatus.BAD_REQUEST);
        }

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (!ROLES_INTERNOS.contains(usuario.getRol())) {
            throw new ApiException("No es un usuario interno", HttpStatus.BAD_REQUEST);
        }

        if (!usuario.getEmail().equals(req.email()) && usuarioRepository.existsByEmail(req.email())) {
            throw new ApiException("El email ya está en uso por otro usuario", HttpStatus.CONFLICT);
        }

        usuario.setNombre(req.nombre());
        usuario.setApellido(req.apellido());
        usuario.setEmail(req.email());
        usuario.setRol(req.rol());
        if (req.password() != null && !req.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(req.password()));
        }

        UsuariosInternos emp = null;
        if (usuario.getIdEmpleado() != null) {
            emp = empleadoRepository.findById(usuario.getIdEmpleado()).orElse(null);
            if (emp != null) {
                emp.setNombre(req.nombre());
                emp.setApellido(req.apellido());
                emp.setEmail(req.email());
                emp.setTelefono(req.telefono());
                emp.setTipoDocumento(req.tipoDocumento());
                emp.setDocumento(req.documento());
                emp.setCiudadResidencia(req.ciudadResidencia());
                emp.setSalaEncargada(req.salaEncargada());
                emp.setNovedadesSala(req.novedadesSala());
                emp.setZonasVisita(req.zonasVisita());
                emp.setTipoEmpleado(req.rol().name());
            }
        }

        log.info("Usuario interno actualizado: {}", usuario.getEmail());
        notificacionService.crearParaAdmins("USUARIO", "Usuario interno actualizado",
                "Se editó el usuario " + req.nombre() + " " + (req.apellido() != null ? req.apellido() : "")
                + " — Rol: " + req.rol().name());
        return UsuarioInternoResponse.from(usuario, emp);
    }

    public UsuarioInternoResponse cambiarEstado(Long idUsuario, boolean activo) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (!ROLES_INTERNOS.contains(usuario.getRol())) {
            throw new ApiException("No es un usuario interno", HttpStatus.BAD_REQUEST);
        }

        usuario.setActivo(activo);

        if (usuario.getIdEmpleado() != null) {
            empleadoRepository.findById(usuario.getIdEmpleado())
                    .ifPresent(emp -> emp.setActivo(activo));
        }

        log.info("Estado de usuario {} cambiado a {}", usuario.getEmail(), activo);
        notificacionService.crearParaAdmins("USUARIO",
                activo ? "Usuario interno activado" : "Usuario interno desactivado",
                "El usuario " + usuario.getNombre() + " (" + usuario.getRol().name() + ") fue "
                + (activo ? "activado" : "desactivado"));
        UsuariosInternos emp = usuario.getIdEmpleado() != null
                ? empleadoRepository.findById(usuario.getIdEmpleado()).orElse(null)
                : null;
        return UsuarioInternoResponse.from(usuario, emp);
    }
}
