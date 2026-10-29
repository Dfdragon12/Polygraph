package com.polygraph.erp.modules.riesgo.service;

import com.polygraph.erp.modules.formularios.repository.FormularioColumnaRepository;
import com.polygraph.erp.modules.formularios.service.OpcionesLista;
import com.polygraph.erp.modules.riesgo.dto.HallazgoCatalogoDtos.HallazgoRequest;
import com.polygraph.erp.modules.riesgo.dto.HallazgoCatalogoDtos.HallazgoResponse;
import com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion;
import com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Catálogo de hallazgos (V205): la base de la calificación por hallazgos. Los puntos, el tipo y
 *  las reglas se cambian aquí y el cálculo los usa de inmediato (no se guardan en los formularios).
 *  Lo que algún formulario usa no se borra: se desactiva. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogoHallazgosService {

    public static final String GRUPO = "GENERAL";

    private final CodigoCalificacionRepository repository;
    private final FormularioColumnaRepository columnaRepository;

    /** Todo el catálogo: qué hallazgos ofrece cada formulario lo define su plantilla (V210). */
    @Transactional(readOnly = true)
    public List<HallazgoResponse> listar(boolean soloActivos) {
        Set<String> usados = usados();
        return repository.findAll().stream()
                .filter(c -> !soloActivos || Boolean.TRUE.equals(c.getActivo()))
                .sorted(Comparator.comparing(CodigoCalificacion::getOrden).thenComparing(CodigoCalificacion::getCodigo))
                .map(c -> HallazgoResponse.de(c, usados.contains(c.getCodigo()))).toList();
    }

    public HallazgoResponse crear(HallazgoRequest r) {
        String codigo = r.codigo().trim().toUpperCase();
        if (repository.findByCodigo(codigo).isPresent()) {
            throw new ApiException("Ya existe el hallazgo " + codigo + ".", HttpStatus.CONFLICT);
        }
        int orden = repository.findAll().stream().mapToInt(c -> c.getOrden() == null ? 0 : c.getOrden()).max().orElse(0) + 1;
        CodigoCalificacion c = CodigoCalificacion.builder().grupo(GRUPO).codigo(codigo).orden(orden).build();
        aplicar(c, r);
        log.info("Hallazgo {} creado", codigo);
        return HallazgoResponse.de(repository.save(c), false);
    }

    /** El código no cambia (los formularios lo guardan tal cual). */
    public HallazgoResponse actualizar(Integer id, HallazgoRequest r) {
        CodigoCalificacion c = buscar(id);
        if (!c.getCodigo().equalsIgnoreCase(r.codigo().trim())) {
            throw new ApiException("El código no se puede cambiar: los formularios ya lo usan. Crea uno nuevo y desactiva este.",
                    HttpStatus.BAD_REQUEST);
        }
        aplicar(c, r);
        log.info("Hallazgo {} actualizado: {} {}", c.getCodigo(), r.tipo(), r.puntos());
        return HallazgoResponse.de(repository.save(c), usados().contains(c.getCodigo()));
    }

    public void eliminar(Integer id) {
        CodigoCalificacion c = buscar(id);
        if (usados().contains(c.getCodigo())) {
            throw new ApiException(c.getCodigo() + " está en uso en una plantilla: desactívalo en vez de borrarlo.", HttpStatus.CONFLICT);
        }
        repository.delete(c);
        log.info("Hallazgo {} eliminado", c.getCodigo());
    }

    /** Nuevo orden de todo el catálogo (lista de ids). */
    public List<HallazgoResponse> ordenar(List<Integer> ids) {
        Map<Integer, CodigoCalificacion> porId = new HashMap<>();
        repository.findAll().forEach(c -> porId.put(c.getId(), c));
        int i = 0;
        for (Integer id : ids) {
            CodigoCalificacion c = porId.get(id);
            if (c != null) c.setOrden(++i);
        }
        repository.saveAll(porId.values());
        return listar(false);
    }

    private void aplicar(CodigoCalificacion c, HallazgoRequest r) {
        boolean critico = "CRITICO".equals(r.tipo());
        boolean alerta = "ALERTA".equals(r.tipo());
        if ("PUNTOS".equals(r.tipo()) && r.puntos() == null) {
            throw new ApiException("Escribe los puntos (0 a 100).", HttpStatus.BAD_REQUEST);
        }
        c.setDescripcion(r.descripcion().trim());
        c.setCritico(critico);
        c.setEsAlerta(alerta);
        c.setPuntos(critico ? null : alerta ? Integer.valueOf(0) : r.puntos());
        c.setPorProceso(!alerta && r.acumula());
        c.setSoloConduccion(!alerta && r.soloCondicion());
        c.setActivo(r.activo() == null || r.activo());
    }

    /** Códigos que ofrece o genera algún campo de alguna plantilla. */
    private Set<String> usados() {
        Set<String> r = new HashSet<>();
        columnaRepository.findAll().forEach(col -> {
            if ("HALLAZGO".equals(col.getUsoCalificacion())) r.addAll(OpcionesLista.leer(col.getOpciones()));
            col.getHallazgos().forEach(h -> r.add(h.getCodigo()));
        });
        return r;
    }

    private CodigoCalificacion buscar(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ApiException("Hallazgo no encontrado.", HttpStatus.NOT_FOUND));
    }
}
