package com.polygraph.erp.modules.tiemporeal;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Después del commit (y en otro hilo, para no demorar la respuesta), convierte lo que cambió en
 *  los servicios afectados y en a quién le incumbe cada uno — gestor del cliente, usuarios del
 *  cliente, ejecutores asignados y programador responsable de sus subprocesos — y avisa por
 *  CanalCambios. Si no hay nadie conectado, no consulta nada. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolutorCambios {

    private final CanalCambios canal;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    void registrarse() { RegistroCambios.usar(this); }

    @Async
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void resolverYAvisar(RegistroCambios.Pendientes p) {
        if (canal.conexionesAbiertas() == 0) return;
        try {
            canal.publicar(cambios(idsServicio(p)));
            if (!p.temasAdmin.isEmpty()) canal.publicarAdmin(p.temasAdmin);
        } catch (Exception e) {
            log.warn("No se pudo avisar en tiempo real: {}", e.getMessage());
        }
    }

    /** Cambios hechos fuera de una transacción: se resuelven un instante después. */
    @Async
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void resolverDespues(RegistroCambios.Pendientes p) {
        if (!p.vacio()) resolverYAvisar(p);
    }

    private Set<Integer> idsServicio(RegistroCambios.Pendientes p) {
        Set<Integer> ids = new HashSet<>(p.servicios);
        if (!p.subprocesos.isEmpty()) {
            ids.addAll(em.createQuery("SELECT sp.servicio.idServicio FROM ServicioSubproceso sp WHERE sp.id IN :ids", Integer.class)
                    .setParameter("ids", p.subprocesos).getResultList());
        }
        if (!p.novedades.isEmpty()) {
            ids.addAll(em.createQuery("SELECT n.servicioSubproceso.servicio.idServicio FROM NovedadSubproceso n WHERE n.id IN :ids", Integer.class)
                    .setParameter("ids", p.novedades).getResultList());
        }
        Set<Long> candidatos = new HashSet<>(p.candidatos);
        if (!p.hojasVida.isEmpty()) {
            candidatos.addAll(em.createQuery("SELECT h.candidato.idCandidato FROM HojaVidaEvaluado h WHERE h.id IN :ids", Long.class)
                    .setParameter("ids", p.hojasVida).getResultList());
        }
        if (!candidatos.isEmpty()) {
            ids.addAll(em.createQuery("SELECT s.idServicio FROM Servicio s WHERE s.candidato.idCandidato IN :ids", Integer.class)
                    .setParameter("ids", candidatos).getResultList());
        }
        ids.remove(null);
        return ids;
    }

    private Collection<CanalCambios.Cambio> cambios(Set<Integer> idsServicio) {
        if (idsServicio.isEmpty()) return List.of();

        Map<Integer, Set<Long>> usuarios = new HashMap<>();
        for (Object[] f : em.createQuery("SELECT sp.servicio.idServicio, ua.idUsuario, pr.idUsuario FROM ServicioSubproceso sp "
                        + "LEFT JOIN sp.usuarioAsignado ua LEFT JOIN sp.programadorResponsable pr WHERE sp.servicio.idServicio IN :ids", Object[].class)
                .setParameter("ids", idsServicio).getResultList()) {
            Set<Long> u = usuarios.computeIfAbsent((Integer) f[0], k -> new HashSet<>());
            if (f[1] != null) u.add((Long) f[1]);
            if (f[2] != null) u.add((Long) f[2]);
        }

        List<CanalCambios.Cambio> cambios = new ArrayList<>();
        for (Object[] f : em.createQuery("SELECT s.idServicio, c.idGestor, c.idCliente FROM Servicio s LEFT JOIN s.cliente c "
                        + "WHERE s.idServicio IN :ids", Object[].class)
                .setParameter("ids", idsServicio).getResultList()) {
            Integer id = (Integer) f[0];
            cambios.add(new CanalCambios.Cambio(id, (Long) f[1], (Integer) f[2], usuarios.getOrDefault(id, Set.of())));
        }
        return cambios;
    }
}
