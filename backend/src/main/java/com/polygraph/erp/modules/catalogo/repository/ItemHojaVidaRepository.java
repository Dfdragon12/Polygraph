package com.polygraph.erp.modules.catalogo.repository;

import com.polygraph.erp.modules.catalogo.entity.ItemHojaVida;
import com.polygraph.erp.modules.catalogo.entity.ListaHojaVida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemHojaVidaRepository extends JpaRepository<ItemHojaVida, Integer> {

    List<ItemHojaVida> findAllByOrderByListaAscOrdenAscIdAsc();

    List<ItemHojaVida> findByActivoTrueOrderByOrdenAscIdAsc();

    List<ItemHojaVida> findByListaOrderByOrdenAscIdAsc(ListaHojaVida lista);

    List<ItemHojaVida> findByListaAndActivoTrueOrderByOrdenAscIdAsc(ListaHojaVida lista);
}
