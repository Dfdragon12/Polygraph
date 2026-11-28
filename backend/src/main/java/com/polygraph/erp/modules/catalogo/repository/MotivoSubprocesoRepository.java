package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.MotivoSubproceso;
import com.polygraph.erp.shared.enums.TipoMotivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MotivoSubprocesoRepository extends JpaRepository<MotivoSubproceso, Integer> {

    List<MotivoSubproceso> findAllByOrderByTipoAscOrdenAscIdAsc();

    List<MotivoSubproceso> findByTipoAndActivoTrueOrderByOrdenAscIdAsc(TipoMotivo tipo);
}
