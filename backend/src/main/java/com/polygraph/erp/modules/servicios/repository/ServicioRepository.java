package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Integer> {

    long countByEstado(EstadoServicio estado);
    long countByCliente_IdClienteAndEstado(Integer idCliente, EstadoServicio estado);

    long countByCliente_IdClienteAndEstadoIn(Integer idCliente, List<EstadoServicio> estados);

    long countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
            Integer idCliente, List<EstadoServicio> estados, LocalDate inicio, LocalDate fin);

    Page<Servicio> findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(
            Integer idCliente, Pageable pageable);

    Page<Servicio> findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
            Integer idCliente, EstadoServicio estado, Pageable pageable);

    Page<Servicio> findByEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
            EstadoServicio estado, Pageable pageable);

    Page<Servicio> findAllByOrderByFechaSolicitudDescHoraSolicitudDesc(Pageable pageable);

    List<Servicio> findTop8ByOrderByFechaSolicitudDescHoraSolicitudDesc();

    List<Servicio> findByEstadoInOrderByFechaEntregaEstimadaAsc(List<EstadoServicio> estados, Pageable pageable);

    // --- Alcance por gestor: solo servicios de clientes con id_gestor = gestor autenticado ---

    long countByCliente_IdGestorAndEstado(Long idGestor, EstadoServicio estado);

    Page<Servicio> findByCliente_IdGestorAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
            Long idGestor, EstadoServicio estado, Pageable pageable);

    Page<Servicio> findByCliente_IdGestorOrderByFechaSolicitudDescHoraSolicitudDesc(
            Long idGestor, Pageable pageable);

    List<Servicio> findTop8ByCliente_IdGestorOrderByFechaSolicitudDescHoraSolicitudDesc(Long idGestor);

    List<Servicio> findByCliente_IdGestorAndEstadoInOrderByFechaEntregaEstimadaAsc(
            Long idGestor, List<EstadoServicio> estados, Pageable pageable);

    List<Servicio> findByCandidato_CedulaAndIdServicioNot(String cedula, Integer idServicio);

    @Query("""
            select count(s) > 0 from Servicio s
            where s.candidato.cedula = :cedula
              and s.proceso.idProceso = :idProceso
              and s.fechaSolicitud >= :fechaLimite
            """)
    boolean existeDuplicado(@Param("cedula") String cedula,
                            @Param("idProceso") Integer idProceso,
                            @Param("fechaLimite") LocalDate fechaLimite);

    /** Para el buscador global — por tipo de proceso, nombre/apellido del candidato, o nombre del
     *  cliente. idGestor null = sin filtrar por gestor (uso de ADMIN_POLYGRAPH); con valor,
     *  mismo alcance que el resto de consultas "por gestor" de este repositorio. Left join en
     *  candidato/cliente para no excluir filas por un join implícito cuando alguno es null.
     *  OJO: `patron` ya viene armado como "%texto%" en minúsculas desde BusquedaGlobalService —
     *  NO usar concat(...) aquí: Hibernate 6 lo traduce con un "escape ''" que hace que el LIKE
     *  no encuentre nada nunca (confirmado con ServicioRepositoryBusquedaTest). */
    @Query("""
            select s from Servicio s
            left join s.candidato c
            left join s.cliente cl
            join s.proceso p
            where (:idGestor is null or cl.idGestor = :idGestor)
              and (
                lower(p.nombreProceso) like :patron
                or lower(coalesce(c.nombres, '')) like :patron
                or lower(coalesce(c.apellidos, '')) like :patron
                or lower(coalesce(cl.razonSocial, '')) like :patron
                or lower(coalesce(cl.nombre, '')) like :patron
              )
            order by s.fechaSolicitud desc
            """)
    List<Servicio> buscar(@Param("patron") String patron, @Param("idGestor") Long idGestor, Pageable pageable);
}
