package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.CategoriaNovedadRequest;
import com.polygraph.erp.modules.catalogo.dto.CategoriaNovedadResponse;
import com.polygraph.erp.modules.catalogo.dto.TipoNovedadRequest;
import com.polygraph.erp.modules.catalogo.dto.TipoNovedadResponse;
import com.polygraph.erp.modules.catalogo.entity.CategoriaNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import com.polygraph.erp.modules.catalogo.repository.CategoriaNovedadRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoNovedadRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Catálogo de tipos de novedad (admin/catálogo → Tipos de novedad): categorías y tipos que salen
 * en la lista "¿Qué pasó?" al reportar una novedad de subproceso. Lo que ya se usó no se borra,
 * se desactiva — así las novedades viejas conservan su tipo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TipoNovedadService {

    /** Solo los roles que EJECUTAN subprocesos reportan novedades. */
    private static final Set<Rol> ROLES_EJECUTORES = Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    private final CategoriaNovedadRepository categoriaRepository;
    private final TipoNovedadRepository tipoRepository;
    private final NovedadSubprocesoRepository novedadRepository;
    private final NotificacionService notificacionService;

    // ───────────────────────────── Categorías ─────────────────────────────

    @Transactional(readOnly = true)
    public List<CategoriaNovedadResponse> listarCategorias() {
        return categoriaRepository.findAllByOrderByOrdenAscNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoriaNovedadResponse crearCategoria(CategoriaNovedadRequest req) {
        CategoriaNovedad c = categoriaRepository.save(CategoriaNovedad.builder()
                .clave(claveUnica(req.nombre(), categoriaRepository::existsByClave))
                .nombre(req.nombre().trim())
                .color(req.color())
                .orden(req.orden() != null ? req.orden() : siguienteOrdenCategoria())
                .build());
        notificacionService.crearParaAdmins("CATALOGO", "Categoría de novedad creada", "Se creó la categoría \"" + c.getNombre() + "\".");
        log.info("Categoría de novedad creada: {} ({})", c.getNombre(), c.getClave());
        return toResponse(c);
    }

    @Transactional
    public CategoriaNovedadResponse actualizarCategoria(Integer id, CategoriaNovedadRequest req) {
        CategoriaNovedad c = categoria(id);
        c.setNombre(req.nombre().trim());
        c.setColor(req.color());
        if (req.orden() != null) c.setOrden(req.orden());
        log.info("Categoría de novedad actualizada: {}", id);
        return toResponse(c);
    }

    @Transactional
    public CategoriaNovedadResponse cambiarEstadoCategoria(Integer id, boolean activo) {
        CategoriaNovedad c = categoria(id);
        c.setActivo(activo);
        log.info("Categoría de novedad {} {}", id, activo ? "activada" : "desactivada");
        return toResponse(c);
    }

    @Transactional
    public void eliminarCategoria(Integer id) {
        CategoriaNovedad c = categoria(id);
        if (tipoRepository.countByCategoria_IdCategoriaNovedad(id) > 0) {
            throw new ApiException("La categoría tiene tipos de novedad — muévelos a otra categoría o desactívala.", HttpStatus.CONFLICT);
        }
        categoriaRepository.delete(c);
        log.info("Categoría de novedad eliminada: {}", id);
    }

    // ───────────────────────────── Tipos ─────────────────────────────

    @Transactional(readOnly = true)
    public List<TipoNovedadResponse> listarTipos() {
        return tipoRepository.findAllByOrderByCategoria_OrdenAscOrdenAscNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TipoNovedadResponse crearTipo(TipoNovedadRequest req) {
        CategoriaNovedad c = categoria(req.idCategoriaNovedad());
        TipoNovedad t = tipoRepository.save(TipoNovedad.builder()
                .categoria(c)
                .clave(claveUnica(req.nombre(), tipoRepository::existsByClave))
                .nombre(req.nombre().trim())
                .textoAyuda(textoONull(req.textoAyuda()))
                .orden(req.orden() != null ? req.orden() : siguienteOrdenTipo(c.getIdCategoriaNovedad()))
                .roles(parsearRoles(req.roles()))
                .build());
        notificacionService.crearParaAdmins("CATALOGO", "Tipo de novedad creado",
                "Se agregó \"" + t.getNombre() + "\" en " + c.getNombre() + ".");
        log.info("Tipo de novedad creado: {} ({}) roles={}", t.getNombre(), t.getClave(), t.getRoles());
        return toResponse(t);
    }

    @Transactional
    public TipoNovedadResponse actualizarTipo(Integer id, TipoNovedadRequest req) {
        TipoNovedad t = tipo(id);
        t.setCategoria(categoria(req.idCategoriaNovedad()));
        t.setNombre(req.nombre().trim());
        t.setTextoAyuda(textoONull(req.textoAyuda()));
        if (req.orden() != null) t.setOrden(req.orden());
        t.getRoles().clear();
        t.getRoles().addAll(parsearRoles(req.roles()));
        log.info("Tipo de novedad actualizado: {} roles={}", id, t.getRoles());
        return toResponse(t);
    }

    @Transactional
    public TipoNovedadResponse cambiarEstadoTipo(Integer id, boolean activo) {
        TipoNovedad t = tipo(id);
        t.setActivo(activo);
        log.info("Tipo de novedad {} {}", id, activo ? "activado" : "desactivado");
        return toResponse(t);
    }

    @Transactional
    public void eliminarTipo(Integer id) {
        TipoNovedad t = tipo(id);
        long enUso = novedadRepository.countByTipo_IdTipoNovedad(id);
        if (enUso > 0) {
            throw new ApiException("Este tipo ya se usó en " + enUso + " novedad" + (enUso == 1 ? "" : "es")
                    + " — desactívalo en vez de eliminarlo.", HttpStatus.CONFLICT);
        }
        tipoRepository.delete(t);
        log.info("Tipo de novedad eliminado: {}", id);
    }

    // ───────────────────────────── Apoyo ─────────────────────────────

    private CategoriaNovedad categoria(Integer id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Categoría de novedad no encontrada.", HttpStatus.NOT_FOUND));
    }

    private TipoNovedad tipo(Integer id) {
        return tipoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Tipo de novedad no encontrado.", HttpStatus.NOT_FOUND));
    }

    private Set<Rol> parsearRoles(Set<String> roles) {
        Set<Rol> resultado = new HashSet<>();
        for (String r : roles) {
            Rol rol;
            try {
                rol = Rol.valueOf(r);
            } catch (IllegalArgumentException e) {
                throw new ApiException("Rol no válido: " + r, HttpStatus.BAD_REQUEST);
            }
            if (!ROLES_EJECUTORES.contains(rol)) {
                throw new ApiException("Solo analista, poligrafista y visitador reportan novedades.", HttpStatus.BAD_REQUEST);
            }
            resultado.add(rol);
        }
        return resultado;
    }

    /** "Candidato llegó en embriaguez" -> "CANDIDATO_LLEGO_EN_EMBRIAGUEZ" (máx. 40), con sufijo
     *  _2, _3... si ya existe. La clave no cambia al renombrar: es el identificador estable. */
    static String claveBase(String nombre) {
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String clave = sinTildes.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (clave.isEmpty()) clave = "NOVEDAD";
        return clave.length() > 36 ? clave.substring(0, 36).replaceAll("_+$", "") : clave;
    }

    private String claveUnica(String nombre, Predicate<String> existe) {
        String base = claveBase(nombre);
        String clave = base;
        for (int i = 2; existe.test(clave); i++) clave = base + "_" + i;
        return clave;
    }

    private int siguienteOrdenCategoria() {
        return categoriaRepository.findAll().stream().map(CategoriaNovedad::getOrden)
                .max(Comparator.naturalOrder()).orElse(0) + 1;
    }

    private int siguienteOrdenTipo(Integer idCategoria) {
        return tipoRepository.findAll().stream()
                .filter(t -> t.getCategoria().getIdCategoriaNovedad().equals(idCategoria))
                .map(TipoNovedad::getOrden).max(Comparator.naturalOrder()).orElse(0) + 1;
    }

    private static String textoONull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private CategoriaNovedadResponse toResponse(CategoriaNovedad c) {
        return new CategoriaNovedadResponse(c.getIdCategoriaNovedad(), c.getClave(), c.getNombre(), c.getColor(),
                c.getOrden(), c.getActivo(), tipoRepository.countByCategoria_IdCategoriaNovedad(c.getIdCategoriaNovedad()));
    }

    private TipoNovedadResponse toResponse(TipoNovedad t) {
        CategoriaNovedad c = t.getCategoria();
        return new TipoNovedadResponse(t.getIdTipoNovedad(), t.getClave(), t.getNombre(),
                c.getIdCategoriaNovedad(), c.getNombre(), c.getColor(),
                t.getRoles().stream().map(Enum::name).sorted().toList(),
                t.getTextoAyuda(), t.getOrden(), t.getActivo(),
                t.getIdTipoNovedad() != null ? novedadRepository.countByTipo_IdTipoNovedad(t.getIdTipoNovedad()) : 0);
    }
}
