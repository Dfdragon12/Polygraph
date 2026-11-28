package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.PreguntaPoligrafiaRequest;
import com.polygraph.erp.modules.catalogo.dto.PreguntaPoligrafiaResponse;
import com.polygraph.erp.modules.catalogo.entity.PreguntaPoligrafia;
import com.polygraph.erp.modules.catalogo.repository.PreguntaPoligrafiaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Temas que el cliente puede pedir evaluar en una poligrafía (Admin → Novedades → Temas de
 *  poligrafía). El cliente solo recibe los activos, en el orden que definió el admin. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PreguntaPoligrafiaService {

    private final PreguntaPoligrafiaRepository repository;

    @Transactional(readOnly = true)
    public List<PreguntaPoligrafiaResponse> listar() {
        return repository.findAllByOrderByOrdenAscIdAsc().stream().map(PreguntaPoligrafiaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<PreguntaPoligrafiaResponse> listarActivas() {
        return repository.findByActivoTrueOrderByOrdenAscIdAsc().stream().map(PreguntaPoligrafiaResponse::de).toList();
    }

    public PreguntaPoligrafiaResponse crear(PreguntaPoligrafiaRequest req) {
        String nombre = req.nombre().trim();
        List<PreguntaPoligrafia> todas = repository.findAllByOrderByOrdenAscIdAsc();
        validarNoRepetido(todas, nombre, null);
        int ultimo = todas.stream().mapToInt(PreguntaPoligrafia::getOrden).max().orElse(0);
        PreguntaPoligrafia tema = repository.save(PreguntaPoligrafia.builder()
                .nombre(nombre)
                .requiereDetalle(Boolean.TRUE.equals(req.requiereDetalle()))
                .grupo(limpiarGrupo(req.grupo()))
                .orden(req.orden() != null ? req.orden() : ultimo + 10)
                .activo(req.activo() == null || req.activo())
                .build());
        log.info("Temas de poligrafía: se creó '{}'", nombre);
        return PreguntaPoligrafiaResponse.de(tema);
    }

    public PreguntaPoligrafiaResponse actualizar(Integer id, PreguntaPoligrafiaRequest req) {
        PreguntaPoligrafia tema = obtener(id);
        String nombre = req.nombre().trim();
        validarNoRepetido(repository.findAllByOrderByOrdenAscIdAsc(), nombre, id);
        tema.setNombre(nombre);
        if (req.requiereDetalle() != null) tema.setRequiereDetalle(req.requiereDetalle());
        tema.setGrupo(limpiarGrupo(req.grupo()));
        if (req.orden() != null) tema.setOrden(req.orden());
        if (req.activo() != null) tema.setActivo(req.activo());
        return PreguntaPoligrafiaResponse.de(repository.save(tema));
    }

    /** Las solicitudes ya hechas conservan el texto del tema: borrarlo no las cambia. */
    public void eliminar(Integer id) {
        PreguntaPoligrafia tema = obtener(id);
        repository.delete(tema);
        log.info("Temas de poligrafía: se eliminó '{}'", tema.getNombre());
    }

    private static String limpiarGrupo(String grupo) {
        return grupo == null || grupo.isBlank() ? null : grupo.trim();
    }

    private PreguntaPoligrafia obtener(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ApiException("El tema no existe", HttpStatus.NOT_FOUND));
    }

    private static void validarNoRepetido(List<PreguntaPoligrafia> todas, String nombre, Integer idPropio) {
        boolean repetido = todas.stream().anyMatch(t -> !t.getId().equals(idPropio) && t.getNombre().equalsIgnoreCase(nombre));
        if (repetido) throw new ApiException("Ya existe el tema \"" + nombre + "\"", HttpStatus.CONFLICT);
    }
}
