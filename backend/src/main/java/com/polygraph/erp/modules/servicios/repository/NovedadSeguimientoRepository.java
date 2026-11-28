package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.NovedadSeguimiento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NovedadSeguimientoRepository extends JpaRepository<NovedadSeguimiento, Long> {

    @EntityGraph(attributePaths = "autor")
    List<NovedadSeguimiento> findByNovedad_IdInOrderByFechaAscIdAsc(Collection<Long> idsNovedad);
}
