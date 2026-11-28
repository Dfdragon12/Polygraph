package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoCreditoPospagoRepository extends JpaRepository<MovimientoCreditoPospago, Long> {
    List<MovimientoCreditoPospago> findByCliente_IdClienteAndFacturaIsNullAndAnuladoEnIsNullOrderByFechaGeneradoAsc(Integer idCliente);

    List<MovimientoCreditoPospago> findByFactura_IdFacturaOrderByFechaGeneradoAsc(Long idFactura);

    /** El cargo de UNA solicitud pospago (lo que vale y en qué factura va). */
    java.util.Optional<MovimientoCreditoPospago> findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(Integer idServicio);

    // Usado por ClienteAdminService.cambiarTipoCliente para bloquear el paso a prepago mientras
    // el cliente tenga consumo pospago sin facturar.
    boolean existsByCliente_IdClienteAndFacturaIsNullAndAnuladoEnIsNull(Integer idCliente);

    // Para el panel "pendientes de facturar" de TODOS los clientes pospago (Facturacion.jsx) —
    // se agrupa por cliente en ClientePospagoService.listarPendientesGlobal.
    List<MovimientoCreditoPospago> findByFacturaIsNullAndAnuladoEnIsNullOrderByFechaGeneradoAsc();
}
