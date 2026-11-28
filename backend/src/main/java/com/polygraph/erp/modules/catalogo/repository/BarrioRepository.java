package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Barrio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BarrioRepository extends JpaRepository<Barrio, Integer> {
    List<Barrio> findByCiudad_IdCiudadOrderByNombreAsc(Integer idCiudad);

    List<Barrio> findAllByOrderByNombreAsc();

    Optional<Barrio> findByCiudad_IdCiudadAndNombreIgnoreCase(Integer idCiudad, String nombre);

    long countByPendienteConfirmacionTrue();

    // Igual criterio que Ciudad.estaEnUso — no se puede borrar un barrio que ya está referenciado.
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM clientes WHERE id_barrio = :id
              UNION ALL
              SELECT 1 FROM candidatos WHERE id_barrio_residencia = :id
            )
            """, nativeQuery = true)
    boolean estaEnUso(@Param("id") Integer id);
}
