package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.ActualizarClienteAdminRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarClientePerfilRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarUsuarioPropioRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarPospagoRequest;
import com.polygraph.erp.modules.clientes.dto.ActualizarUsuarioClienteRequest;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.dto.ClientePerfilResponse;
import com.polygraph.erp.modules.clientes.dto.CrearUsuarioClienteRequest;
import com.polygraph.erp.modules.clientes.dto.CrearUsuarioPropioRequest;
import com.polygraph.erp.modules.clientes.dto.EstadoCuentaClienteResponse;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.entity.Pais;
import com.polygraph.erp.modules.catalogo.entity.Barrio;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.repository.PaisRepository;
import com.polygraph.erp.modules.catalogo.repository.BarrioRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    /** Cuántos usuarios ANALISTA_CLIENTE puede crear un ADMIN_CLIENTE por su cuenta, sin pasar
     *  por un admin interno — ver crearUsuarioPropio(). Si necesita más, debe pedírselo a su
     *  gestor (no hay tope para lo que crea un admin interno vía crearUsuario()). */
    public static final int MAX_ANALISTAS_CLIENTE = 2;

    private final ClienteRepository clienteRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final MovimientoCreditoPospagoRepository movimientoCreditoPospagoRepository;
    private final FacturaPospagoRepository facturaPospagoRepository;
    private final SaldoServicioClienteRepository saldoServicioClienteRepository;
    private final com.polygraph.erp.modules.pagos.service.BolsaPrepagoService bolsaPrepagoService;
    private final UsuarioRepository usuarioRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final CiudadRepository ciudadRepository;
    private final PaisRepository paisRepository;
    private final BarrioRepository barrioRepository;
    private final SoporteClienteService soporteClienteService;
    private final NotificacionService notificacionService;
    private final ReasignacionGestorService reasignacionGestorService;
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

    /** Estado de cuenta de todos los clientes a la vez — pantalla "Gestión de cuentas": para
     *  prepago, cuántas unidades de servicio les quedan disponibles (sumadas entre procesos); para
     *  pospago, su cupo/crédito. */
    @Transactional(readOnly = true)
    public List<EstadoCuentaClienteResponse> listarEstadoCuentas() {
        return clienteRepository.findAll().stream()
                .map(this::mapearEstadoCuenta)
                .toList();
    }

    private EstadoCuentaClienteResponse mapearEstadoCuenta(Cliente cliente) {
        String nombre = nombreCliente(cliente);
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            ClientePospago pospago = clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).orElse(null);
            return new EstadoCuentaClienteResponse(
                    cliente.getIdCliente(), cliente.getNit(), nombre, "POSPAGO", null,
                    pospago != null ? pospago.getLimiteCredito() : BigDecimal.ZERO,
                    pospago != null ? pospago.getCreditoDisponible() : BigDecimal.ZERO,
                    pospago != null && pospago.getEstadoMora() != null ? pospago.getEstadoMora().name() : null,
                    pospago != null ? pospago.getRequiereAprobacion() : null);
        }
        int totalUnidades = bolsaPrepagoService.saldoPorServicio(cliente.getIdCliente()).totalDisponible();
        return new EstadoCuentaClienteResponse(
                cliente.getIdCliente(), cliente.getNit(), nombre, "PREPAGO", totalUnidades,
                null, null, null, null);
    }

    public ClienteAdminResponse actualizar(Integer id, ActualizarClienteAdminRequest req) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));

        if (req.tipoPersona() != null && !req.tipoPersona().equals(cliente.getTipoPersona().name())) {
            cambiarTipoPersona(cliente, req);
        }
        if (req.tipoCliente() != null && !req.tipoCliente().equals(cliente.getTipoCliente().name())) {
            cambiarTipoCliente(cliente, req);
        }

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
        if (req.idPais() != null) {
            if (!paisRepository.existsById(req.idPais())) {
                throw new ApiException("País no encontrado", HttpStatus.BAD_REQUEST);
            }
            cliente.setIdPais(req.idPais());
        }
        if (req.idBarrio() != null) {
            if (!barrioRepository.existsById(req.idBarrio())) {
                throw new ApiException("Barrio no encontrado", HttpStatus.BAD_REQUEST);
            }
            cliente.setIdBarrio(req.idBarrio());
        }
        Long idGestorAnterior = cliente.getIdGestor();
        if (req.idGestor()  != null) {
            Usuario gestor = usuarioRepository.findById(req.idGestor())
                    .orElseThrow(() -> new ApiException("Gestor no encontrado", HttpStatus.NOT_FOUND));
            if (gestor.getRol() != Rol.GESTOR) {
                throw new ApiException("El usuario seleccionado no tiene rol Gestor", HttpStatus.BAD_REQUEST);
            }
            boolean cambiaDeGestor = !gestor.getIdUsuario().equals(idGestorAnterior);
            if (cambiaDeGestor && !Boolean.TRUE.equals(gestor.getActivo())) {
                throw new ApiException("El gestor seleccionado está inactivo", HttpStatus.BAD_REQUEST);
            }
            cliente.setIdGestor(gestor.getIdUsuario());
            if (cambiaDeGestor) {
                reasignacionGestorService.avisarClienteNuevoGestor(cliente, gestor);
            }
        }

        String nombre = nombreCliente(cliente);
        log.info("Cliente {} actualizado", id);
        notificacionService.crearParaAdmins("CLIENTE", "Cliente actualizado",
                "Se modificó la información del cliente " + nombre
                + " (" + cliente.getTipoPersona().name() + " · " + cliente.getTipoCliente().name() + ")");

        // Antes el cambio de gestor solo generaba el aviso genérico de arriba a los roles
        // internos — el gestor nuevo no tenía ninguna señal puntual de que le cayó un cliente.
        if (req.idGestor() != null && !req.idGestor().equals(idGestorAnterior)) {
            notificacionService.crearParaUsuario(req.idGestor(), "CLIENTE_ASIGNADO",
                    "Te asignaron un cliente nuevo",
                    "Ahora eres el gestor de " + nombre + ".",
                    "CLIENTE", cliente.getIdCliente().longValue());
        }
        return toResponse(cliente);
    }

    /** Corrige el tipo de persona de un cliente ya registrado (ej. se registró como Natural
     *  siendo en realidad una empresa) — no existía ninguna forma de arreglar esto antes, el tipo
     *  quedaba fijo desde el registro (ver AuthService.registrarNatural/registrarJuridica). No se
     *  borran los campos del tipo anterior: si más adelante hay que revertir, no se pierden. */
    private void cambiarTipoPersona(Cliente cliente, ActualizarClienteAdminRequest req) {
        TipoPersona nuevoTipo;
        try {
            nuevoTipo = TipoPersona.valueOf(req.tipoPersona());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Tipo de persona inválido", HttpStatus.BAD_REQUEST);
        }

        if (nuevoTipo == TipoPersona.JURIDICA) {
            if (req.razonSocial() == null || req.razonSocial().isBlank()) {
                throw new ApiException("La razón social es obligatoria para cambiar a persona jurídica", HttpStatus.BAD_REQUEST);
            }
            if (req.nit() == null || req.nit().isBlank()) {
                throw new ApiException("El NIT es obligatorio para cambiar a persona jurídica", HttpStatus.BAD_REQUEST);
            }
            if (!req.nit().equals(cliente.getNit()) && clienteRepository.existsByNit(req.nit())) {
                throw new ApiException("Ya existe un cliente con ese NIT", HttpStatus.CONFLICT);
            }
            cliente.setNit(req.nit());
            cliente.setDv(req.dv());
        } else {
            if (req.nombre() == null || req.nombre().isBlank()) {
                throw new ApiException("El nombre es obligatorio para cambiar a persona natural", HttpStatus.BAD_REQUEST);
            }
        }
        cliente.setTipoPersona(nuevoTipo);
    }

    /** Cambia la modalidad de pago de un cliente ya registrado (ej. se registró prepago y llegó a
     *  un acuerdo de crédito, o al revés) — antes solo se fijaba en AuthService al registrarse, no
     *  había forma de corregirlo después.
     *  Pospago → prepago se bloquea si el cliente tiene consumo sin facturar o facturas sin pagar
     *  (para no perderle el rastro a plata que todavía debe); prepago → pospago crea la fila en
     *  clientes_pospago si no existe, con cupo en cero y requiereAprobacion=true — el gestor debe
     *  revisarle el crédito antes de que pueda solicitar (misma regla que un cliente pospago
     *  nuevo, ver "Cliente pospago requiere estudio de crédito previo" en CLAUDE.md). */
    private void cambiarTipoCliente(Cliente cliente, ActualizarClienteAdminRequest req) {
        TipoCliente nuevoTipo;
        try {
            nuevoTipo = TipoCliente.valueOf(req.tipoCliente());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Tipo de cliente inválido", HttpStatus.BAD_REQUEST);
        }

        if (nuevoTipo == TipoCliente.PREPAGO) {
            if (movimientoCreditoPospagoRepository.existsByCliente_IdClienteAndFacturaIsNullAndAnuladoEnIsNull(cliente.getIdCliente())) {
                throw new ApiException(
                        "Este cliente tiene consumo pospago sin facturar. Genera esa factura antes de cambiarlo a prepago.",
                        HttpStatus.CONFLICT);
            }
            if (facturaPospagoRepository.existsByCliente_IdClienteAndEstadoIn(
                    cliente.getIdCliente(), List.of(EstadoFacturaPospago.PENDIENTE, EstadoFacturaPospago.VENCIDA))) {
                throw new ApiException(
                        "Este cliente tiene facturas pospago sin pagar. No se puede cambiar a prepago con saldo pendiente.",
                        HttpStatus.CONFLICT);
            }
        } else {
            if (bolsaPrepagoService.saldoPorServicio(cliente.getIdCliente()).totalDisponible() > 0) {
                throw new ApiException(
                        "Este cliente todavía tiene saldo prepago sin usar. Debe consumirlo (o coordinarlo de otra forma) antes de cambiarlo a pospago.",
                        HttpStatus.CONFLICT);
            }
            if (clientePospagoRepository.findByIdCliente(cliente.getIdCliente()).isEmpty()) {
                // No fijar idCliente a mano acá: con @MapsId, Spring Data decide save() vs merge()
                // mirando si el id ya viene puesto — si lo ponemos, cree que la fila ya existe y
                // usa merge(), que revienta con "AssertionFailure: null identifier" porque el
                // @MapsId todavía no se resolvió en ese punto. Dejando null, detecta que es nueva
                // y usa persist(), que sí deriva el id de `cliente` correctamente (mismo patrón
                // que ya usa actualizarPospago más abajo).
                clientePospagoRepository.save(ClientePospago.builder()
                        .cliente(cliente)
                        .limiteCredito(BigDecimal.ZERO)
                        .creditoDisponible(BigDecimal.ZERO)
                        .estadoMora(EstadoMora.NORMAL)
                        .requiereAprobacion(true)
                        .build());
            }
        }
        cliente.setTipoCliente(nuevoTipo);
    }

    /** Asigna/ajusta el cupo de crédito de un cliente POSPAGO. Crea la fila en clientes_pospago si aún no existe. */
    public ClienteAdminResponse actualizarPospago(Integer id, ActualizarPospagoRequest req) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (cliente.getTipoCliente() != TipoCliente.POSPAGO) {
            throw new ApiException("Solo los clientes pospago tienen cupo de crédito", HttpStatus.BAD_REQUEST);
        }

        ClientePospago pospago = clientePospagoRepository.findByIdCliente(id)
                .orElseGet(() -> ClientePospago.builder().cliente(cliente)
                        .limiteCredito(java.math.BigDecimal.ZERO)
                        .creditoDisponible(java.math.BigDecimal.ZERO)
                        .build());

        boolean requeriaAprobacion = Boolean.TRUE.equals(pospago.getRequiereAprobacion());

        // creditoDisponible ya no lo edita el admin directamente (se descuenta/restituye solo con
        // el consumo y el pago de facturas) — al cambiar el límite, se ajusta por el delta para no
        // alterar el consumo ya registrado (ej. límite 1M, disponible 700k = 300k consumidos; si
        // sube el límite a 1.5M, el disponible pasa a 1.2M, sigue reflejando esos 300k consumidos).
        if (req.limiteCredito() != null) {
            java.math.BigDecimal limiteAnterior = pospago.getLimiteCredito() != null ? pospago.getLimiteCredito() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal disponibleAnterior = pospago.getCreditoDisponible() != null ? pospago.getCreditoDisponible() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal delta = req.limiteCredito().subtract(limiteAnterior);
            pospago.setLimiteCredito(req.limiteCredito());
            pospago.setCreditoDisponible(disponibleAnterior.add(delta));
        }
        if (req.estadoMora() != null) pospago.setEstadoMora(req.estadoMora());
        if (req.requiereAprobacion() != null) pospago.setRequiereAprobacion(req.requiereAprobacion());
        pospago.setUltimaRevisionCredito(LocalDate.now());
        clientePospagoRepository.save(pospago);

        String nombre = nombreCliente(cliente);
        log.info("Crédito pospago actualizado: cliente={}, limiteCredito={}, estadoMora={}",
                id, pospago.getLimiteCredito(), pospago.getEstadoMora());
        notificacionService.crearParaAdmins("CLIENTE", "Crédito pospago actualizado",
                "Se actualizó el cupo de crédito de " + nombre);

        // Antes el cliente no tenía ninguna señal de que ya lo aprobaron — el menú del portal se
        // actualizaba solo (ver ClientLayout.jsx) pero nadie le avisaba que podía volver a entrar.
        if (requeriaAprobacion && !Boolean.TRUE.equals(pospago.getRequiereAprobacion())) {
            notificacionService.crearParaCliente(id, "POSPAGO_APROBADO", "Tu crédito fue aprobado",
                    "Ya puedes solicitar servicios con tu cupo de crédito pospago.");
        }
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
                c.getIdCiudad(), nombreCiudad,
                null, null, null, null, 0, false);
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

    /** Los propios usuarios ANALISTA_CLIENTE del cliente autenticado — para la pantalla de
     *  autoservicio "Mi equipo" del portal cliente (distinta del listado completo de crearUsuario,
     *  que también incluye al ADMIN_CLIENTE y es solo para admins internos). */
    @Transactional(readOnly = true)
    public List<ClienteAdminResponse.UsuarioAsociado> listarUsuariosPropios(Integer idCliente) {
        return usuarioRepository.findByIdCliente(idCliente).stream()
                .filter(u -> u.getRol() == Rol.ANALISTA_CLIENTE)
                .map(ClienteAdminResponse.UsuarioAsociado::from)
                .toList();
    }

    /** El propio ADMIN_CLIENTE crea uno de sus usuarios ANALISTA_CLIENTE, sin pasar por un admin
     *  interno — a diferencia de crearUsuario() (que sí puede crear ADMIN_CLIENTE también y no
     *  tiene tope), esto SIEMPRE crea ANALISTA_CLIENTE y tiene un tope de
     *  MAX_ANALISTAS_CLIENTE por cliente. Al llegar al tope, el frontend dirige al cliente a
     *  pedírselo por el chat a su gestor (ver crearUsuarioComoGestor, que sí puede pasarse el
     *  tope). */
    public ClienteAdminResponse.UsuarioAsociado crearUsuarioPropio(Integer idCliente, CrearUsuarioPropioRequest req) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (contarAnalistas(idCliente) >= MAX_ANALISTAS_CLIENTE) {
            throw new ApiException(
                    "Ya tienes el máximo de " + MAX_ANALISTAS_CLIENTE + " usuarios. Escríbele a tu gestor si necesitas uno adicional.",
                    HttpStatus.CONFLICT);
        }
        Usuario usuario = crearAnalistaCliente(cliente, req);
        notificacionService.crearParaAdmins("USUARIO", "Nuevo usuario de cliente creado",
                "El cliente " + nombreCliente(cliente) + " creó su propio usuario " + req.nombre() + " (ANALISTA_CLIENTE)");
        return ClienteAdminResponse.UsuarioAsociado.from(usuario);
    }

    /** El gestor asignado al cliente le crea un usuario ANALISTA_CLIENTE adicional — la vía para
     *  cuando el cliente ya llegó al tope de crearUsuarioPropio() y le escribe pidiendo uno más
     *  (ver mensaje de esa excepción). Sin tope: es una excepción puntual que decide el gestor,
     *  no una segunda forma de autoservicio. Solo el gestor asignado a ese cliente puede hacerlo
     *  (o cualquiera si el cliente todavía no tiene gestor asignado). */
    public ClienteAdminResponse.UsuarioAsociado crearUsuarioComoGestor(Integer idCliente, Long idGestor, CrearUsuarioPropioRequest req) {
        Cliente cliente = validarClienteDeGestor(idCliente, idGestor);
        Usuario usuario = crearAnalistaCliente(cliente, req);
        notificacionService.crearParaAdmins("USUARIO", "Nuevo usuario de cliente creado",
                "El gestor le creó un usuario adicional (" + req.nombre() + ", ANALISTA_CLIENTE) a " + nombreCliente(cliente));
        return ClienteAdminResponse.UsuarioAsociado.from(usuario);
    }

    /** Los usuarios ANALISTA_CLIENTE de un cliente asignado a este gestor — para que el gestor
     *  vea con quién está tratando antes de crearle uno adicional. */
    @Transactional(readOnly = true)
    public List<ClienteAdminResponse.UsuarioAsociado> listarUsuariosComoGestor(Integer idCliente, Long idGestor) {
        validarClienteDeGestor(idCliente, idGestor);
        return listarUsuariosPropios(idCliente);
    }

    private Cliente validarClienteDeGestor(Integer idCliente, Long idGestor) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        if (cliente.getIdGestor() != null && !cliente.getIdGestor().equals(idGestor)) {
            throw new ApiException("Este cliente no está asignado a ti.", HttpStatus.FORBIDDEN);
        }
        return cliente;
    }

    private long contarAnalistas(Integer idCliente) {
        return usuarioRepository.findByIdCliente(idCliente).stream()
                .filter(u -> u.getRol() == Rol.ANALISTA_CLIENTE)
                .count();
    }

    private Usuario crearAnalistaCliente(Cliente cliente, CrearUsuarioPropioRequest req) {
        if (usuarioRepository.existsByEmail(req.email())) {
            throw new ApiException("El email ya está registrado", HttpStatus.CONFLICT);
        }
        Usuario usuario = Usuario.builder()
                .nombre(req.nombre())
                .apellido(req.apellido())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .rol(Rol.ANALISTA_CLIENTE)
                .activo(true)
                .requiere2fa(false)
                .emailVerificado(true)
                .idCliente(cliente.getIdCliente())
                .fechaCreacion(LocalDateTime.now())
                .requiereCambioPassword(true)
                .build();
        usuarioRepository.save(usuario);
        log.info("Usuario ANALISTA_CLIENTE {} creado para cliente {}", req.email(), cliente.getIdCliente());
        return usuario;
    }

    /** El propio ADMIN_CLIENTE edita uno de sus usuarios ANALISTA_CLIENTE (nombre/email/
     *  contraseña) — no puede tocar su propia cuenta por acá (para eso está /client/perfil) ni
     *  cambiarle el rol. */
    public ClienteAdminResponse.UsuarioAsociado actualizarUsuarioPropio(Integer idCliente, Long idUsuario, ActualizarUsuarioPropioRequest req) {
        Usuario usuario = obtenerAnalistaPropio(idCliente, idUsuario);
        if (!usuario.getEmail().equals(req.email()) && usuarioRepository.existsByEmail(req.email())) {
            throw new ApiException("El email ya está en uso por otro usuario", HttpStatus.CONFLICT);
        }
        usuario.setNombre(req.nombre());
        usuario.setApellido(req.apellido());
        usuario.setEmail(req.email());
        if (req.password() != null && !req.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(req.password()));
        }
        log.info("Usuario propio {} actualizado por cliente {}", usuario.getEmail(), idCliente);
        return ClienteAdminResponse.UsuarioAsociado.from(usuario);
    }

    /** El propio ADMIN_CLIENTE activa/desactiva uno de sus usuarios ANALISTA_CLIENTE (ej. alguien
     *  que ya no trabaja ahí) — no libera un puesto del tope de MAX_ANALISTAS_CLIENTE, sigue
     *  contando: para eso está eliminarlo, no desactivarlo. */
    public ClienteAdminResponse.UsuarioAsociado cambiarEstadoUsuarioPropio(Integer idCliente, Long idUsuario, boolean activar) {
        Usuario usuario = obtenerAnalistaPropio(idCliente, idUsuario);
        usuario.setActivo(activar);
        log.info("Usuario propio {} de cliente {} -> activo={}", usuario.getEmail(), idCliente, activar);
        return ClienteAdminResponse.UsuarioAsociado.from(usuario);
    }

    private Usuario obtenerAnalistaPropio(Integer idCliente, Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        if (!idCliente.equals(usuario.getIdCliente()) || usuario.getRol() != Rol.ANALISTA_CLIENTE) {
            throw new ApiException("Este usuario no es uno de tus usuarios.", HttpStatus.FORBIDDEN);
        }
        return usuario;
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
        String nombrePais = c.getIdPais() != null
                ? paisRepository.findById(c.getIdPais()).map(Pais::getNombre).orElse(null)
                : null;
        String nombreBarrio = c.getIdBarrio() != null
                ? barrioRepository.findById(c.getIdBarrio()).map(Barrio::getNombre).orElse(null)
                : null;
        return ClienteAdminResponse.from(c, usuarioPrincipal, usuariosCliente, pospago, resolverGestor(c), nombreCiudad,
                nombrePais, nombreBarrio,
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
