package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.ItemHojaVidaRequest;
import com.polygraph.erp.modules.catalogo.dto.ItemHojaVidaResponse;
import com.polygraph.erp.modules.catalogo.entity.ItemHojaVida;
import com.polygraph.erp.modules.catalogo.entity.ListaHojaVida;
import com.polygraph.erp.modules.catalogo.repository.ItemHojaVidaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Listas y preguntas de la hoja de vida del candidato (Admin → Novedades → Hoja de vida del
 *  candidato). El portal del candidato solo recibe lo activo, en el orden que definió el admin. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemHojaVidaService {

    private final ItemHojaVidaRepository repository;

    @Transactional(readOnly = true)
    public List<ItemHojaVidaResponse> listar() {
        return repository.findAllByOrderByListaAscOrdenAscIdAsc().stream().map(ItemHojaVidaResponse::de).toList();
    }

    /** Lo que ve el candidato: por cada lista, sus opciones activas en orden (vacía si no hay). */
    @Transactional(readOnly = true)
    public Map<ListaHojaVida, List<ItemHojaVidaResponse>> activosPorLista() {
        Map<ListaHojaVida, List<ItemHojaVidaResponse>> mapa = new LinkedHashMap<>();
        for (ListaHojaVida lista : ListaHojaVida.values()) mapa.put(lista, new ArrayList<>());
        repository.findByActivoTrueOrderByOrdenAscIdAsc()
                .forEach(i -> mapa.get(i.getLista()).add(ItemHojaVidaResponse.de(i)));
        return mapa;
    }

    public ItemHojaVidaResponse crear(ItemHojaVidaRequest req) {
        String nombre = req.nombre().trim();
        List<ItemHojaVida> deLaLista = repository.findByListaOrderByOrdenAscIdAsc(req.lista());
        validarNoRepetido(deLaLista, nombre, null);
        int ultimo = deLaLista.stream().mapToInt(ItemHojaVida::getOrden).max().orElse(0);
        ItemHojaVida item = repository.save(ItemHojaVida.builder()
                .lista(req.lista())
                .nombre(nombre)
                .descripcion(texto(req.descripcion()))
                .orden(req.orden() != null ? req.orden() : ultimo + 10)
                .activo(req.activo() == null || req.activo())
                .build());
        log.info("Hoja de vida: se creó '{}' en la lista {}", nombre, req.lista());
        return ItemHojaVidaResponse.de(item);
    }

    public ItemHojaVidaResponse actualizar(Integer id, ItemHojaVidaRequest req) {
        ItemHojaVida item = obtener(id);
        String nombre = req.nombre().trim();
        validarNoRepetido(repository.findByListaOrderByOrdenAscIdAsc(item.getLista()), nombre, id);
        item.setNombre(nombre);
        item.setDescripcion(texto(req.descripcion()));
        if (req.orden() != null) item.setOrden(req.orden());
        if (req.activo() != null) item.setActivo(req.activo());
        return ItemHojaVidaResponse.de(repository.save(item));
    }

    public void eliminar(Integer id) {
        ItemHojaVida item = obtener(id);
        repository.delete(item);
        log.info("Hoja de vida: se eliminó '{}' de la lista {}", item.getNombre(), item.getLista());
    }

    private ItemHojaVida obtener(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException("El registro no existe", HttpStatus.NOT_FOUND));
    }

    private void validarNoRepetido(List<ItemHojaVida> deLaLista, String nombre, Integer idPropio) {
        boolean repetido = deLaLista.stream()
                .anyMatch(i -> !i.getId().equals(idPropio) && i.getNombre().equalsIgnoreCase(nombre));
        if (repetido) throw new ApiException("Ya existe \"" + nombre + "\" en esa lista", HttpStatus.CONFLICT);
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
