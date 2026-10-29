package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.MotivoSubprocesoRequest;
import com.polygraph.erp.modules.catalogo.dto.MotivoSubprocesoResponse;
import com.polygraph.erp.modules.catalogo.entity.MotivoSubproceso;
import com.polygraph.erp.modules.catalogo.repository.MotivoSubprocesoRepository;
import com.polygraph.erp.shared.enums.TipoMotivo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Listas de motivos de reprogramación, cancelación y devolución (SE-02). El admin las administra; quien actúa elige una. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MotivoSubprocesoService {

    /** Motivo ya validado: el nombre de la lista, el texto escrito (si lo hubo) y cómo queda en el historial. */
    public record MotivoResuelto(Integer id, String nombre, String texto, String descripcion, String codigo) {}

    private final MotivoSubprocesoRepository repository;

    @Transactional(readOnly = true)
    public List<MotivoSubprocesoResponse> listarActivos(TipoMotivo tipo) {
        return repository.findByTipoAndActivoTrueOrderByOrdenAscIdAsc(tipo).stream().map(MotivoSubprocesoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MotivoSubprocesoResponse> listarTodos() {
        return repository.findAllByOrderByTipoAscOrdenAscIdAsc().stream().map(MotivoSubprocesoResponse::de).toList();
    }

    public MotivoSubprocesoResponse crear(MotivoSubprocesoRequest req) {
        String nombre = req.nombre().trim();
        List<MotivoSubproceso> todos = repository.findAllByOrderByTipoAscOrdenAscIdAsc();
        validarNoRepetido(todos, req.tipo(), nombre, null);
        int ultimo = todos.stream().filter(m -> m.getTipo() == req.tipo()).mapToInt(MotivoSubproceso::getOrden).max().orElse(0);
        MotivoSubproceso m = repository.save(MotivoSubproceso.builder()
                .tipo(req.tipo()).nombre(nombre)
                .requiereTexto(Boolean.TRUE.equals(req.requiereTexto()))
                .orden(req.orden() != null ? req.orden() : ultimo + 1)
                .activo(req.activo() == null || req.activo())
                .build());
        log.info("Motivos: se creó '{}' ({})", nombre, req.tipo());
        return MotivoSubprocesoResponse.de(m);
    }

    /** El tipo no se cambia: el motivo ya usado en el historial saldría de la lista donde se eligió. */
    public MotivoSubprocesoResponse actualizar(Integer id, MotivoSubprocesoRequest req) {
        MotivoSubproceso m = repository.findById(id).orElseThrow(() -> new ApiException("El motivo no existe", HttpStatus.NOT_FOUND));
        if (req.tipo() != m.getTipo()) throw new ApiException("No se puede cambiar el tipo de un motivo; crea uno nuevo.", HttpStatus.BAD_REQUEST);
        String nombre = req.nombre().trim();
        validarNoRepetido(repository.findAllByOrderByTipoAscOrdenAscIdAsc(), m.getTipo(), nombre, id);
        m.setNombre(nombre);
        if (req.requiereTexto() != null) m.setRequiereTexto(req.requiereTexto());
        if (req.orden() != null) m.setOrden(req.orden());
        if (req.activo() != null) m.setActivo(req.activo());
        return MotivoSubprocesoResponse.de(repository.save(m));
    }

    /**
     * Valida el motivo que eligió quien reprograma, cancela o devuelve: existe, es de esa lista, está activo y trae el texto si
     * es "Otro". Devuelve cómo queda escrito en el historial.
     */
    @Transactional(readOnly = true)
    public MotivoResuelto resolver(Integer idMotivo, TipoMotivo tipo, String texto) {
        if (idMotivo == null) throw new ApiException("Falta indicar el motivo.", HttpStatus.BAD_REQUEST);
        MotivoSubproceso m = repository.findById(idMotivo).orElseThrow(() -> new ApiException("El motivo no existe", HttpStatus.BAD_REQUEST));
        if (m.getTipo() != tipo) throw new ApiException("Ese motivo no corresponde a esta acción.", HttpStatus.BAD_REQUEST);
        if (!Boolean.TRUE.equals(m.getActivo())) throw new ApiException("Ese motivo ya no está disponible.", HttpStatus.BAD_REQUEST);
        String limpio = texto == null ? "" : texto.trim();
        if (Boolean.TRUE.equals(m.getRequiereTexto()) && limpio.isEmpty()) {
            throw new ApiException("Escribe cuál es el motivo en «" + m.getNombre() + "».", HttpStatus.BAD_REQUEST);
        }
        String descripcion = limpio.isEmpty() ? m.getNombre()
                : Boolean.TRUE.equals(m.getRequiereTexto()) ? m.getNombre() + ": " + limpio : m.getNombre() + " — " + limpio;
        return new MotivoResuelto(m.getId(), m.getNombre(), limpio.isEmpty() ? null : limpio, descripcion, m.getCodigo());
    }

    private static void validarNoRepetido(List<MotivoSubproceso> todos, TipoMotivo tipo, String nombre, Integer idPropio) {
        boolean repetido = todos.stream().anyMatch(m -> m.getTipo() == tipo && !m.getId().equals(idPropio) && m.getNombre().equalsIgnoreCase(nombre));
        if (repetido) throw new ApiException("Ya existe el motivo \"" + nombre + "\" en esa lista", HttpStatus.CONFLICT);
    }
}
