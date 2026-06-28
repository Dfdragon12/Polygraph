package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoProgresoRepository extends JpaRepository<TipoProgreso, Integer> {
    boolean existsByNombreProgreso(String nombreProgreso);
    boolean existsByNombreProgresoAndIdTipoProgresoNot(String nombreProgreso, Integer id);
    List<TipoProgreso> findAllByOrderByOrdenAscNombreProgresoAsc();
}
