package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClasificacionProcesoRepository extends JpaRepository<ClasificacionProceso, Integer> {
    List<ClasificacionProceso> findAllByOrderByCodigoAsc();
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdClasificacionNot(String codigo, Integer id);
    boolean existsByNombre(String nombre);
    boolean existsByNombreAndIdClasificacionNot(String nombre, Integer id);
}
