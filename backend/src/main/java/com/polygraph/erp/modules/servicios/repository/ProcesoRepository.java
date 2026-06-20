package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcesoRepository extends JpaRepository<Proceso, Integer> {
    boolean existsByNombreProceso(String nombreProceso);
    boolean existsByNombreProcesoAndIdProcesoNot(String nombreProceso, Integer idProceso);
}
