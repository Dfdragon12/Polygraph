package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NovedadSubprocesoRepository extends JpaRepository<NovedadSubproceso, Long> {

    @EntityGraph(attributePaths = {"registradoPor", "resueltaPor", "tipo", "tipo.categoria"})
    List<NovedadSubproceso> findByServicioSubproceso_IdOrderByFechaRegistroAsc(Long idServicioSubproceso);

    /** Cuántas novedades usan un tipo — si es > 0 el tipo no se puede borrar, solo desactivar. */
    long countByTipo_IdTipoNovedad(Integer idTipoNovedad);
}
