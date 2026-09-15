package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.DescuentoCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DescuentoClienteRepository extends JpaRepository<DescuentoCliente, Integer> {
    List<DescuentoCliente> findByDescuento_IdDescuento(Long idDescuento);

    boolean existsByDescuento_IdDescuentoAndCliente_IdCliente(Long idDescuento, Integer idCliente);

    void deleteByDescuento_IdDescuento(Long idDescuento);
}
