package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.CiudadRequest;
import com.polygraph.erp.modules.catalogo.dto.CiudadResponse;
import com.polygraph.erp.modules.catalogo.dto.ClasificarCiudadRequest;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CiudadService {

    private static final Locale ES_CO = new Locale("es", "CO");

    private final CiudadRepository repository;

    public List<CiudadResponse> listar() {
        Set<Integer> idsEnUso = Set.copyOf(repository.idsEnUso());
        List<CiudadResponse> ciudades = repository.findAllByOrderByNombreCiudadAsc().stream()
                .map(c -> CiudadResponse.from(c, idsEnUso.contains(c.getIdCiudad())))
                .toList();
        log.debug("Catálogo de ciudades consultado: {} registros", ciudades.size());
        return ciudades;
    }

    // Público: permite que un evaluado (sin sesión) registre su ciudad si no está en el catálogo.
    // Es "obtener o crear" — si ya existe una ciudad con el mismo nombre y departamento (sin
    // importar mayúsculas/tildes de capitalización) se reutiliza en vez de crear un duplicado.
    @Transactional
    public CiudadResponse crear(CiudadRequest req) {
        String nombre = normalizar(req.nombreCiudad());
        String departamento = normalizar(req.departamento());

        // Comparación insensible a tildes y mayúsculas: "Bogotá"/"Bogota" o "BOGOTÁ" deben
        // considerarse la misma ciudad y no crear un duplicado en el catálogo.
        String claveNombre = quitarTildes(nombre);
        String claveDepartamento = quitarTildes(departamento);
        Ciudad existente = repository.findAllByOrderByNombreCiudadAsc().stream()
                .filter(c -> quitarTildes(c.getNombreCiudad()).equals(claveNombre)
                        && quitarTildes(c.getDepartamento()).equals(claveDepartamento))
                .findFirst()
                .orElse(null);
        if (existente != null) {
            log.debug("Ciudad ya existía en el catálogo, se reutiliza: {} ({})", nombre, departamento);
            return CiudadResponse.from(existente, repository.estaEnUso(existente.getIdCiudad()));
        }

        String codigoDaneCiudad = limpiarCodigo(req.codigoDaneCiudad());
        String codigoDaneDepto = limpiarCodigo(req.codigoDaneDepto());

        // Se auto-crea sin sesión (cliente/evaluado) — nunca se asume el nivel más barato: entra
        // directo como MUNICIPIO_SECUNDARIO y queda pendiente de que un admin la confirme o cambie.
        Ciudad ciudad = Ciudad.builder()
                .nombreCiudad(nombre)
                .departamento(departamento)
                .codigoDaneCiudad(codigoDaneCiudad)
                .codigoDaneDepto(codigoDaneDepto)
                .nivelCiudad(NivelCiudad.MUNICIPIO_SECUNDARIO)
                .pendienteConfirmacion(true)
                .build();
        repository.save(ciudad);
        log.info("Ciudad agregada al catálogo (pendiente de confirmación): {} ({})", nombre, departamento);
        return CiudadResponse.from(ciudad, false);
    }

    // Solo admin — asigna o cambia el nivel logístico de una ciudad; la deja confirmada.
    @Transactional
    public CiudadResponse clasificar(Integer id, ClasificarCiudadRequest req) {
        Ciudad ciudad = repository.findById(id)
                .orElseThrow(() -> new ApiException("Ciudad no encontrada", HttpStatus.NOT_FOUND));
        ciudad.setNivelCiudad(req.nivelCiudad());
        ciudad.setPendienteConfirmacion(false);
        log.info("Ciudad clasificada: {} ({}) -> {}", ciudad.getNombreCiudad(), ciudad.getDepartamento(), req.nivelCiudad());
        return CiudadResponse.from(ciudad, repository.estaEnUso(id));
    }

    // Solo admin — confirma que el nivel actual (típicamente el default MUNICIPIO_SECUNDARIO) es
    // correcto, sin cambiarlo. Limpia la alerta de pendiente.
    @Transactional
    public CiudadResponse confirmar(Integer id) {
        Ciudad ciudad = repository.findById(id)
                .orElseThrow(() -> new ApiException("Ciudad no encontrada", HttpStatus.NOT_FOUND));
        ciudad.setPendienteConfirmacion(false);
        log.info("Ciudad confirmada: {} ({}) en nivel {}", ciudad.getNombreCiudad(), ciudad.getDepartamento(), ciudad.getNivelCiudad());
        return CiudadResponse.from(ciudad, repository.estaEnUso(id));
    }

    public long contarPendientes() {
        return repository.countByPendienteConfirmacionTrue();
    }

    // Solo admin — se bloquea el borrado ANTES de intentarlo si la ciudad está referenciada por
    // clientes/candidatos/visitas/empleados/recargos (FK real en la base de datos): en ese caso
    // solo queda disponible reclasificar su nivel logístico, nunca eliminarla. El catch de
    // DataIntegrityViolationException queda como red de seguridad ante una condición de carrera
    // (alguien la usa justo entre el chequeo y el borrado).
    @Transactional
    public void eliminar(Integer id) {
        Ciudad ciudad = repository.findById(id)
                .orElseThrow(() -> new ApiException("Ciudad no encontrada", HttpStatus.NOT_FOUND));
        if (repository.estaEnUso(id)) {
            throw new ApiException(
                    "No se puede eliminar \"" + ciudad.getNombreCiudad() + "\": hay clientes, candidatos u otros registros que la usan. Solo puedes editar su nivel logístico.",
                    HttpStatus.CONFLICT);
        }
        try {
            repository.delete(ciudad);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(
                    "No se puede eliminar \"" + ciudad.getNombreCiudad() + "\": hay clientes, candidatos u otros registros que la usan.",
                    HttpStatus.CONFLICT);
        }
        log.info("Ciudad eliminada del catálogo: {} ({})", ciudad.getNombreCiudad(), ciudad.getDepartamento());
    }

    private String limpiarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        return codigo.trim();
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

    private String quitarTildes(String texto) {
        String descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "").toUpperCase(ES_CO);
    }
}
