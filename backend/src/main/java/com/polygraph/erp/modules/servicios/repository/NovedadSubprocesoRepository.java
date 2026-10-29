package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NovedadSubprocesoRepository extends JpaRepository<NovedadSubproceso, Long> {

    // ── Tableros: novedades de servicios de los clientes del GESTOR, de los subprocesos que
    //    coordina el PROGRAMADOR, o las que REGISTRÓ un ejecutor (analista/poligrafista/visitador).
    //    Se pasa un solo id; los otros van en -1 (ningún usuario lo tiene). ──
    String DESDE = "FROM NovedadSubproceso n LEFT JOIN n.servicioSubproceso.programadorResponsable pr LEFT JOIN n.registradoPor rp ";
    String ALCANCE = "(n.servicioSubproceso.servicio.cliente.idGestor = :idGestor OR pr.idUsuario = :idProgramador OR rp.idUsuario = :idRegistro)";

    /** [nombreCategoria, colorCategoria, cantidad] de las novedades ABIERTAS. */
    @org.springframework.data.jpa.repository.Query("SELECT c.nombre, c.color, COUNT(n) " + DESDE
            + "JOIN n.tipo t LEFT JOIN t.categoria c WHERE " + ALCANCE
            + " AND n.estado = com.polygraph.erp.shared.enums.EstadoNovedad.ABIERTA GROUP BY c.nombre, c.color ORDER BY COUNT(n) DESC")
    List<Object[]> contarAbiertasPorCategoria(@org.springframework.data.repository.query.Param("idGestor") Long idGestor,
            @org.springframework.data.repository.query.Param("idProgramador") Long idProgramador,
            @org.springframework.data.repository.query.Param("idRegistro") Long idRegistro);

    /** [nombreTipo, cantidad] de las novedades ABIERTAS, las más frecuentes primero. */
    @org.springframework.data.jpa.repository.Query("SELECT t.nombre, COUNT(n) " + DESDE + "JOIN n.tipo t WHERE " + ALCANCE
            + " AND n.estado = com.polygraph.erp.shared.enums.EstadoNovedad.ABIERTA GROUP BY t.nombre ORDER BY COUNT(n) DESC")
    List<Object[]> contarAbiertasPorTipo(@org.springframework.data.repository.query.Param("idGestor") Long idGestor,
            @org.springframework.data.repository.query.Param("idProgramador") Long idProgramador,
            @org.springframework.data.repository.query.Param("idRegistro") Long idRegistro,
                                          org.springframework.data.domain.Pageable pageable);

    /** Fechas de registro desde una fecha (para agrupar por semana en Java). */
    @org.springframework.data.jpa.repository.Query("SELECT n.fechaRegistro " + DESDE + "WHERE " + ALCANCE
            + " AND n.fechaRegistro >= :desde")
    List<java.time.LocalDateTime> fechasRegistro(@org.springframework.data.repository.query.Param("idGestor") Long idGestor,
            @org.springframework.data.repository.query.Param("idProgramador") Long idProgramador,
            @org.springframework.data.repository.query.Param("idRegistro") Long idRegistro,
                                                 @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);

    /** [fechaRegistro, fechaResolucion] de las resueltas desde una fecha. */
    @org.springframework.data.jpa.repository.Query("SELECT n.fechaRegistro, n.fechaResolucion " + DESDE + "WHERE " + ALCANCE
            + " AND n.estado = com.polygraph.erp.shared.enums.EstadoNovedad.RESUELTA AND n.fechaResolucion >= :desde")
    List<Object[]> resueltasDesde(@org.springframework.data.repository.query.Param("idGestor") Long idGestor,
            @org.springframework.data.repository.query.Param("idProgramador") Long idProgramador,
            @org.springframework.data.repository.query.Param("idRegistro") Long idRegistro,
                                  @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);

    /** Las ABIERTAS más antiguas (las que más llevan esperando respuesta). */
    @EntityGraph(attributePaths = {"tipo", "tipo.categoria", "servicioSubproceso", "servicioSubproceso.servicio",
            "servicioSubproceso.servicio.candidato", "servicioSubproceso.tipoProgreso"})
    @org.springframework.data.jpa.repository.Query("SELECT n " + DESDE + "WHERE " + ALCANCE
            + " AND n.estado = com.polygraph.erp.shared.enums.EstadoNovedad.ABIERTA ORDER BY n.fechaRegistro ASC")
    List<NovedadSubproceso> abiertasMasAntiguas(@org.springframework.data.repository.query.Param("idGestor") Long idGestor,
            @org.springframework.data.repository.query.Param("idProgramador") Long idProgramador,
            @org.springframework.data.repository.query.Param("idRegistro") Long idRegistro,
                                                org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"registradoPor", "resueltaPor", "tipo", "tipo.categoria"})
    List<NovedadSubproceso> findByServicioSubproceso_IdOrderByFechaRegistroAsc(Long idServicioSubproceso);

    /** Cuántas novedades usan un tipo — si es > 0 el tipo no se puede borrar, solo desactivar. */
    long countByTipo_IdTipoNovedad(Integer idTipoNovedad);

    /** Novedades en ese estado (ABIERTA) de esos subprocesos: la alerta «Novedades abiertas» (SE-09). */
    @org.springframework.data.jpa.repository.Query("select count(n) from NovedadSubproceso n where n.estado = :estado and n.servicioSubproceso.id in :ids")
    long contarPorSubprocesos(@org.springframework.data.repository.query.Param("estado") com.polygraph.erp.shared.enums.EstadoNovedad estado,
                              @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    /** Novedades que este usuario registró y siguen sin respuesta, por subproceso (cola "Validaciones"). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT n.servicioSubproceso.id, COUNT(n) FROM NovedadSubproceso n
            WHERE n.registradoPor.idUsuario = :idUsuario
              AND n.estado = com.polygraph.erp.shared.enums.EstadoNovedad.ABIERTA
              AND n.servicioSubproceso.id IN :idsSubproceso
            GROUP BY n.servicioSubproceso.id
            """)
    List<Object[]> contarAbiertasPorSubproceso(@org.springframework.data.repository.query.Param("idUsuario") Long idUsuario,
                                               @org.springframework.data.repository.query.Param("idsSubproceso") java.util.Collection<Long> idsSubproceso);
}
