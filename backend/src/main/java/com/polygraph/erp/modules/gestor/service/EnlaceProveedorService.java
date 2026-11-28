package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.gestor.service.ReglasEnlaceProveedor.Estado;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** PF-09 · Enlaces del proveedor externo. El programador responsable (o el administrador) genera UN enlace por evaluación de
 *  poligrafía; quien lo abre diligencia esa evaluación sin usuario ni contraseña. Se guarda la huella del token, nunca el
 *  token. Vence al enviar el informe o a las 72 h, lo primero; se puede revocar y generar otro (el anterior queda cerrado).
 *  La tabla es también el historial: quién lo generó, cuándo y cuándo se usó. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnlaceProveedorService {

    /** Lo que el filtro necesita saber de un enlace válido. */
    public record Sesion(Long idEnlace, Long idServicioSubproceso, Long idUsuarioEjecutor, OffsetDateTime expiraEn) {}

    /** El token solo se devuelve aquí, una vez: no se puede volver a ver. */
    public record Generado(Long id, String token, OffsetDateTime expiraEn) {}

    public record Entrada(Long id, String estado, String generadoPor, OffsetDateTime generadoEn, OffsetDateTime expiraEn,
                          OffsetDateTime primerUsoEn, OffsetDateTime ultimoUsoEn, int usos, String revocadoPor, OffsetDateTime revocadoEn,
                          OffsetDateTime cerradoEn) {}

    private final JdbcTemplate jdbc;
    private final ServicioSubprocesoRepository subprocesoRepository;
    private final Clock reloj;

    @Transactional
    public Generado generar(Long idSubproceso, Usuario actor) {
        ServicioSubproceso sp = coordinado(idSubproceso, actor);
        if (sp.getTipoProgreso().getRolResponsable() != Rol.POLIGRAFISTA) {
            throw new ApiException("El enlace para proveedor externo es solo para evaluaciones de poligrafía.", HttpStatus.CONFLICT);
        }
        if (sp.getUsuarioAsignado() == null) {
            throw new ApiException("Asigna primero al poligrafista: el enlace trabaja a su nombre.", HttpStatus.CONFLICT);
        }
        if (sp.getEstado() != EstadoSubproceso.EN_EJECUCION) {
            throw new ApiException("El enlace solo se puede generar mientras la evaluación está en ejecución.", HttpStatus.CONFLICT);
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        // Uno solo vigente: el anterior deja de servir.
        jdbc.update("UPDATE enlaces_proveedor SET cerrado_en = ?, motivo_cierre = 'REEMPLAZADO' WHERE id_servicio_subproceso = ? AND revocado_en IS NULL AND cerrado_en IS NULL",
                ahora, idSubproceso);
        String token = ReglasEnlaceProveedor.nuevoToken();
        OffsetDateTime expira = ahora.plus(ReglasEnlaceProveedor.VIGENCIA);
        Long id = jdbc.queryForObject(
                "INSERT INTO enlaces_proveedor (id_servicio_subproceso, token_hash, creado_por, creado_en, expira_en) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, idSubproceso, ReglasEnlaceProveedor.huella(token), actor.getIdUsuario(), ahora, expira);
        log.info("Enlace de proveedor {} generado para la evaluación {} por {}", id, idSubproceso, actor.getIdUsuario());
        return new Generado(id, token, expira);
    }

    @Transactional
    public void revocar(Long idSubproceso, Usuario actor) {
        coordinado(idSubproceso, actor);
        int filas = jdbc.update("UPDATE enlaces_proveedor SET revocado_en = ?, revocado_por = ? WHERE id_servicio_subproceso = ? AND revocado_en IS NULL AND cerrado_en IS NULL",
                OffsetDateTime.now(reloj), actor.getIdUsuario(), idSubproceso);
        if (filas == 0) throw new ApiException("No hay un enlace vigente para revocar.", HttpStatus.NOT_FOUND);
        log.info("Enlace de proveedor de la evaluación {} revocado por {}", idSubproceso, actor.getIdUsuario());
    }

    @Transactional(readOnly = true)
    public List<Entrada> historial(Long idSubproceso, Usuario actor) {
        coordinado(idSubproceso, actor);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        return jdbc.query("""
                SELECT e.id, e.creado_en, e.expira_en, e.primer_uso_en, e.ultimo_uso_en, e.usos, e.revocado_en, e.cerrado_en, e.motivo_cierre,
                       TRIM(CONCAT(c.nombre, ' ', COALESCE(c.apellido, ''))) AS generado_por,
                       TRIM(CONCAT(r.nombre, ' ', COALESCE(r.apellido, ''))) AS revocado_por
                FROM enlaces_proveedor e
                JOIN usuarios c ON c.id_usuario = e.creado_por
                LEFT JOIN usuarios r ON r.id_usuario = e.revocado_por
                WHERE e.id_servicio_subproceso = ?
                ORDER BY e.creado_en DESC, e.id DESC""",
                (rs, n) -> {
                    OffsetDateTime expira = rs.getObject("expira_en", OffsetDateTime.class);
                    OffsetDateTime revocado = rs.getObject("revocado_en", OffsetDateTime.class);
                    OffsetDateTime cerrado = rs.getObject("cerrado_en", OffsetDateTime.class);
                    Estado estado = ReglasEnlaceProveedor.estado(expira, revocado, cerrado, rs.getString("motivo_cierre"), ahora);
                    return new Entrada(rs.getLong("id"), estado.name(), rs.getString("generado_por"), rs.getObject("creado_en", OffsetDateTime.class), expira,
                            rs.getObject("primer_uso_en", OffsetDateTime.class), rs.getObject("ultimo_uso_en", OffsetDateTime.class), rs.getInt("usos"),
                            rs.getString("revocado_por"), revocado, cerrado);
                }, idSubproceso);
    }

    /** El enlace es válido solo si está vigente, la evaluación sigue en ejecución y tiene a quién atribuirle lo que se guarde. */
    @Transactional(readOnly = true)
    public Optional<Sesion> resolver(String token) {
        if (!ReglasEnlaceProveedor.tokenBienFormado(token)) return Optional.empty();
        List<Sesion> r = jdbc.query("""
                SELECT e.id, e.id_servicio_subproceso, sp.id_usuario_asignado, e.expira_en
                FROM enlaces_proveedor e JOIN servicio_subprocesos sp ON sp.id = e.id_servicio_subproceso
                WHERE e.token_hash = ? AND e.revocado_en IS NULL AND e.cerrado_en IS NULL AND e.expira_en > ?
                  AND sp.estado = 'EN_EJECUCION' AND sp.id_usuario_asignado IS NOT NULL""",
                (rs, n) -> new Sesion(rs.getLong("id"), rs.getLong("id_servicio_subproceso"), rs.getLong("id_usuario_asignado"), rs.getObject("expira_en", OffsetDateTime.class)),
                ReglasEnlaceProveedor.huella(token), OffsetDateTime.now(reloj));
        return r.stream().findFirst();
    }

    /** Por qué un enlace ya no sirve, para decírselo a quien lo abre ("ya se envió", "venció"...). Código estable para la pantalla. */
    @Transactional(readOnly = true)
    public String motivoInvalido(String token) {
        if (!ReglasEnlaceProveedor.tokenBienFormado(token)) return "ENLACE_DESCONOCIDO";
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<String> r = jdbc.query("""
                SELECT e.expira_en, e.revocado_en, e.cerrado_en, e.motivo_cierre, sp.estado
                FROM enlaces_proveedor e JOIN servicio_subprocesos sp ON sp.id = e.id_servicio_subproceso WHERE e.token_hash = ?""",
                (rs, n) -> {
                    Estado estado = ReglasEnlaceProveedor.estado(rs.getObject("expira_en", OffsetDateTime.class), rs.getObject("revocado_en", OffsetDateTime.class),
                            rs.getObject("cerrado_en", OffsetDateTime.class), rs.getString("motivo_cierre"), ahora);
                    return estado == Estado.VIGENTE ? "ENLACE_NO_DISPONIBLE" : "ENLACE_" + estado.name();
                }, ReglasEnlaceProveedor.huella(token));
        return r.isEmpty() ? "ENLACE_DESCONOCIDO" : r.get(0);
    }

    /** Se anota el uso (primera vez, última y cuántas veces) sin escribir en cada petición: como mucho una vez por minuto. */
    public void registrarUso(Long idEnlace) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        jdbc.update("UPDATE enlaces_proveedor SET primer_uso_en = COALESCE(primer_uso_en, ?), ultimo_uso_en = ?, usos = usos + 1 WHERE id = ? AND (ultimo_uso_en IS NULL OR ultimo_uso_en < ?)",
                ahora, ahora, idEnlace, ahora.minusMinutes(1));
    }

    /** Al enviar el informe el enlace deja de servir. Lo llama AsignacionService.completar dentro de su transacción. */
    public void cerrarAlEnviar(Long idSubproceso) {
        int n = jdbc.update("UPDATE enlaces_proveedor SET cerrado_en = ?, motivo_cierre = 'ENVIADO' WHERE id_servicio_subproceso = ? AND revocado_en IS NULL AND cerrado_en IS NULL",
                OffsetDateTime.now(reloj), idSubproceso);
        if (n > 0) log.info("Enlace de proveedor de la evaluación {} cerrado: el informe se envió", idSubproceso);
    }

    /** Solo el programador responsable de esa cita (o el administrador) maneja el enlace. */
    private ServicioSubproceso coordinado(Long idSubproceso, Usuario actor) {
        ServicioSubproceso sp = subprocesoRepository.findById(idSubproceso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        boolean admin = actor.getRol() == Rol.ADMIN_POLYGRAPH;
        boolean programador = actor.getRol() == Rol.PROGRAMADOR && (sp.getProgramadorResponsable() == null
                || Objects.equals(sp.getProgramadorResponsable().getIdUsuario(), actor.getIdUsuario()));
        if (!admin && !programador) {
            throw new ApiException("Solo el programador responsable de esta cita puede manejar su enlace.", HttpStatus.FORBIDDEN);
        }
        return sp;
    }
}
