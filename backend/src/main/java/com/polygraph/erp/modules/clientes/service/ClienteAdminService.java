package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.dto.ActualizarClienteAdminRequest;
import com.polygraph.erp.modules.clientes.dto.ClienteAdminResponse;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteAdminService {

    private final ClienteRepository clienteRepository;
    private final ClientePospagoRepository clientePospagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

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

        String nombre = nombreCliente(cliente);
        log.info("Cliente {} actualizado", id);
        notificacionService.crearParaAdmins("CLIENTE", "Cliente actualizado",
                "Se modificó la información del cliente " + nombre
                + " (" + cliente.getTipoPersona().name() + " · " + cliente.getTipoCliente().name() + ")");
        return toResponse(cliente);
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

    private String nombreCliente(Cliente c) {
        if ("NATURAL".equals(c.getTipoPersona().name())) {
            return (c.getNombre() != null ? c.getNombre() : "")
                    + (c.getApellido() != null ? " " + c.getApellido() : "");
        }
        return c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreComercial();
    }

    private ClienteAdminResponse toResponse(Cliente c) {
        Usuario usuario = usuarioRepository
                .findFirstByIdClienteAndRol(c.getIdCliente(), Rol.ADMIN_CLIENTE)
                .orElse(null);
        ClientePospago pospago = "POSPAGO".equals(c.getTipoCliente().name())
                ? clientePospagoRepository.findByIdCliente(c.getIdCliente()).orElse(null)
                : null;
        return ClienteAdminResponse.from(c, usuario, pospago);
    }
}
