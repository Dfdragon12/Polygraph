package com.polygraph.erp.modules.pagos.repository;

import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenCompraItemRepository extends JpaRepository<OrdenCompraItem, Long> {
    List<OrdenCompraItem> findByOrdenCompra_IdOrdenCompra(Long idOrdenCompra);

    /** Última compra aprobada de un servicio (proceso) por un cliente prepago — su valor unitario
     *  es la referencia de lo que le costó cada solicitud de ese servicio. */
    java.util.Optional<OrdenCompraItem> findFirstByOrdenCompra_Cliente_IdClienteAndProceso_IdProcesoAndOrdenCompra_EstadoOrderByOrdenCompra_FechaCreacionDesc(
            Integer idCliente, Integer idProceso, com.polygraph.erp.shared.enums.EstadoOrdenCompra estado);

    /** Unidades compradas en órdenes con ese estado, por cliente y servicio: filas [idCliente, idProceso, unidades]. */
    @org.springframework.data.jpa.repository.Query("select i.ordenCompra.cliente.idCliente, i.proceso.idProceso, sum(i.cantidad) from OrdenCompraItem i "
            + "where i.ordenCompra.estado = :estado group by i.ordenCompra.cliente.idCliente, i.proceso.idProceso")
    java.util.List<Object[]> sumarCantidadPorClienteYProceso(
            @org.springframework.data.repository.query.Param("estado") com.polygraph.erp.shared.enums.EstadoOrdenCompra estado);
}
