package com.polygraph.erp.modules.busqueda.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.busqueda.dto.ResultadoBusquedaResponse;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Buscador global — cada rol ve categorías y alcance distintos, según lo que ya administra en el
 *  resto del ERP:
 *
 *  - ADMIN_POLYGRAPH: Clientes, Equipo (usuarios internos) y Servicios — de TODOS los clientes.
 *  - GESTOR: Candidatos y Servicios — solo de SUS clientes (mismo alcance que ya usa el resto de
 *    endpoints de gestor, vía Cliente.idGestor). Servicios navega al Servicio completo
 *    (/gestor/solicitudes?id=) porque gestor supervisa, no llena formularios.
 *  - ANALISTA_INTERNO / POLIGRAFISTA / VISITADOR / PROGRAMADOR: Candidatos y Servicios, pero SOLO
 *    los suyos — mismo alcance que ya usa CandidatoHojaVidaService para decidir si pueden ver la
 *    hoja de vida de un candidato (usuarioAsignado, o programadorResponsable para PROGRAMADOR).
 *    Servicios navega directo al formulario del subproceso puntual (.../formulario/:id), porque
 *    estos roles trabajan uno a uno por subproceso, no por Servicio completo.
 *
 *  Clientes/Equipo solo aparecen para ADMIN_POLYGRAPH porque son los únicos controladores
 *  (ClienteAdminController, UsuariosInternosController) que los exponen — extender esas
 *  categorías a otro rol sería saltarse una restricción de seguridad que ya existe, no solo
 *  agregar una pantalla. Servicios para ADMIN_POLYGRAPH no lleva al detalle exacto porque
 *  SemaforoServicios.jsx no tiene esa capacidad todavía (a diferencia de Solicitudes.jsx de
 *  GESTOR) — se manda al tablero general. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusquedaGlobalService {

    private static final int LIMITE_POR_CATEGORIA = 6;

    private static final Set<Rol> ROLES_EJECUTORES = Set.of(
            Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR, Rol.PROGRAMADOR);

    private static final Map<Rol, String> PREFIJO_RUTA = new EnumMap<>(Rol.class);
    static {
        PREFIJO_RUTA.put(Rol.GESTOR, "/gestor");
        PREFIJO_RUTA.put(Rol.ANALISTA_INTERNO, "/analista");
        PREFIJO_RUTA.put(Rol.POLIGRAFISTA, "/poligrafista");
        PREFIJO_RUTA.put(Rol.VISITADOR, "/visitador");
        PREFIJO_RUTA.put(Rol.PROGRAMADOR, "/programador");
    }

    private final ClienteRepository clienteRepository;
    private final CandidatoRepository candidatoRepository;
    private final UsuariosIntenosRepository empleadoRepository;
    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;

    public List<ResultadoBusquedaResponse> buscar(String q, Usuario actor) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 2) return List.of();

        // Armado en Java, no con concat(...) en el JPQL: Hibernate 6 traduce ese concat con un
        // "escape ''" que hace que el LIKE no encuentre nada nunca en H2 (y es SQL ambiguo en
        // cualquier motor) — confirmado con ServicioRepositoryBusquedaTest.
        String patron = "%" + query.toLowerCase() + "%";
        Pageable limite = PageRequest.of(0, LIMITE_POR_CATEGORIA);
        List<ResultadoBusquedaResponse> resultados = new ArrayList<>();
        Rol rol = actor.getRol();

        if (rol == Rol.ADMIN_POLYGRAPH) {
            clienteRepository.buscar(patron, limite).forEach(c -> resultados.add(deCliente(c)));
            empleadoRepository.buscar(patron, limite).forEach(e -> resultados.add(deEmpleado(e)));
            servicioRepository.buscar(patron, null, limite).forEach(s -> resultados.add(deServicio(s, "/admin/semaforo")));
        } else if (rol == Rol.GESTOR) {
            candidatoRepository.buscar(patron, limite).forEach(c -> resultados.add(deCandidato(c, "/gestor")));
            servicioRepository.buscar(patron, actor.getIdUsuario(), limite)
                    .forEach(s -> resultados.add(deServicio(s, "/gestor/solicitudes?id=" + s.getIdServicio())));
        } else if (ROLES_EJECUTORES.contains(rol)) {
            String prefijo = PREFIJO_RUTA.get(rol);
            candidatoRepository.buscarAsignadoA(patron, actor.getIdUsuario(), limite)
                    .forEach(c -> resultados.add(deCandidato(c, prefijo)));
            servicioSubprocesoRepository.buscarAsignadoA(patron, actor.getIdUsuario(), limite)
                    .forEach(sp -> resultados.add(deSubproceso(sp, prefijo)));
        }
        return resultados;
    }

    private ResultadoBusquedaResponse deCliente(Cliente c) {
        String nombre = nombreCliente(c);
        return new ResultadoBusquedaResponse("CLIENTE", nombre, c.getEmailPrincipal(),
                "/admin/clientes?q=" + codificar(nombre), "🏢");
    }

    private ResultadoBusquedaResponse deEmpleado(UsuariosInternos e) {
        String nombre = e.getNombre() + (e.getApellido() != null ? " " + e.getApellido() : "");
        return new ResultadoBusquedaResponse("EQUIPO", nombre, e.getEmail(),
                "/admin/usuarios-internos?q=" + codificar(nombre), "👥");
    }

    private ResultadoBusquedaResponse deCandidato(Candidato c, String prefijoRuta) {
        String nombre = c.getNombres() + " " + c.getApellidos();
        return new ResultadoBusquedaResponse("CANDIDATO", nombre, "C.C. " + c.getCedula(),
                prefijoRuta + "/candidatos/" + c.getIdCandidato(), "🧑");
    }

    private ResultadoBusquedaResponse deServicio(Servicio s, String ruta) {
        String subtitulo = nombreCandidatoDe(s.getCandidato()) + nombreClienteDe(s.getCliente()) + " · " + s.getEstado();
        return new ResultadoBusquedaResponse("SERVICIO", s.getProceso().getNombreProceso(), subtitulo, ruta, "🦺");
    }

    private ResultadoBusquedaResponse deSubproceso(ServicioSubproceso sp, String prefijoRuta) {
        Servicio s = sp.getServicio();
        String subtitulo = nombreCandidatoDe(s.getCandidato()) + " · " + sp.getEstado();
        return new ResultadoBusquedaResponse("SERVICIO", s.getProceso().getNombreProceso(), subtitulo,
                prefijoRuta + "/formulario/" + sp.getId(), "🦺");
    }

    private String nombreCandidatoDe(Candidato c) {
        return c != null ? c.getNombres() + " " + c.getApellidos() : "Sin candidato";
    }

    private String nombreClienteDe(Cliente c) {
        if (c == null) return "";
        String nombre = c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombre();
        return nombre != null ? " · " + nombre : "";
    }

    private String nombreCliente(Cliente c) {
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        if (c.getNombreComercial() != null && !c.getNombreComercial().isBlank()) return c.getNombreComercial();
        return c.getNombre() + (c.getApellido() != null ? " " + c.getApellido() : "");
    }

    private String codificar(String texto) {
        return UriUtils.encode(texto, StandardCharsets.UTF_8);
    }
}
