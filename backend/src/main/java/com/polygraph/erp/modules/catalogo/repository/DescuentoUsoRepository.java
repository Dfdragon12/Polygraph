package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.DescuentoUso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DescuentoUsoRepository extends JpaRepository<DescuentoUso, Integer> {
    boolean existsByDescuento_IdDescuentoAndCliente_IdCliente(Long idDescuento, Integer idCliente);

    long countByDescuento_IdDescuento(Long idDescuento);

    List<DescuentoUso> findByDescuento_IdDescuentoOrderByFechaUsoDesc(Long idDescuento);
}
