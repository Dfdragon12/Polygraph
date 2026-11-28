package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {
    List<Ciudad> findAllByOrderByNombreCiudadAsc();

    long countByPendienteConfirmacionTrue();

    // Ciudad.departamento es texto denormalizado (no FK a Departamento) — se usa para bloquear el
    // borrado de un departamento en uso y para saber si conviene ofrecer "eliminar" en su fila.
    boolean existsByDepartamentoIgnoreCase(String departamento);

    // Al renombrar un departamento (DepartamentoService.actualizar), se propaga el nombre nuevo a
    // todas las ciudades que ya tenían guardado el nombre viejo — si no, quedarían con un
    // departamento "fantasma" que ya no existe en el catálogo.
    @Modifying
    @Query("update Ciudad c set c.departamento = :nuevo, c.codigoDaneDepto = :codigoDane where lower(c.departamento) = lower(:viejo)")
    void renombrarDepartamento(@Param("viejo") String viejo, @Param("nuevo") String nuevo, @Param("codigoDane") String codigoDane);

    // Ids de ciudades referenciadas por FK desde clientes/candidatos/empleados/recargos — se usa
    // para no ofrecer "Eliminar" en el catálogo cuando la ciudad ya está en uso. (visitas_domiciliarias
    // salió de aquí: era del modelo viejo de V1, siempre vacía, y V195 la elimina.)
    @Query(value = """
            SELECT DISTINCT id_ciudad FROM (
              SELECT id_ciudad FROM clientes WHERE id_ciudad IS NOT NULL
              UNION
              SELECT id_ciudad_nacimiento AS id_ciudad FROM candidatos WHERE id_ciudad_nacimiento IS NOT NULL
              UNION
              SELECT id_ciudad_residencia AS id_ciudad FROM candidatos WHERE id_ciudad_residencia IS NOT NULL
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
              SELECT 1 FROM empleados WHERE id_ciudad_residencia = :id
              UNION ALL
              SELECT 1 FROM recargos_pendientes WHERE id_ciudad = :id
            )
            """, nativeQuery = true)
    boolean estaEnUso(@Param("id") Integer id);
}
