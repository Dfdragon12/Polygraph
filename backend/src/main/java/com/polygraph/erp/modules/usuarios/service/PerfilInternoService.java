package com.polygraph.erp.modules.usuarios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.service.AuthService;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.usuarios.dto.ActualizarPerfilInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.PerfilInternoResponse;
import com.polygraph.erp.modules.usuarios.dto.UsuarioInternoResponse;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** "Mi perfil" de los usuarios internos (analista, poligrafista, programador, visitador, gestor):
 *  cada uno ve y edita SU información, nunca la de otro — todo sale del usuario autenticado, ningún
 *  método recibe un id. Los datos viven en dos tablas (usuarios y empleados) y se mantienen iguales,
 *  como hace la edición del administrador. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfilInternoService {

    private final UsuarioRepository usuarioRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final CiudadRepository ciudadRepository;

    @Transactional(readOnly = true)
    public PerfilInternoResponse obtener(String email) {
        Usuario usuario = usuario(email);
        return respuesta(usuario, empleadoDe(usuario));
    }

    @Transactional
    public PerfilInternoResponse actualizar(String email, ActualizarPerfilInternoRequest req) {
        Usuario usuario = usuario(email);
        if (req.idCiudadResidencia() != null && !ciudadRepository.existsById(req.idCiudadResidencia())) {
            throw new ApiException("Ciudad no encontrada", HttpStatus.BAD_REQUEST);
        }
        String nombre = req.nombre().trim();
        String apellido = req.apellido() == null || req.apellido().isBlank() ? null : req.apellido().trim();
        String telefono = req.telefono() == null || req.telefono().isBlank() ? null : req.telefono().trim();

        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        UsuariosInternos empleado = empleadoDe(usuario);
        if (empleado != null) {
            empleado.setNombre(nombre);
            empleado.setApellido(apellido);
            empleado.setTelefono(telefono);
            empleado.setIdCiudadResidencia(req.idCiudadResidencia());
        }
        log.info("Perfil actualizado por su dueño: {}", usuario.getEmail());
        return respuesta(usuario, empleado);
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private UsuariosInternos empleadoDe(Usuario usuario) {
        return usuario.getIdEmpleado() == null ? null : empleadoRepository.findById(usuario.getIdEmpleado()).orElse(null);
    }

    private PerfilInternoResponse respuesta(Usuario usuario, UsuariosInternos empleado) {
        Integer idCiudad = empleado != null ? empleado.getIdCiudadResidencia() : null;
        String nombreCiudad = idCiudad == null ? null : ciudadRepository.findById(idCiudad).map(Ciudad::getNombreCiudad).orElse(null);
        return new PerfilInternoResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                usuario.getRol(),
                empleado != null ? empleado.getTelefono() : null,
                empleado != null ? empleado.getTipoDocumento() : null,
                empleado != null ? empleado.getDocumento() : null,
                idCiudad,
                nombreCiudad,
                empleado == null ? List.of() : empleado.getCiudadesAsignadas().stream()
                        .map(UsuarioInternoResponse.CiudadAsignadaResponse::from).toList(),
                empleado == null ? List.of() : empleado.getSubprocesosAsignados().stream()
                        .map(UsuarioInternoResponse.SubprocesoAsignadoResponse::from).toList(),
                empleado != null ? empleado.getFechaIngreso() : null,
                usuario.getUltimoAcceso(),
                usuario.getFechaCreacion(),
                usuario.getFechaUltimoCambioPassword(),
                diasParaCambiarPassword(usuario, LocalDate.now()),
                AuthService.DIAS_VIGENCIA_PASSWORD,
                Boolean.TRUE.equals(usuario.getRequiereCambioPassword()));
    }

    /** Misma regla del login: si el administrador la marcó para cambio, ya toca (0); si no, la
     *  contraseña dura DIAS_VIGENCIA_PASSWORD días desde el último cambio. Sin fecha registrada, el
     *  login nunca la vence, así que tampoco se muestra un plazo. */
    public static Integer diasParaCambiarPassword(Usuario usuario, LocalDate hoy) {
        if (Boolean.TRUE.equals(usuario.getRequiereCambioPassword())) return 0;
        LocalDateTime ultimoCambio = usuario.getFechaUltimoCambioPassword();
        if (ultimoCambio == null) return null;
        long transcurridos = ChronoUnit.DAYS.between(ultimoCambio.toLocalDate(), hoy);
        return (int) (AuthService.DIAS_VIGENCIA_PASSWORD - transcurridos);
    }
}
