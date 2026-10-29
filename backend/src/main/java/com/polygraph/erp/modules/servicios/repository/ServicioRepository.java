package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Integer>, JpaSpecificationExecutor<Servicio> {

    /** Solicitudes anteriores a las columnas de tiempo (ver TiempoHistoricoInicializador). */
    List<Servicio> findByFechaRecepcionEfectivaIsNull();
    /** Las publicadas cuyo cumplimiento hay que (re)calcular: la migración V227 lo deja vacío cuando cambió una pausa. */
    List<Servicio> findByEstadoAndCumplimientoIsNull(EstadoServicio estado);

    long countByEstado(EstadoServicio estado);
    long countByCliente_IdClienteAndEstado(Integer idCliente, EstadoServicio estado);

    long countByCliente_IdClienteAndEstadoIn(Integer idCliente, List<EstadoServicio> estados);

    long countByCliente_IdClienteAndEstadoInAndFechaSolicitudBetween(
            Integer idCliente, List<EstadoServicio> estados, LocalDate inicio, LocalDate fin);

    Page<Servicio> findByCliente_IdClienteOrderByFechaSolicitudDescHoraSolicitudDesc(
            Integer idCliente, Pageable pageable);

    Page<Servicio> findByCliente_IdClienteAndEstadoOrderByFechaSolicitudDescHoraSolicitudDesc(
            Integer idCliente, EstadoServicio estado, Pageable pageable);

    /** Conteos por estado de tiempo: leen lo que guardó SemaforoTiempoService, no calculan nada. */
    long countByEstadoTiempo(com.polygraph.erp.shared.enums.EstadoTiempo estadoTiempo);

    long countByCliente_IdClienteAndEstadoTiempo(Integer idCliente, com.polygraph.erp.shared.enums.EstadoTiempo estadoTiempo);

    long countByCliente_IdGestorAndEstadoTiempo(Long idGestor, com.polygraph.erp.shared.enums.EstadoTiempo estadoTiempo);

    /** Las solicitudes que aún pueden cambiar de estado de tiempo (todas menos las publicadas y canceladas). */
    @Query("SELECT s.idServicio FROM Servicio s WHERE s.estado NOT IN :cerrados ORDER BY s.idServicio")
    List<Integer> idsAbiertos(@Param("cerrados") List<EstadoServicio> cerrados);

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

    // ── Gráficas del tablero del gestor (conteos agrupados, sin traer los servicios) ──

    /** [fechaSolicitud, cantidad] de los servicios de sus clientes desde una fecha. */
    @Query("SELECT s.fechaSolicitud, COUNT(s) FROM Servicio s WHERE s.cliente.idGestor = :idGestor "
            + "AND s.fechaSolicitud >= :desde GROUP BY s.fechaSolicitud")
    List<Object[]> contarPorFechaSolicitud(@Param("idGestor") Long idGestor, @Param("desde") LocalDate desde);

    /** [fechaEntregaEstimada, cantidad] de los servicios en los estados dados (null = sin fecha). */
    @Query("SELECT s.fechaCompromisoAjustada, s.estadoTiempo, COUNT(s) FROM Servicio s WHERE s.cliente.idGestor = :idGestor "
            + "AND s.estado IN :estados GROUP BY s.fechaCompromisoAjustada, s.estadoTiempo")
    List<Object[]> contarPorFechaEntrega(@Param("idGestor") Long idGestor, @Param("estados") List<EstadoServicio> estados);

    /** [idCliente, razonSocial, nombreComercial, nombre, apellido, cantidad] — más cargados primero. */
    @Query("SELECT c.idCliente, c.razonSocial, c.nombreComercial, c.nombre, c.apellido, COUNT(s) FROM Servicio s "
            + "JOIN s.cliente c WHERE c.idGestor = :idGestor AND s.estado IN :estados "
            + "GROUP BY c.idCliente, c.razonSocial, c.nombreComercial, c.nombre, c.apellido ORDER BY COUNT(s) DESC")
    List<Object[]> contarPorCliente(@Param("idGestor") Long idGestor, @Param("estados") List<EstadoServicio> estados, Pageable pageable);

    /** [nombreProceso, cantidad] — más usados primero. */
    @Query("SELECT p.nombreProceso, COUNT(s) FROM Servicio s JOIN s.proceso p "
            + "WHERE s.cliente.idGestor = :idGestor AND s.estado IN :estados "
            + "GROUP BY p.nombreProceso ORDER BY COUNT(s) DESC")
    List<Object[]> contarPorProceso(@Param("idGestor") Long idGestor, @Param("estados") List<EstadoServicio> estados, Pageable pageable);
}
