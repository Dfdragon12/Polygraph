package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.DepartamentoRequest;
import com.polygraph.erp.modules.catalogo.dto.DepartamentoResponse;
import com.polygraph.erp.modules.catalogo.entity.Departamento;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.catalogo.repository.DepartamentoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catálogo de departamentos — antes era una lista fija hardcodeada en código
 * (DepartamentosColombiaUtil), ahora vive en la BD y es gestionable por un admin (agregar/editar/
 * eliminar), igual que ciudades y barrios. Ciudad.departamento sigue siendo texto denormalizado
 * (no FK): al validar/crear una ciudad se usa {@link #buscar} para resolver el nombre y código DANE
 * "oficiales" a partir de lo que se haya escrito, igual que antes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartamentoService {

    private static final Locale ES_CO = new Locale("es", "CO");

    private final DepartamentoRepository repository;
    private final CiudadRepository ciudadRepository;

    public List<DepartamentoResponse> listar() {
        return repository.findAllByOrderByNombreAsc().stream()
                .map(d -> DepartamentoResponse.from(d, ciudadRepository.existsByDepartamentoIgnoreCase(d.getNombre())))
                .toList();
    }

    /** Búsqueda insensible a tildes/mayúsculas/puntuación — usada por CiudadService para resolver
     *  el departamento "oficial" a partir de lo que escribió quien crea/confirma una ciudad. */
    public Optional<Departamento> buscar(String nombre) {
        if (nombre == null || nombre.isBlank()) return Optional.empty();
        String clave = clave(nombre);
        return repository.findAll().stream()
                .filter(d -> clave(d.getNombre()).equals(clave))
                .findFirst();
    }

    @Transactional
    public DepartamentoResponse crear(DepartamentoRequest req) {
        String nombre = normalizar(req.nombre());
        String codigoDane = req.codigoDane().trim();

        if (buscar(nombre).isPresent()) {
            throw new ApiException("Ya existe un departamento con ese nombre.", HttpStatus.CONFLICT);
        }
        if (repository.existsByCodigoDaneIgnoreCase(codigoDane)) {
            throw new ApiException("Ya existe un departamento con ese código DANE.", HttpStatus.CONFLICT);
        }

        Departamento departamento = Departamento.builder().nombre(nombre).codigoDane(codigoDane).build();
        repository.save(departamento);
        log.info("Departamento agregado al catálogo: {} ({})", nombre, codigoDane);
        return DepartamentoResponse.from(departamento, false);
    }

    @Transactional
    public DepartamentoResponse actualizar(Integer id, DepartamentoRequest req) {
        Departamento departamento = repository.findById(id)
                .orElseThrow(() -> new ApiException("Departamento no encontrado", HttpStatus.NOT_FOUND));

        String nombreNuevo = normalizar(req.nombre());
        String codigoNuevo = req.codigoDane().trim();

        buscar(nombreNuevo)
                .filter(otro -> !otro.getIdDepartamento().equals(id))
                .ifPresent(otro -> { throw new ApiException("Ya existe otro departamento con ese nombre.", HttpStatus.CONFLICT); });
        if (!codigoNuevo.equalsIgnoreCase(departamento.getCodigoDane()) && repository.existsByCodigoDaneIgnoreCase(codigoNuevo)) {
            throw new ApiException("Ya existe otro departamento con ese código DANE.", HttpStatus.CONFLICT);
        }

        String nombreAnterior = departamento.getNombre();
        departamento.setNombre(nombreNuevo);
        departamento.setCodigoDane(codigoNuevo);

        // Ciudad.departamento es texto copiado, no FK — si el nombre cambió, se propaga a todas las
        // ciudades que ya lo tenían guardado para que no queden con un nombre que ya no existe.
        if (!nombreAnterior.equalsIgnoreCase(nombreNuevo)) {
            ciudadRepository.renombrarDepartamento(nombreAnterior, nombreNuevo, codigoNuevo);
        }

        log.info("Departamento actualizado: {} ({}) -> {} ({})", nombreAnterior, id, nombreNuevo, codigoNuevo);
        return DepartamentoResponse.from(departamento, ciudadRepository.existsByDepartamentoIgnoreCase(nombreNuevo));
    }

    @Transactional
    public void eliminar(Integer id) {
        Departamento departamento = repository.findById(id)
                .orElseThrow(() -> new ApiException("Departamento no encontrado", HttpStatus.NOT_FOUND));
        if (ciudadRepository.existsByDepartamentoIgnoreCase(departamento.getNombre())) {
            throw new ApiException(
                    "No se puede eliminar \"" + departamento.getNombre() + "\": hay ciudades registradas en ese departamento.",
                    HttpStatus.CONFLICT);
        }
        repository.delete(departamento);
        log.info("Departamento eliminado del catálogo: {} ({})", departamento.getNombre(), id);
    }

    // A diferencia de ciudad/barrio (texto libre escrito por cualquiera, sin sesión), un
    // departamento solo lo edita un admin desde este catálogo — se respeta su capitalización
    // exacta (solo se recorta espacios) en vez de forzar Title Case, que rompería nombres oficiales
    // con sufijos como "Bogotá, D.C." (quedaría "Bogotá, D.c.").
    private String normalizar(String texto) {
        return texto.trim().replaceAll("\\s+", " ");
    }

    private String clave(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(ES_CO).replaceAll("[.,]", "").replaceAll("\\s+", " ").trim();
    }
}
