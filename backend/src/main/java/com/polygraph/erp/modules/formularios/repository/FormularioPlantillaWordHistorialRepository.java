package com.polygraph.erp.modules.formularios.repository;

import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaWordHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormularioPlantillaWordHistorialRepository extends JpaRepository<FormularioPlantillaWordHistorial, Long> {
    List<FormularioPlantillaWordHistorial> findByTipoProgreso_IdTipoProgresoOrderByFechaCargaDesc(Integer idTipoProgreso);
}
