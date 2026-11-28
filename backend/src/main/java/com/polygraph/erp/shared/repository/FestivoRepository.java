package com.polygraph.erp.shared.repository;

import com.polygraph.erp.shared.entity.Festivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FestivoRepository extends JpaRepository<Festivo, Integer> {
    Optional<Festivo> findByFecha(LocalDate fecha);

    List<Festivo> findByFechaBetweenOrderByFecha(LocalDate desde, LocalDate hasta);

    boolean existsByOrigenAndFechaBetween(String origen, LocalDate desde, LocalDate hasta);
}
