package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.BarrioRequest;
import com.polygraph.erp.modules.catalogo.dto.BarrioResponse;
import com.polygraph.erp.modules.catalogo.entity.Barrio;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.BarrioRepository;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Catálogo de barrios — mismo criterio que CiudadService: no existe una fuente oficial única de
 * barrios en Colombia, así que la tabla crece sola. Quien escribe una dirección con un barrio que
 * no está en el catálogo lo crea como "pendiente de confirmar" (scoped a una ciudad puntual — el
 * mismo nombre de barrio puede existir en dos ciudades distintas, son registros separados), y un
 * admin lo revisa/depura después, igual que con las ciudades.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BarrioService {

    private static final Locale ES_CO = new Locale("es", "CO");

    private final BarrioRepository repository;
    private final CiudadRepository ciudadRepository;

    public List<BarrioResponse> listarPorCiudad(Integer idCiudad) {
        List<Barrio> barrios = idCiudad != null
                ? repository.findByCiudad_IdCiudadOrderByNombreAsc(idCiudad)
                : repository.findAllByOrderByNombreAsc();
        return barrios.stream().map(BarrioResponse::from).toList();
    }

    // Público: mismo patrón "obtener o crear" que Ciudad.crear — si el barrio ya existe en esa
    // ciudad (sin importar mayúsculas), se reutiliza en vez de duplicar.
    @Transactional
    public BarrioResponse crear(BarrioRequest req) {
        Ciudad ciudad = ciudadRepository.findById(req.idCiudad())
                .orElseThrow(() -> new ApiException("Ciudad no encontrada", HttpStatus.NOT_FOUND));

        String nombre = normalizar(req.nombre());
        Barrio existente = repository.findByCiudad_IdCiudadAndNombreIgnoreCase(req.idCiudad(), nombre).orElse(null);
        if (existente != null) {
            return BarrioResponse.from(existente);
        }

        Barrio barrio = Barrio.builder()
                .nombre(nombre)
                .ciudad(ciudad)
                .pendienteConfirmacion(true)
                .build();
        repository.save(barrio);
        log.info("Barrio agregado al catálogo (pendiente de confirmación): {} ({})", nombre, ciudad.getNombreCiudad());
        return BarrioResponse.from(barrio);
    }

    // Solo admin.
    @Transactional
    public BarrioResponse confirmar(Integer id) {
        Barrio barrio = repository.findById(id)
                .orElseThrow(() -> new ApiException("Barrio no encontrado", HttpStatus.NOT_FOUND));
        barrio.setPendienteConfirmacion(false);
        log.info("Barrio confirmado: {} ({})", barrio.getNombre(), barrio.getCiudad().getNombreCiudad());
        return BarrioResponse.from(barrio);
    }

    public long contarPendientes() {
        return repository.countByPendienteConfirmacionTrue();
    }

    // Solo admin — mismo criterio que Ciudad.eliminar: bloqueado si ya está en uso.
    @Transactional
    public void eliminar(Integer id) {
        Barrio barrio = repository.findById(id)
                .orElseThrow(() -> new ApiException("Barrio no encontrado", HttpStatus.NOT_FOUND));
        if (repository.estaEnUso(id)) {
            throw new ApiException(
                    "No se puede eliminar \"" + barrio.getNombre() + "\": hay clientes o candidatos que lo usan.",
                    HttpStatus.CONFLICT);
        }
        try {
            repository.delete(barrio);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(
                    "No se puede eliminar \"" + barrio.getNombre() + "\": hay clientes o candidatos que lo usan.",
                    HttpStatus.CONFLICT);
        }
        log.info("Barrio eliminado del catálogo: {} ({})", barrio.getNombre(), barrio.getCiudad().getNombreCiudad());
    }

    private String normalizar(String texto) {
        String limpio = texto.trim().replaceAll("\\s+", " ");
        StringBuilder resultado = new StringBuilder();
        for (String palabra : limpio.split(" ")) {
            if (palabra.isEmpty()) continue;
            if (resultado.length() > 0) resultado.append(" ");
            resultado.append(Character.toUpperCase(palabra.charAt(0)))
                    .append(palabra.substring(1).toLowerCase(ES_CO));
        }
        return resultado.toString();
    }
}
