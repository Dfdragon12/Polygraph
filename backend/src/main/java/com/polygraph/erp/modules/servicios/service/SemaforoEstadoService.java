package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.servicios.dto.SemaforoResponse;
import com.polygraph.erp.modules.servicios.dto.SemaforoResponse.EstadoConteo;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.ResumenEstado;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila.SubprocesoEnFila;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Filtros;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Solicitante;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.estados.SemaforoEstados;
import com.polygraph.erp.shared.estados.SemaforoEstados.Avance;
import com.polygraph.erp.shared.estados.SemaforoEstados.Conteo;
import com.polygraph.erp.shared.estados.SemaforoEstados.Fila;
import com.polygraph.erp.shared.estados.SemaforoEstados.Resultado;
import com.polygraph.erp.shared.service.TiempoServicioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El semáforo de estado de TODAS las pantallas (SE-04): un solo servicio calcula los contadores y la lista filtrada. El alcance por
 * perfil lo decide {@link ConsultaSemaforo}; el conteo, {@link SemaforoEstados}. Las pantallas solo leen: ningún conteo se calcula en el
 * navegador.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SemaforoEstadoService {

    @PersistenceContext
    private EntityManager em;

    private final ServicioRepository servicioRepository;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final TiempoServicioService tiempoServicioService;
    private final com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository novedadRepository;
    private final java.time.Clock reloj;

    // ───────────────────────── contadores ─────────────────────────

    public SemaforoResponse resumen(Solicitante quien, Filtros filtros) {
        List<Fila> filas = filas(quien, filtros);
        Resultado r = SemaforoEstados.contar(filas);
        boolean porSubprocesos = SemaforoEstados.cuentaSubprocesos(quien.rol());
        List<EstadoConteo> estados = new ArrayList<>();
        for (EstadoSubproceso e : SemaforoEstados.estadosVisibles(quien.rol())) {
            Conteo c = r.porEstado().get(e);
            estados.add(new EstadoConteo(e.name(), e.nombre(), e.color(), e.orden(), e.textoCliente(), c.servicios(), c.subprocesos()));
        }
        int total = porSubprocesos ? filas.size() : r.totalServicios();
        return new SemaforoResponse(porSubprocesos ? "SUBPROCESOS" : "SERVICIOS", total, estados);
    }

    // ───────────────────────── alertas aparte ─────────────────────────

    /** Sin asignar, Novedades abiertas, Citas de hoy, Vencidas, Para hoy y Por confirmar: debajo del semáforo y con otro estilo (SE-09). */
    @SuppressWarnings("unchecked")
    public com.polygraph.erp.modules.servicios.dto.AlertasResponse alertas(Solicitante quien, Filtros filtros) {
        ConsultaSemaforo.Consulta c = ConsultaSemaforo.filasParaAlertas(quien, filtros);
        Query q = em.createQuery(c.jpql());
        c.parametros().forEach(q::setParameter);
        List<com.polygraph.erp.shared.estados.AlertasAgenda.Fila> filas = new ArrayList<>();
        for (Object[] r : (List<Object[]>) q.getResultList()) {
            filas.add(new com.polygraph.erp.shared.estados.AlertasAgenda.Fila((Integer) r[0], (Long) r[1], (EstadoSubproceso) r[2], r[3] != null,
                    (java.time.LocalDateTime) r[4], (com.polygraph.erp.shared.enums.Rol) r[5], (com.polygraph.erp.shared.enums.EstadoTiempo) r[6],
                    (Integer) r[7], Boolean.TRUE.equals(r[8])));
        }
        long novedades = filas.isEmpty() ? 0 : novedadRepository.contarPorSubprocesos(com.polygraph.erp.shared.enums.EstadoNovedad.ABIERTA,
                filas.stream().map(com.polygraph.erp.shared.estados.AlertasAgenda.Fila::idSubproceso).toList());
        var ahora = java.time.LocalDateTime.now(reloj.withZone(com.polygraph.erp.modules.servicios.service.RegistroCambiosService.ZONA));
        var r = com.polygraph.erp.shared.estados.AlertasAgenda.calcular(quien.rol(), filas, novedades, ahora);
        return new com.polygraph.erp.modules.servicios.dto.AlertasResponse(r.sinAsignar(), r.novedadesAbiertas(), r.citasDeHoy(), r.vencidas(), r.paraHoy(), r.porConfirmar());
    }

    // ───────────────────────── lista filtrada ─────────────────────────

    /**
     * Los servicios que pertenecen al estado (al menos un subproceso en él); sin estado, todos los del alcance. Cada fila trae el
     * resumen por estado, el avance, la etiqueta de publicación, el tiempo y los subprocesos, con los del estado elegido resaltados.
     */
    public Page<ServicioSemaforoFila> servicios(Solicitante quien, Filtros filtros, EstadoSubproceso estado, Pageable pageable) {
        if (estado != null && !SemaforoEstados.estadosVisibles(quien.rol()).contains(estado)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<Fila> filas = filas(quien, filtros);
        Set<Integer> ids = estado == null
                ? filas.stream().map(Fila::idServicio).collect(Collectors.toCollection(LinkedHashSet::new))
                : SemaforoEstados.serviciosEn(estado, filas);
        List<Integer> ordenados = ids.stream().sorted(java.util.Comparator.reverseOrder()).toList();   // el más reciente primero
        int desde = (int) Math.min(pageable.getOffset(), ordenados.size());
        int hasta = Math.min(desde + pageable.getPageSize(), ordenados.size());
        List<Integer> pagina = ordenados.subList(desde, hasta);
        if (pagina.isEmpty()) return new PageImpl<>(List.of(), pageable, ordenados.size());

        Map<Integer, Servicio> servicios = servicioRepository.findAllById(pagina).stream()
                .collect(Collectors.toMap(Servicio::getIdServicio, Function.identity()));
        Map<Integer, List<ServicioSubproceso>> subprocesos = subprocesoRepository
                .findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(pagina).stream()
                .filter(sp -> esDelAlcance(quien, sp))
                .collect(Collectors.groupingBy(sp -> sp.getServicio().getIdServicio()));

        List<ServicioSemaforoFila> rows = new ArrayList<>();
        for (Integer id : pagina) {
            Servicio s = servicios.get(id);
            if (s != null) rows.add(fila(s, subprocesos.getOrDefault(id, List.of()), estado));
        }
        return new PageImpl<>(rows, pageable, ordenados.size());
    }

    // ───────────────────────── internos ─────────────────────────

    @SuppressWarnings("unchecked")
    private List<Fila> filas(Solicitante quien, Filtros filtros) {
        ConsultaSemaforo.Consulta c = ConsultaSemaforo.filas(quien, filtros);
        Query q = em.createQuery(c.jpql());
        c.parametros().forEach(q::setParameter);
        List<Object[]> resultado = q.getResultList();
        List<Fila> filas = new ArrayList<>(resultado.size());
        for (Object[] r : resultado) filas.add(new Fila((Integer) r[0], (EstadoSubproceso) r[1]));
        return filas;
    }

    /** Quien ejecuta o coordina solo ve en la fila SUS subprocesos (y de los estados de su perfil); los demás perfiles, todos. */
    private static boolean esDelAlcance(Solicitante quien, ServicioSubproceso sp) {
        if (!SemaforoEstados.cuentaSubprocesos(quien.rol())) return true;
        if (!SemaforoEstados.estadosVisibles(quien.rol()).contains(sp.getEstado())) return false;
        Long yo = quien.idUsuario();
        boolean esMio = quien.rol() == com.polygraph.erp.shared.enums.Rol.PROGRAMADOR
                ? sp.getProgramadorResponsable() != null && yo.equals(sp.getProgramadorResponsable().getIdUsuario())
                : sp.getUsuarioAsignado() != null && yo.equals(sp.getUsuarioAsignado().getIdUsuario());
        return esMio;
    }

    private ServicioSemaforoFila fila(Servicio s, List<ServicioSubproceso> sps, EstadoSubproceso filtrado) {
        Map<EstadoSubproceso, Integer> porEstado = new EnumMap<>(EstadoSubproceso.class);
        for (ServicioSubproceso sp : sps) porEstado.merge(sp.getEstado(), 1, Integer::sum);
        List<ResumenEstado> resumen = new ArrayList<>();
        for (EstadoSubproceso e : EstadoSubproceso.enOrden()) {
            Integer n = porEstado.get(e);
            if (n != null) resumen.add(new ResumenEstado(e.name(), e.nombre(), e.color(), n));
        }
        Avance avance = SemaforoEstados.avance(sps.stream().map(ServicioSubproceso::getEstado).toList());
        List<SubprocesoEnFila> filasSp = sps.stream()
                .map(sp -> new SubprocesoEnFila(sp.getId(), sp.getTipoProgreso().getNombreProgreso(), sp.getEstado().name(),
                        sp.getEstado().nombre(), filtrado != null && sp.getEstado() == filtrado))
                .toList();
        var candidato = s.getCandidato();
        return new ServicioSemaforoFila(
                s.getIdServicio(),
                candidato != null ? ((candidato.getNombres() != null ? candidato.getNombres() : "") + " "
                        + (candidato.getApellidos() != null ? candidato.getApellidos() : "")).trim() : null,
                candidato != null ? candidato.getCedula() : null,
                s.getCargo(),
                s.getProceso() != null ? s.getProceso().getNombreProceso() : null,
                s.getCliente() != null ? nombreCliente(s.getCliente()) : null,
                s.getFechaSolicitud(),
                resumen,
                avance.publicados(),
                avance.vigentes(),
                SemaforoEstados.etiqueta(avance).texto(),
                filasSp,
                tiempoServicioService.de(s));
    }

    private static String nombreCliente(Cliente c) {
        if (TipoPersona.JURIDICA.equals(c.getTipoPersona()) && c.getRazonSocial() != null) return c.getRazonSocial();
        return ((c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "")).trim();
    }
}
