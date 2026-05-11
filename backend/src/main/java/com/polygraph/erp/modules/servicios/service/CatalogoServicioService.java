package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.servicios.dto.CatalogoServicioRequest;
import com.polygraph.erp.modules.servicios.dto.CatalogoServicioResponse;
import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.modules.servicios.repository.CatalogoServicioRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogoServicioService {

    private final CatalogoServicioRepository repositorio;

    public List<CatalogoServicioResponse> listarActivos() {
        return repositorio.findByActivoTrueOrderByCategoriaAscOrdenAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CatalogoServicioResponse obtenerPorId(Integer id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public CatalogoServicioResponse crear(CatalogoServicioRequest solicitud) {
        log.info("Creando servicio en catálogo: {}", solicitud.nombre());
        CatalogoServicio servicio = CatalogoServicio.builder()
                .nombre(solicitud.nombre())
                .descripcion(solicitud.descripcion())
                .categoria(solicitud.categoria())
                .precioBase(solicitud.precioBase())
                .orden(solicitud.orden() != null ? solicitud.orden() : 0)
                .activo(true)
                .build();
        return toResponse(repositorio.save(servicio));
    }

    @Transactional
    public CatalogoServicioResponse actualizar(Integer id, CatalogoServicioRequest solicitud) {
        log.info("Actualizando servicio catálogo id={}", id);
        CatalogoServicio servicio = buscarOFallar(id);
        servicio.setNombre(solicitud.nombre());
        servicio.setDescripcion(solicitud.descripcion());
        servicio.setCategoria(solicitud.categoria());
        servicio.setPrecioBase(solicitud.precioBase());
        if (solicitud.orden() != null) servicio.setOrden(solicitud.orden());
        return toResponse(repositorio.save(servicio));
    }

    @Transactional
    public void desactivar(Integer id) {
        log.info("Desactivando servicio catálogo id={}", id);
        CatalogoServicio servicio = buscarOFallar(id);
        servicio.setActivo(false);
        repositorio.save(servicio);
    }

    private CatalogoServicio buscarOFallar(Integer id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
    }

    private CatalogoServicioResponse toResponse(CatalogoServicio s) {
        return new CatalogoServicioResponse(
                s.getIdCatalogo(), s.getNombre(), s.getDescripcion(),
                s.getCategoria(), s.getPrecioBase(), s.getActivo(), s.getOrden()
        );
    }
}
