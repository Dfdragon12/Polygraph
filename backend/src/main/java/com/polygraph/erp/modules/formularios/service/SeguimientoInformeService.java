package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse.Devolucion;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse.Publicacion;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * PC-04 · Lo que el ejecutor ve de su informe DESPUÉS de enviarlo: si lo publicaron (por quién, cuándo, y si el gestor ajustó la conclusión o el nivel, con lo
 * anterior y lo nuevo) o si se lo devolvieron (quién, cuándo, motivo y detalle). Una sola lectura para los siete formatos: ningún formato arma su propio aviso.
 * No cambia nada; lee las columnas del subproceso y el historial de publicación (que solo se inserta).
 */
@Service
@RequiredArgsConstructor
public class SeguimientoInformeService {

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;

    /** null si el informe no está publicado. */
    @Transactional(readOnly = true)
    public Publicacion publicacion(ServicioSubproceso sp) {
        if (sp.getEstado() != EstadoSubproceso.PUBLICADO || sp.getFechaPublicado() == null) return null;
        String sugerido = null, motivoNivel = null, nivelCambiado = null;
        List<Map<String, Object>> cambios = jdbc.queryForList(
                "SELECT valor_anterior, valor_nuevo, motivo FROM historial_publicacion_subproceso WHERE id_servicio_subproceso = ? AND tipo = 'CAMBIO_NIVEL' "
                        + "ORDER BY fecha DESC, id DESC LIMIT 1", sp.getId());
        if (!cambios.isEmpty()) {
            sugerido = (String) cambios.get(0).get("valor_anterior");
            nivelCambiado = (String) cambios.get(0).get("valor_nuevo");
            motivoNivel = (String) cambios.get(0).get("motivo");
        }
        return armar(sp, nombreDe(sp.getPublicadoPor()), sugerido, nivelCambiado, motivoNivel);
    }

    /** null si el informe no está devuelto. */
    @Transactional(readOnly = true)
    public Devolucion devolucion(ServicioSubproceso sp) {
        if (!Boolean.TRUE.equals(sp.getDevuelto())) return null;
        return new Devolucion(nombreDe(sp.getDevueltoPor()), sp.getFechaDevuelto(), sp.getMotivoDevolucion(), sp.getDetalleDevolucion());
    }

    /** Lo anterior y lo nuevo, solo cuando de verdad cambió (puro: se prueba sin base). */
    static Publicacion armar(ServicioSubproceso sp, String publicadoPor, String nivelSugerido, String nivelCambiado, String motivoNivel) {
        String original = sp.getConclusion() == null ? null : sp.getConclusion().trim();
        String publicada = sp.getConclusionPublicada() == null ? null : sp.getConclusionPublicada().trim();
        boolean conclusionAjustada = original != null && !original.isBlank() && publicada != null && !publicada.equals(original);
        boolean nivelAjustado = nivelSugerido != null && nivelCambiado != null && !nivelSugerido.equals(nivelCambiado)
                && nivelCambiado.equals(sp.getNivelPublicado());
        return new Publicacion(sp.getFechaPublicado(), publicadoPor, conclusionAjustada ? original : null, publicada, conclusionAjustada,
                nivelAjustado ? nivelSugerido : null, sp.getNivelPublicado(), nivelAjustado, nivelAjustado ? motivoNivel : null);
    }

    private String nombreDe(Long idUsuario) {
        if (idUsuario == null) return null;
        return usuarioRepository.findById(idUsuario).map(SeguimientoInformeService::nombre).orElse(null);
    }

    private static String nombre(Usuario u) {
        return ((u.getNombre() == null ? "" : u.getNombre()) + (u.getApellido() == null ? "" : " " + u.getApellido())).trim();
    }
}
