package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.shared.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TipoProgresoRepository extends JpaRepository<TipoProgreso, Integer> {
    boolean existsByNombreProgreso(String nombreProgreso);
    boolean existsByNombreProgresoAndIdTipoProgresoNot(String nombreProgreso, Integer id);
    List<TipoProgreso> findAllByOrderByOrdenAscNombreProgresoAsc();
    List<TipoProgreso> findByRolResponsableInAndActivoTrueOrderByOrdenAscNombreProgresoAsc(Set<Rol> roles);
    Optional<TipoProgreso> findByNombreProgreso(String nombreProgreso);
}
