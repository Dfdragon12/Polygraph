package com.polygraph.erp.modules.evaluados.repository;

import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoEvaluadoRepository extends JpaRepository<DocumentoEvaluado, Long> {
    List<DocumentoEvaluado> findByHojaVidaEvaluado_IdOrderByFechaCargaDesc(Long idHojaVida);

    Optional<DocumentoEvaluado> findByHojaVidaEvaluado_IdAndTipoDocumento(Long idHojaVida, String tipoDocumento);

    /** Documentos por candidato y estado de revisión (Mis Asignaciones: documentos por revisar / devueltos). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT h.candidato.idCandidato, d.estadoRevision, COUNT(d)
            FROM DocumentoEvaluado d JOIN d.hojaVidaEvaluado h
            WHERE h.candidato.idCandidato IN :ids
            GROUP BY h.candidato.idCandidato, d.estadoRevision
            """)
    List<Object[]> contarPorCandidatoYEstado(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
