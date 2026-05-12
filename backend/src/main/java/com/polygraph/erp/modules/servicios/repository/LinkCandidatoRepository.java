package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinkCandidatoRepository extends JpaRepository<LinkCandidato, Long> {

    Optional<LinkCandidato> findByToken(String token);
}
