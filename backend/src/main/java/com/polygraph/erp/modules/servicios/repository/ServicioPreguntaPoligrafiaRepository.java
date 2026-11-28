package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.ServicioPreguntaPoligrafia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicioPreguntaPoligrafiaRepository extends JpaRepository<ServicioPreguntaPoligrafia, Long> {

    List<ServicioPreguntaPoligrafia> findByServicio_IdServicioOrderByOrdenAscIdAsc(Integer idServicio);
}
