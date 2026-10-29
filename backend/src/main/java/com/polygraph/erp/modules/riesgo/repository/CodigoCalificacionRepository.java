package com.polygraph.erp.modules.riesgo.repository;

import com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodigoCalificacionRepository extends JpaRepository<CodigoCalificacion, Integer> {

    /** Catálogo vigente de un subservicio, en el orden del anexo (lo que usa el cálculo). */
    List<CodigoCalificacion> findByGrupoAndActivoTrueOrderByOrdenAsc(String grupo);

    java.util.Optional<CodigoCalificacion> findByCodigo(String codigo);
}
