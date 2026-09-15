package com.polygraph.erp.modules.clientes.repository;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {
    boolean existsByNit(String nit);
    boolean existsByEmailPrincipal(String email);
    Optional<Cliente> findByEmailPrincipal(String email);
    List<Cliente> findByIdGestor(Long idGestor);

    /** Para el buscador global (solo ADMIN_POLYGRAPH ve clientes, ver BusquedaGlobalService) —
     *  busca por razón social, nombre comercial, nombre/apellido (persona natural), email o NIT.
     *  `patron` ya viene armado como "%texto%" en minúsculas desde el servicio — ver la nota en
     *  ServicioRepository.buscar sobre por qué no usar concat(...) directo en el JPQL. */
    @Query("""
            select c from Cliente c
            where lower(coalesce(c.razonSocial, '')) like :patron
               or lower(coalesce(c.nombreComercial, '')) like :patron
               or lower(coalesce(c.nombre, '')) like :patron
               or lower(coalesce(c.apellido, '')) like :patron
               or lower(c.emailPrincipal) like :patron
               or lower(coalesce(c.nit, '')) like :patron
            """)
    List<Cliente> buscar(@Param("patron") String patron, Pageable pageable);
}
