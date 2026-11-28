package com.polygraph.erp.modules.usuarios.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;

import java.util.List;
import java.util.Optional;

public interface UsuariosIntenosRepository extends JpaRepository<UsuariosInternos, Integer> {
    Optional<UsuariosInternos> findByEmail(String email);
    List<UsuariosInternos> findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(Integer idTipoProgreso);

    /** Para el buscador global (solo ADMIN_POLYGRAPH) — por nombre, apellido, email o documento.
     *  `patron` ya viene armado como "%texto%" en minúsculas — ver la nota en
     *  ServicioRepository.buscar. */
    @Query("""
            select e from UsuariosInternos e
            where lower(e.nombre) like :patron
               or lower(coalesce(e.apellido, '')) like :patron
               or lower(e.email) like :patron
               or lower(coalesce(e.documento, '')) like :patron
            """)
    List<UsuariosInternos> buscar(@Param("patron") String patron, Pageable pageable);
}