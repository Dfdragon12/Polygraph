package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.ActualizarClienteAdminRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarClientePerfilRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarPospagoRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarUsuarioClienteRequest;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.dto.ClientePerfilResponse;
import com.polygraph.erp.modules.clientes.dto.CrearUsuarioClienteRequest;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
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
public class ClienteAdminService {

    private static final Set<Rol> ROLES_CLIENTE = Set.of(Rol.ADMIN_CLIENTE, Rol.ANALISTA_CLIENTE);

    private final ClienteRepository clienteRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final CiudadRepository ciudadRepository;
    private final SoporteClienteService soporteClienteService;
    private final NotificacionService notificacionService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<ClienteAdminResponse> listar() {
        return clienteRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteAdminResponse obtener(Integer id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        return toResponse(cliente);
    }

    public ClienteAdminResponse actualizar(Integer id, ActualizarClienteAdminRequest req) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        if ("NATURAL".equals(cliente.getTipoPersona().name())) {
            if (req.nombre() != null)   cliente.setNombre(req.nombre());
            if (req.apellido() != null) cliente.setApellido(req.apellido());
        } else {
            if (req.razonSocial() != null)        cliente.setRazonSocial(req.razonSocial());
            if (req.nombreComercial() != null)    cliente.setNombreComercial(req.nombreComercial());
            if (req.representanteLegal() != null) cliente.setRepresentanteLegal(req.representanteLegal());
        }
        if (req.telefono()  != null) cliente.setTelefono(req.telefono());
        if (req.direccion() != null) cliente.setDireccion(req.direccion());
        if (req.idCiudad()  != null) {
            if (!ciudadRepository.existsById(req.idCiudad())) {
                throw new ApiException("Ciudad no encontrada", HttpStatus.BAD_REQUEST);
            }
            cliente.setIdCiudad(req.idCiudad());
        }
        if (req.idGestor()  != null) {
            Usuario gestor = usuarioRepository.findById(req.idGestor())
                    .orElseThrow(() -> new ApiException("Gestor no encontrado", HttpStatus.NOT_FOUND));
            if (gestor.getRol() != Rol.GESTOR) {
                throw new ApiException("El usuario seleccionado no tiene rol Gestor", HttpStatus.BAD_REQUEST);
            }
            cliente.setIdGestor(gestor.getIdUsuario());
        }

        String nombre = nombreCliente(cliente);
        log.info("Cliente {} actualizado", id);
        notificacionService.crearParaAdmins("CLIENTE", "Cliente actualizado",
                "Se modificó la información del cliente " + nombre
                + " (" + cliente.getTipoPersona().name() + " · " + cliente.getTipoCliente().name() + ")");
        return toResponse(cliente);
    }

    /** Asigna/ajusta el cupo de crédito de un cliente POSPAGO. Crea la fila en clientes_pospago si aún no existe. */
    public ClienteAdminResponse actualizarPospago(Integer id, ActualizarPospagoRequest req) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (cliente.getTipoCliente() != TipoCliente.POSPAGO) {
            throw new ApiException("Solo los clientes pospago tienen cupo de crédito", HttpStatus.BAD_REQUEST);
        }

        ClientePospago pospago = clientePospagoRepository.findByIdCliente(id)
                .orElseGet(() -> ClientePospago.builder().cliente(cliente).build());

        if (req.limiteCredito() != null) pospago.setLimiteCredito(req.limiteCredito());
        if (req.creditoDisponible() != null) pospago.setCreditoDisponible(req.creditoDisponible());
        if (req.estadoMora() != null) pospago.setEstadoMora(req.estadoMora());
        if (req.requiereAprobacion() != null) pospago.setRequiereAprobacion(req.requiereAprobacion());
        pospago.setUltimaRevisionCredito(LocalDate.now());
        clientePospagoRepository.save(pospago);

        String nombre = nombreCliente(cliente);
        log.info("Crédito pospago actualizado: cliente={}, limiteCredito={}, estadoMora={}",
                id, pospago.getLimiteCredito(), pospago.getEstadoMora());
        notificacionService.crearParaAdmins("CLIENTE", "Crédito pospago actualizado",
                "Se actualizó el cupo de crédito de " + nombre);
        return toResponse(cliente);
    }

    // ---------- Perfil propio (el cliente ve/edita su propia información — sin gestor ni notas internas) ----------

    @Transactional(readOnly = true)
    public ClientePerfilResponse obtenerPerfilPropio(Integer idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        return toPerfilResponse(cliente);
    }

    public ClientePerfilResponse actualizarPerfilPropio(Integer idCliente, ActualizarClientePerfilRequest req) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        if ("NATURAL".equals(cliente.getTipoPersona().name())) {
            if (req.nombre() != null)   cliente.setNombre(req.nombre());
            if (req.apellido() != null) cliente.setApellido(req.apellido());
        } else {
            if (req.razonSocial() != null)        cliente.setRazonSocial(req.razonSocial());
            if (req.nombreComercial() != null)    cliente.setNombreComercial(req.nombreComercial());
            if (req.representanteLegal() != null) cliente.setRepresentanteLegal(req.representanteLegal());
        }
        if (req.telefono()  != null) cliente.setTelefono(req.telefono());
        if (req.direccion() != null) cliente.setDireccion(req.direccion());
        if (req.idCiudad()  != null) {
            if (!ciudadRepository.existsById(req.idCiudad())) {
                throw new ApiException("Ciudad no encontrada", HttpStatus.BAD_REQUEST);
            }
            cliente.setIdCiudad(req.idCiudad());
        }

        log.info("Cliente {} actualizó su propia información", idCliente);
        notificacionService.crearParaAdmins("CLIENTE", "Cliente actualizó su información",
                nombreCliente(cliente) + " actualizó los datos de su empresa.");
        return toPerfilResponse(cliente);
    }

    private ClientePerfilResponse toPerfilResponse(Cliente c) {
        String nombreCiudad = c.getIdCiudad() != null
                ? ciudadRepository.findById(c.getIdCiudad()).map(Ciudad::getNombreCiudad).orElse(null)
                : null;
        return new ClientePerfilResponse(
                c.getIdCliente(), c.getTipoPersona().name(), c.getTipoCliente().name(),
                c.getNit(), c.getDv(), c.getRazonSocial(), c.getNombreComercial(), c.getRepresentanteLegal(),
                c.getNombre(), c.getApellido(), c.getEmailPrincipal(), c.getTelefono(), c.getDireccion(),
                c.getIdCiudad(), nombreCiudad);
    }

    public ClienteAdminResponse cambiarEstado(Integer id, boolean activar) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        String nuevoEstado = activar ? "ACTIVO" : "INACTIVO";
        cliente.setEstado(nuevoEstado);

        // Cambiar estado de todos los usuarios asociados
        List<Usuario> usuarios = usuarioRepository.findByIdCliente(id);
        usuarios.forEach(u -> u.setActivo(activar));

        String nombre = nombreCliente(cliente);
        log.info("Cliente {} -> estado {}", id, nuevoEstado);
        notificacionService.crearParaAdmins("CLIENTE",
                activar ? "Cliente activado" : "Cliente desactivado",
                "El cliente " + nombre + " fue " + (activar ? "activado" : "desactivado"));
        return toResponse(cliente);
    }

    public ClienteAdminResponse crearUsuario(Integer idCliente, CrearUsuarioClienteRequest req) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (!ROLES_CLIENTE.contains(req.rol())) {
            throw new ApiException("Rol inválido para usuario de cliente", HttpStatus.BAD_REQUEST);
        }
        if (usuarioRepository.existsByEmail(req.email())) {
            throw new ApiException("El email ya está registrado", HttpStatus.CONFLICT);
        }

        Usuario usuario = Usuario.builder()
                .nombre(req.nombre())
                .apellido(req.apellido())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .rol(req.rol())
                .activo(true)
                .requiere2fa(false)
                .emailVerificado(true)
                .idCliente(idCliente)
                .fechaCreacion(LocalDateTime.now())
                .requiereCambioPassword(true)
                .build();
        usuarioRepository.save(usuario);

        String nombre = nombreCliente(cliente);
        log.info("Usuario {} creado para cliente {}", req.email(), idCliente);
        notificacionService.crearParaAdmins("USUARIO", "Nuevo usuario de cliente creado",
                "Se creó el usuario " + req.nombre() + " (" + req.rol().name() + ") para " + nombre);
        return toResponse(cliente);
    }

    public ClienteAdminResponse actualizarUsuario(Integer idCliente, Long idUsuario, ActualizarUsuarioClienteRequest req) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (!idCliente.equals(usuario.getIdCliente())) {
            throw new ApiException("El usuario no pertenece a este cliente", HttpStatus.BAD_REQUEST);
        }
        if (!ROLES_CLIENTE.contains(req.rol())) {
            throw new ApiException("Rol inválido para usuario de cliente", HttpStatus.BAD_REQUEST);
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

        String nombre = nombreCliente(cliente);
        log.info("Usuario {} de cliente {} actualizado", usuario.getEmail(), idCliente);
        notificacionService.crearParaAdmins("USUARIO", "Usuario de cliente actualizado",
                "Se editó el usuario " + usuario.getNombre() + " (" + req.rol().name() + ") de " + nombre);
        return toResponse(cliente);
    }

    public ClienteAdminResponse cambiarEstadoUsuario(Integer idCliente, Long idUsuario, boolean activar) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (!idCliente.equals(usuario.getIdCliente())) {
            throw new ApiException("El usuario no pertenece a este cliente", HttpStatus.BAD_REQUEST);
        }
        usuario.setActivo(activar);

        String nombre = nombreCliente(cliente);
        log.info("Usuario {} de cliente {} -> activo={}", usuario.getEmail(), idCliente, activar);
        notificacionService.crearParaAdmins("USUARIO",
                activar ? "Usuario de cliente activado" : "Usuario de cliente desactivado",
                "El usuario " + usuario.getNombre() + " de " + nombre + " fue " + (activar ? "activado" : "desactivado"));
        return toResponse(cliente);
    }

    private String nombreCliente(Cliente c) {
        if ("NATURAL".equals(c.getTipoPersona().name())) {
            return (c.getNombre() != null ? c.getNombre() : "")
                    + (c.getApellido() != null ? " " + c.getApellido() : "");
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }

    private ClienteAdminResponse toResponse(Cliente c) {
        List<Usuario> usuariosCliente = usuarioRepository.findByIdCliente(c.getIdCliente());
        Usuario usuarioPrincipal = usuariosCliente.stream()
                .filter(u -> u.getRol() == Rol.ADMIN_CLIENTE)
                .findFirst()
                .orElse(null);
        ClientePospago pospago = "POSPAGO".equals(c.getTipoCliente().name())
                ? clientePospagoRepository.findByIdCliente(c.getIdCliente()).orElse(null)
                : null;
        String nombreCiudad = c.getIdCiudad() != null
                ? ciudadRepository.findById(c.getIdCiudad()).map(Ciudad::getNombreCiudad).orElse(null)
                : null;
        return ClienteAdminResponse.from(c, usuarioPrincipal, usuariosCliente, pospago, resolverGestor(c), nombreCiudad,
                resumenDocumentos(c.getIdCliente()));
    }

    /** Reusa el mismo cálculo de "qué documentos aplican" que ve el propio cliente, incluyendo los que ni se han cargado. */
    private ClienteAdminResponse.DocumentosResumen resumenDocumentos(Integer idCliente) {
        long sinCargar = 0, pendientes = 0, validados = 0, rechazados = 0, vencidos = 0;
        for (var soporte : soporteClienteService.listarParaCliente(idCliente)) {
            switch (soporte.estado() != null ? soporte.estado() : "SIN_CARGAR") {
                case "PENDIENTE" -> pendientes++;
                case "VALIDADO"  -> validados++;
                case "RECHAZADO" -> rechazados++;
                case "VENCIDO"   -> vencidos++;
                default          -> sinCargar++;
            }
        }
        return new ClienteAdminResponse.DocumentosResumen(sinCargar, pendientes, validados, rechazados, vencidos);
    }

    private ClienteAdminResponse.GestorInfo resolverGestor(Cliente c) {
        if (c.getIdGestor() == null) return null;
        return usuarioRepository.findById(c.getIdGestor())
                .map(gestor -> {
                    String telefono = gestor.getIdEmpleado() != null
                            ? empleadoRepository.findById(gestor.getIdEmpleado())
                                    .map(UsuariosInternos::getTelefono)
                                    .orElse(null)
                            : null;
                    String nombreCompleto = (gestor.getNombre() != null ? gestor.getNombre() : "")
                            + (gestor.getApellido() != null ? " " + gestor.getApellido() : "");
                    return new ClienteAdminResponse.GestorInfo(gestor.getIdUsuario(), nombreCompleto.trim(), telefono);
                })
                .orElse(null);
    }
}
