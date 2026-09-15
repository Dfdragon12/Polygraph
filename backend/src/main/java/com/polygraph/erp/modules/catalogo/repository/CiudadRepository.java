package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {
    List<Ciudad> findAllByOrderByNombreCiudadAsc();

    long countByPendienteConfirmacionTrue();

    // Ids de ciudades referenciadas por FK desde clientes/candidatos/visitas/empleados/recargos —
    // se usa para no ofrecer "Eliminar" en el catálogo cuando la ciudad ya está en uso.
    @Query(value = """
            SELECT DISTINCT id_ciudad FROM (
              SELECT id_ciudad FROM clientes WHERE id_ciudad IS NOT NULL
              UNION
              SELECT id_ciudad_nacimiento AS id_ciudad FROM candidatos WHERE id_ciudad_nacimiento IS NOT NULL
              UNION
              SELECT id_ciudad_residencia AS id_ciudad FROM candidatos WHERE id_ciudad_residencia IS NOT NULL
              UNION
              SELECT id_ciudad FROM visitas_domiciliarias WHERE id_ciudad IS NOT NULL
              UNION
              SELECT id_ciudad_residencia AS id_ciudad FROM empleados WHERE id_ciudad_residencia IS NOT NULL
              UNION
              SELECT id_ciudad FROM recargos_pendientes WHERE id_ciudad IS NOT NULL
            ) t
            """, nativeQuery = true)
    List<Integer> idsEnUso();

    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM clientes WHERE id_ciudad = :id
              UNION ALL
              SELECT 1 FROM candidatos WHERE id_ciudad_nacimiento = :id OR id_ciudad_residencia = :id
              UNION ALL
              SELECT 1 FROM visitas_domiciliarias WHERE id_ciudad = :id
              UNION ALL
              SELECT 1 FROM empleados WHERE id_ciudad_residencia = :id
              UNION ALL
              SELECT 1 FROM recargos_pendientes WHERE id_ciudad = :id
            )
            """, nativeQuery = true)
    boolean estaEnUso(@Param("id") Integer id);
}
