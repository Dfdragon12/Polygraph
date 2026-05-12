package com.polygraph.erp.modules.evaluados.repository;

import com.polygraph.erp.modules.evaluados.entity.Candidato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

    Optional<Candidato> findByCedula(String cedula);
}
