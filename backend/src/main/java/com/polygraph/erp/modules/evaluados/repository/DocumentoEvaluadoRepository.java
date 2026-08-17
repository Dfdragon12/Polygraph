package com.polygraph.erp.modules.evaluados.repository;

import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoEvaluadoRepository extends JpaRepository<DocumentoEvaluado, Long> {
    List<DocumentoEvaluado> findByHojaVidaEvaluado_IdOrderByFechaCargaDesc(Long idHojaVida);

    Optional<DocumentoEvaluado> findByHojaVidaEvaluado_IdAndTipoDocumento(Long idHojaVida, String tipoDocumento);
}
