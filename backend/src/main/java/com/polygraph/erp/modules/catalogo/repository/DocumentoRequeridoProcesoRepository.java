package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.DocumentoRequeridoProceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRequeridoProcesoRepository extends JpaRepository<DocumentoRequeridoProceso, Integer> {
    List<DocumentoRequeridoProceso> findByProceso_IdProcesoOrderByOrdenAscNombreAsc(Integer idProceso);

    boolean existsByProceso_IdProcesoAndClaveIgnoreCase(Integer idProceso, String clave);

    boolean existsByProceso_IdProcesoAndClaveIgnoreCaseAndIdNot(Integer idProceso, String clave, Integer id);
}
