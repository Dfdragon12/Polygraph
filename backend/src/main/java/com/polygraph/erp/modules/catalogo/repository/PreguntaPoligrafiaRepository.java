package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.PreguntaPoligrafia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaPoligrafiaRepository extends JpaRepository<PreguntaPoligrafia, Integer> {

    List<PreguntaPoligrafia> findAllByOrderByOrdenAscIdAsc();

    List<PreguntaPoligrafia> findByActivoTrueOrderByOrdenAscIdAsc();
}
