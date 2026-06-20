package com.polygraph.erp.modules.dashboard.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse;
import com.polygraph.erp.modules.dashboard.dto.ServiciosPorEstadoResponse;
import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.modules.servicios.repository.CatalogoServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.enums.CategoriaServicio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoPersona;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAdminService {

    private static final Set<Rol> ROLES_INTERNOS = Set.of(
            Rol.ADMIN_POLYGRAPH, Rol.GESTOR, Rol.ANALISTA_INTERNO,
            Rol.PROGRAMADOR, Rol.POLIGRAFISTA, Rol.VISITADOR
    );

    private static final Set<Rol> ROLES_CLIENTES = Set.of(
            Rol.ADMIN_CLIENTE, Rol.ANALISTA_CLIENTE
    );

    private final UsuarioRepository usuarioRepository;
    private final CatalogoServicioRepository catalogoServicioRepository;
    private final ServicioRepository servicioRepository;
    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public DashboardAdminResponse obtenerDashboard() {
        long totalActivos     = usuarioRepository.countByActivoTrue();
        long internosActivos  = usuarioRepository.countByActivoTrueAndRolIn(ROLES_INTERNOS);
        long clientesActivos  = usuarioRepository.countByActivoTrueAndRolIn(ROLES_CLIENTES);

        List<DashboardAdminResponse.UltimoLoginDto> ultimosLogins =
                usuarioRepository.findTop8ByUltimoAccesoIsNotNullOrderByUltimoAccesoDesc()
                        .stream()
                        .map(u -> new DashboardAdminResponse.UltimoLoginDto(
                                u.getIdUsuario(), u.getNombre(), u.getApellido(),
                                u.getEmail(), u.getRol().name(), u.getActivo(), u.getUltimoAcceso()
                        ))
                        .toList();

        Map<CategoriaServicio, List<CatalogoServicio>> porCategoria =
                catalogoServicioRepository.findAll().stream()
                        .collect(Collectors.groupingBy(CatalogoServicio::getCategoria));

        List<DashboardAdminResponse.ServicioCategoriaDto> serviciosPorCategoria =
                porCategoria.entrySet().stream()
                        .map(e -> new DashboardAdminResponse.ServicioCategoriaDto(
                                e.getKey().name(),
                                e.getValue().size(),
                                e.getValue().stream().filter(s -> Boolean.TRUE.equals(s.getActivo())).count()
                        ))
                        .sorted(Comparator.comparingLong(DashboardAdminResponse.ServicioCategoriaDto::total).reversed())
                        .toList();

        return new DashboardAdminResponse(totalActivos, internosActivos, clientesActivos,
                ultimosLogins, serviciosPorCategoria);
    }

    @Transactional(readOnly = true)
    public ServiciosPorEstadoResponse serviciosPorEstado(Integer idCliente) {
        List<ServiciosPorEstadoResponse.ClienteResumenDto> clientes = clienteRepository.findAll()
                .stream()
                .map(c -> {
                    String nombre = TipoPersona.JURIDICA.equals(c.getTipoPersona())
                            ? c.getRazonSocial()
                            : (c.getNombre() != null ? c.getNombre() + " " + (c.getApellido() != null ? c.getApellido() : "") : c.getEmailPrincipal());
                    return new ServiciosPorEstadoResponse.ClienteResumenDto(c.getIdCliente(), nombre.trim());
                })
                .sorted(Comparator.comparing(ServiciosPorEstadoResponse.ClienteResumenDto::nombre))
                .toList();

        List<ServiciosPorEstadoResponse.EstadoDto> estados = Arrays.stream(EstadoServicio.values())
                .map(e -> {
                    long total = idCliente != null
                            ? servicioRepository.countByCliente_IdClienteAndEstado(idCliente, e)
                            : servicioRepository.countByEstado(e);
                    return new ServiciosPorEstadoResponse.EstadoDto(e.name(), total);
                })
                .toList();

        long totalServicios = estados.stream().mapToLong(ServiciosPorEstadoResponse.EstadoDto::total).sum();

        return new ServiciosPorEstadoResponse(clientes, estados, totalServicios);
    }
}
