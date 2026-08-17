package com.polygraph.erp.modules.evaluados.repository;

import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HojaVidaEvaluadoRepository extends JpaRepository<HojaVidaEvaluado, Long> {

    Optional<HojaVidaEvaluado> findByCandidato_IdCandidato(Long idCandidato);
}
