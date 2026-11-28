package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Registro de cambios de las preguntas relevantes de la poligrafía (PF-05): qué se cambió, quién y cuándo. Solo inserción. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistorialPreguntasService {

    public record Cambio(int posicion, String anterior, String nuevo, String usuario, LocalDateTime fecha) {}

    private final JdbcTemplate jdbc;

    /** Compara lo que había guardado con lo que llega, por posición. Si todavía no había nada guardado no registra nada (es el texto que nació
     *  de la solicitud del cliente). Las preguntas nuevas quedan con «anterior» vacío. */
    public void registrar(Integer idFormulario, Usuario actor, List<String> nuevos) {
        if (idFormulario == null || nuevos == null) return;
        List<String> guardados = jdbc.query(
                "SELECT COALESCE(d.valor, '') FROM formulario_filas f JOIN formulario_secciones s ON s.id_seccion = f.id_seccion "
                        + "JOIN formulario_columnas c ON c.id_seccion = s.id_seccion AND c.clave = 'pr_pregunta' "
                        + "LEFT JOIN formulario_datos d ON d.id_fila = f.id_fila AND d.id_columna = c.id_columna "
                        + "WHERE f.id_formulario = ? AND s.clave = 'preguntas' ORDER BY f.orden",
                (rs, n) -> rs.getString(1), idFormulario);
        if (guardados.isEmpty()) return;
        String nombre = ((actor.getNombre() == null ? "" : actor.getNombre()) + " " + (actor.getApellido() == null ? "" : actor.getApellido())).trim();
        for (int i = 0; i < nuevos.size(); i++) {
            String nuevo = nuevos.get(i) == null ? "" : nuevos.get(i).trim();
            String anterior = i < guardados.size() ? guardados.get(i).trim() : null;
            if (nuevo.isEmpty() || Objects.equals(anterior, nuevo)) continue;
            jdbc.update("INSERT INTO preguntas_relevantes_historial (id_formulario, posicion, texto_anterior, texto_nuevo, id_usuario, nombre_usuario) VALUES (?, ?, ?, ?, ?, ?)",
                    idFormulario, i + 1, anterior, nuevo, actor.getIdUsuario(), nombre);
        }
    }

    public List<Cambio> listar(Integer idFormulario) {
        if (idFormulario == null) return List.of();
        return jdbc.query("SELECT posicion, texto_anterior, texto_nuevo, nombre_usuario, fecha FROM preguntas_relevantes_historial WHERE id_formulario = ? ORDER BY fecha, id",
                (rs, n) -> new Cambio(rs.getInt("posicion"), rs.getString("texto_anterior"), rs.getString("texto_nuevo"), rs.getString("nombre_usuario"),
                        rs.getTimestamp("fecha").toLocalDateTime()), idFormulario);
    }
}
