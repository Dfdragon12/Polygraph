package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.dto.ProductividadResponse;
import com.polygraph.erp.modules.servicios.dto.ProductividadResponse.EstadoConteo;
import com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.RegistroCambioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.ProductividadDiaria;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma los datos del reporte diario de productividad (SE-11) y se los pasa al cálculo puro {@link ProductividadDiaria}. Solo lee: el reporte sale del
 * registro de cambios (SE-03), nunca cambia nada. Alcance: el administrador ve todo; el gestor, los clientes que atiende; cada ejecutor, solo lo suyo.
 */
@Service
@RequiredArgsConstructor
public class ProductividadService {

    /** Un rango más largo se pide por partes: cada día recorre todo el registro. */
    public static final int MAX_DIAS = 93;
    private static final int LOTE = 2000;

    private final EntityManager em;
    private final RegistroCambioSubprocesoRepository registroRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock reloj;

    public record Filtros(LocalDate desde, LocalDate hasta, Rol perfil, Long idPersona, Integer idTipoProgreso, Integer idCliente) {}

    @Transactional(readOnly = true)
    public ProductividadResponse reporte(Usuario quien, Filtros f) {
        LocalDate hoy = LocalDate.now(reloj.withZone(RegistroCambiosService.ZONA));
        LocalDate hasta = f.hasta() != null ? f.hasta() : (f.desde() != null ? f.desde() : hoy);
        LocalDate desde = f.desde() != null ? f.desde() : hasta;
        if (hasta.isBefore(desde)) throw new ApiException("La fecha final no puede ser anterior a la inicial.", HttpStatus.BAD_REQUEST);
        if (desde.plusDays(MAX_DIAS).isBefore(hasta)) {
            throw new ApiException("El rango no puede pasar de " + MAX_DIAS + " días: pide el reporte por partes.", HttpStatus.BAD_REQUEST);
        }
        if (hasta.isAfter(hoy)) hasta = hoy;                       // un día futuro no tiene nada que contar
        if (desde.isAfter(hasta)) desde = hasta;

        Long soloGestor = quien.getRol() == Rol.GESTOR ? quien.getIdUsuario() : null;
        Long persona = f.idPersona();
        if (quien.getRol() != Rol.ADMIN_POLYGRAPH && quien.getRol() != Rol.GESTOR) persona = quien.getIdUsuario();     // un ejecutor solo ve lo suyo

        // ── universo: subprocesos (con su cliente y gestor) ──
        List<ServicioSubproceso> alcance = subprocesos(soloGestor);
        List<ServicioSubproceso> universo = alcance.stream()
                .filter(sp -> f.idCliente() == null || (sp.getServicio().getCliente() != null && f.idCliente().equals(sp.getServicio().getCliente().getIdCliente())))
                .filter(sp -> f.idTipoProgreso() == null || f.idTipoProgreso().equals(sp.getTipoProgreso().getIdTipoProgreso())).toList();
        List<ProductividadDiaria.Subproceso> subs = new ArrayList<>();
        Map<Integer, ProductividadDiaria.Servicio> servicios = new HashMap<>();
        Set<Long> idsUsuarios = new HashSet<>();
        for (ServicioSubproceso sp : universo) {
            Servicio s = sp.getServicio();
            Long gestor = s.getCliente() == null ? null : s.getCliente().getIdGestor();
            Long programador = sp.getProgramadorResponsable() == null ? null : sp.getProgramadorResponsable().getIdUsuario();
            subs.add(new ProductividadDiaria.Subproceso(sp.getId(), s.getIdServicio(), gestor, programador, s.getFechaSolicitud()));
            servicios.putIfAbsent(s.getIdServicio(), new ProductividadDiaria.Servicio(s.getIdServicio(), s.getFechaCompromisoAjustada(),
                    s.getEstadoTiempo() == EstadoTiempo.VENCIDA));
            if (gestor != null) idsUsuarios.add(gestor);
            if (programador != null) idsUsuarios.add(programador);
        }

        // ── registro de cambios de esos subprocesos, hasta el corte del último día ──
        var fin = hasta.plusDays(1).atStartOfDay();
        List<ProductividadDiaria.Evento> eventos = new ArrayList<>();
        List<Long> ids = subs.stream().map(ProductividadDiaria.Subproceso::id).toList();
        for (int i = 0; i < ids.size(); i += LOTE) {
            for (RegistroCambioSubproceso r : registroRepository.findByIdServicioSubprocesoInAndFechaBogotaBeforeOrderByFechaBogotaAscIdAsc(
                    ids.subList(i, Math.min(i + LOTE, ids.size())), fin)) {
                eventos.add(new ProductividadDiaria.Evento(r.getIdServicioSubproceso(), estado(r.getEstadoAnterior()), estado(r.getEstadoNuevo()),
                        r.getMarca(), r.getMotivo(), r.getIdResponsable(), r.getFechaBogota()));
                if (r.getIdResponsable() != null) idsUsuarios.add(r.getIdResponsable());
            }
        }

        Map<Long, Usuario> usuarios = usuarioRepository.findAllById(idsUsuarios).stream().collect(Collectors.toMap(Usuario::getIdUsuario, u -> u));
        Map<Long, Rol> roles = new HashMap<>();
        usuarios.forEach((id, u) -> roles.put(id, u.getRol()));

        var calculo = ProductividadDiaria.calcular(desde, hasta, hoy, subs, eventos, servicios, roles);

        final Long personaFiltro = persona;
        List<ProductividadResponse.FilaPersona> filasPersona = calculo.personas().stream()
                .filter(p -> personaFiltro == null || personaFiltro.equals(p.idPersona()))
                .filter(p -> f.perfil() == null || f.perfil() == p.rol())
                .filter(p -> p.rol() != Rol.GESTOR)
                .map(p -> new ProductividadResponse.FilaPersona(p.fecha(), p.idPersona(), nombre(usuarios, p.idPersona()), p.rol() == null ? null : p.rol().name(),
                        p.asignados(), p.finalizados(), p.publicados(), p.devueltos(), p.reprogramados(), p.abiertosAlCierre()))
                .toList();
        boolean verGestores = (f.perfil() == null || f.perfil() == Rol.GESTOR) && (quien.getRol() == Rol.ADMIN_POLYGRAPH || quien.getRol() == Rol.GESTOR);
        List<ProductividadResponse.FilaGestor> filasGestor = !verGestores ? List.of() : calculo.gestores().stream()
                .filter(g -> personaFiltro == null || personaFiltro.equals(g.idGestor()))
                .map(g -> new ProductividadResponse.FilaGestor(g.fecha(), g.idGestor(), nombre(usuarios, g.idGestor()), g.porVerificarAlCierre(), g.totalServicios(),
                        g.serviciosPorEstado().entrySet().stream()
                                .map(e -> new EstadoConteo(e.getKey().name(), e.getKey().nombre(), e.getValue().servicios(), e.getValue().subprocesos())).toList(),
                        g.estudiosCompletosPublicados(), g.vencidosAlCierre(), g.vencidosAproximado()))
                .toList();
        Map<Long, String> clientesOp = new java.util.TreeMap<>(), tiposOp = new java.util.TreeMap<>();
        for (ServicioSubproceso sp : alcance) {
            if (sp.getServicio().getCliente() != null) clientesOp.put(sp.getServicio().getCliente().getIdCliente().longValue(), nombreCliente(sp.getServicio().getCliente()));
            tiposOp.put(sp.getTipoProgreso().getIdTipoProgreso().longValue(), sp.getTipoProgreso().getNombreProgreso());
        }
        List<ProductividadResponse.Opcion> personasOp = usuarios.values().stream().filter(u -> u.getRol() != null && u.getRol() != Rol.ADMIN_POLYGRAPH)
                .map(u -> new ProductividadResponse.Opcion(u.getIdUsuario(), nombre(usuarios, u.getIdUsuario()))).toList();
        var opciones = new ProductividadResponse.Opciones(
                clientesOp.entrySet().stream().map(e -> new ProductividadResponse.Opcion(e.getKey(), e.getValue())).toList(),
                tiposOp.entrySet().stream().map(e -> new ProductividadResponse.Opcion(e.getKey(), e.getValue())).toList(), personasOp);
        return new ProductividadResponse(desde, hasta, "11:59 p. m.", RegistroCambiosService.ZONA.getId(), filasPersona, filasGestor, opciones);
    }

    private List<ServicioSubproceso> subprocesos(Long idGestor) {
        StringBuilder q = new StringBuilder("SELECT sp FROM ServicioSubproceso sp JOIN FETCH sp.servicio s LEFT JOIN FETCH s.cliente c "
                + "JOIN FETCH sp.tipoProgreso LEFT JOIN FETCH sp.programadorResponsable WHERE 1=1");
        if (idGestor != null) q.append(" AND c.idGestor = :idGestor");
        TypedQuery<ServicioSubproceso> t = em.createQuery(q.toString(), ServicioSubproceso.class);
        if (idGestor != null) t.setParameter("idGestor", idGestor);
        return t.getResultList();
    }

    private static String nombreCliente(com.polygraph.erp.modules.clientes.entity.Cliente c) {
        return c.getNombreComercial() != null && !c.getNombreComercial().isBlank() ? c.getNombreComercial()
                : c.getRazonSocial() != null ? c.getRazonSocial() : "Cliente " + c.getIdCliente();
    }

    private static EstadoSubproceso estado(String texto) {
        return texto == null ? null : EstadoSubproceso.valueOf(texto);
    }

    private static String nombre(Map<Long, Usuario> usuarios, Long id) {
        Usuario u = id == null ? null : usuarios.get(id);
        return u == null || u.getNombre() == null ? "Usuario " + id : u.getNombre();
    }
}
