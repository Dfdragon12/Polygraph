package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Solo se inserta y se lee: nada actualiza ni borra el registro (la base de datos también lo impide). */
public interface RegistroCambioSubprocesoRepository extends JpaRepository<RegistroCambioSubproceso, Long> {

    List<RegistroCambioSubproceso> findByIdServicioOrderByFechaHoraAscIdAsc(Integer idServicio);

    List<RegistroCambioSubproceso> findByIdServicioSubprocesoOrderByFechaHoraAscIdAsc(Long idServicioSubproceso);

    /** Los cambios de esos subprocesos anteriores al corte (hora de Colombia), en orden: es lo que usa el reporte de productividad (SE-11). */
    List<RegistroCambioSubproceso> findByIdServicioSubprocesoInAndFechaBogotaBeforeOrderByFechaBogotaAscIdAsc(
            java.util.Collection<Long> ids, java.time.LocalDateTime corte);
}
