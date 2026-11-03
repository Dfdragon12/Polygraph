package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.utils.AccesoGestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** Foto del evaluado en la poligrafía (PF-06). La toma en vivo el poligrafista asignado con la cámara de su dispositivo, junto con la verificación de la
 *  cédula original; es obligatoria para pasar a la fase 2. Queda fija al pasar a la fase 2: desde ahí solo el gestor (o el admin) la reemplaza, con motivo.
 *  Nada de esto se imprime salvo la foto, recortada 3:4, en el marcador ev_foto del informe. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FotoEvaluadoService {

    private static final String CATEGORIA = "fotos-poligrafia";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    /** La fecha y hora de la foto son siempre de Colombia, sin importar la zona del servidor. */
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public record Foto(Long id, String tomadaPor, LocalDateTime fecha, boolean cedulaVerificada, boolean vigente, String motivoReemplazo, LocalDateTime bloqueadaEn, String origen) {}

    /** existe: hay foto vigente. bloqueada: ya se pasó a la fase 2. puedeTomar / puedeReemplazar: lo que puede hacer quien pregunta. */
    public record Estado(boolean existe, boolean bloqueada, boolean puedeTomar, boolean puedeReemplazar, Foto vigente, List<Foto> historial) {}

    private final JdbcTemplate jdbc;
    private final AlmacenamientoDocumentosService almacenamiento;
    private final ServicioSubprocesoRepository subprocesoRepository;

    @Transactional(readOnly = true)
    public Estado estado(Long idSubproceso, Usuario actor) {
        ServicioSubproceso sp = subproceso(idSubproceso, actor);
        List<Foto> todas = jdbc.query(
                "SELECT id, nombre_usuario, fecha, verificacion_cedula, vigente, motivo_reemplazo, bloqueada_en, origen FROM fotos_evaluado_poligrafia WHERE id_servicio_subproceso = ? ORDER BY fecha DESC, id DESC",
                (rs, n) -> new Foto(rs.getLong("id"), rs.getString("nombre_usuario"), rs.getTimestamp("fecha").toLocalDateTime(), rs.getBoolean("verificacion_cedula"),
                        rs.getBoolean("vigente"), rs.getString("motivo_reemplazo"),
                        rs.getTimestamp("bloqueada_en") == null ? null : rs.getTimestamp("bloqueada_en").toLocalDateTime(), rs.getString("origen")), idSubproceso);
        Foto vigente = todas.stream().filter(Foto::vigente).findFirst().orElse(null);
        boolean bloqueada = vigente != null && vigente.bloqueadaEn() != null;
        boolean cerrado = cerrado(sp);
        return new Estado(vigente != null, bloqueada, esElPoligrafista(sp, actor) && !bloqueada && !cerrado, esCoordinador(actor), vigente,
                todas.stream().filter(f -> !f.vigente()).toList());
    }

    /** Guarda la foto que el poligrafista acaba de tomar (reemplaza la vigente mientras no se haya pasado a la fase 2). */
    @Transactional
    public Estado tomar(Long idSubproceso, MultipartFile archivo, boolean cedulaVerificada, String origen, Usuario actor) {
        ServicioSubproceso sp = subproceso(idSubproceso, actor);
        if (!esElPoligrafista(sp, actor)) throw new ApiException("Solo el poligrafista asignado puede tomar la foto del evaluado.", HttpStatus.FORBIDDEN);
        if (cerrado(sp)) throw new ApiException("Este subproceso ya está cerrado.", HttpStatus.CONFLICT);
        if (!cedulaVerificada) throw new ApiException("Verifica la cédula original antes de guardar la foto.", HttpStatus.BAD_REQUEST);
        Foto actual = vigente(idSubproceso);
        if (actual != null && actual.bloqueadaEn() != null) {
            throw new ApiException("La foto ya quedó fija: solo el gestor puede reemplazarla.", HttpStatus.CONFLICT);
        }
        String ruta = guardarImagen(archivo, idSubproceso);
        if (actual != null) jdbc.update("UPDATE fotos_evaluado_poligrafia SET vigente = FALSE, motivo_reemplazo = 'Repetida por el poligrafista' WHERE id = ?", actual.id());
        insertar(idSubproceso, ruta, actor, true, null, null, origenValido(origen));
        log.info("Foto del evaluado del subproceso {} tomada por {} (origen {})", idSubproceso, actor.getEmail(), origenValido(origen));
        return estado(idSubproceso, actor);
    }

    /** El gestor (o el admin) reemplaza la foto, con motivo; la anterior queda en el historial. */
    @Transactional
    public Estado reemplazar(Long idSubproceso, MultipartFile archivo, String motivo, String origen, Usuario actor) {
        subproceso(idSubproceso, actor);
        if (!esCoordinador(actor)) throw new ApiException("Solo el gestor puede reemplazar la foto.", HttpStatus.FORBIDDEN);
        if (motivo == null || motivo.isBlank()) throw new ApiException("Escribe el motivo del reemplazo.", HttpStatus.BAD_REQUEST);
        Foto actual = vigente(idSubproceso);
        String ruta = guardarImagen(archivo, idSubproceso);
        if (actual != null) jdbc.update("UPDATE fotos_evaluado_poligrafia SET vigente = FALSE WHERE id = ?", actual.id());
        // La que reemplaza hereda el bloqueo (si ya estaba fija, sigue fija); la verificación de la cédula la hizo quien tomó la original.
        insertar(idSubproceso, ruta, actor, actual == null || actual.cedulaVerificada(), motivo.trim(), actual == null ? null : actual.bloqueadaEn(), origenValido(origen));
        log.info("Foto del evaluado del subproceso {} reemplazada por {} ({})", idSubproceso, actor.getEmail(), motivo.trim());
        return estado(idSubproceso, actor);
    }

    /** Al pasar a la fase 2 la foto queda fija. Sin foto (o sin verificación) no se puede pasar. */
    @Transactional
    public Estado bloquear(Long idSubproceso, Usuario actor) {
        ServicioSubproceso sp = subproceso(idSubproceso, actor);
        if (!esElPoligrafista(sp, actor)) throw new ApiException("Solo el poligrafista asignado puede iniciar la entrevista.", HttpStatus.FORBIDDEN);
        Foto actual = vigente(idSubproceso);
        if (actual == null || !actual.cedulaVerificada()) {
            throw new ApiException("Toma la foto del evaluado y verifica su cédula.", HttpStatus.CONFLICT);
        }
        if (actual.bloqueadaEn() == null) jdbc.update("UPDATE fotos_evaluado_poligrafia SET bloqueada_en = NOW() WHERE id = ?", actual.id());
        return estado(idSubproceso, actor);
    }

    @Transactional(readOnly = true)
    public Resource imagen(Long idSubproceso, Usuario actor) {
        subproceso(idSubproceso, actor);
        String ruta = rutaVigente(idSubproceso).orElseThrow(() -> new ApiException("Todavía no hay foto del evaluado.", HttpStatus.NOT_FOUND));
        return almacenamiento.cargarComoResource(ruta);
    }

    /** Para el informe: la foto vigente recortada 3:4 (JPEG), o vacío si no hay. */
    @Transactional(readOnly = true)
    public Optional<byte[]> imagenParaInforme(Long idSubproceso) {
        try {
            Optional<String> ruta = rutaVigente(idSubproceso);
            if (ruta.isEmpty()) return Optional.empty();
            try (InputStream in = almacenamiento.cargarComoResource(ruta.get()).getInputStream()) {
                return Optional.of(recortar34(ImageIO.read(in)));
            }
        } catch (Exception e) {
            log.warn("No se pudo preparar la foto del evaluado del subproceso {} para el informe: {}", idSubproceso, e.getMessage());
            return Optional.empty();
        }
    }

    /** Recorta al centro en proporción vertical 3:4 (ancho:alto). */
    static byte[] recortar34(BufferedImage origen) throws IOException {
        if (origen == null) throw new IOException("Imagen ilegible");
        int w = origen.getWidth();
        int h = origen.getHeight();
        int cw = w;
        int ch = h;
        if (w * 4 > h * 3) cw = h * 3 / 4;   // demasiado ancha: se recortan los lados
        else ch = w * 4 / 3;                 // demasiado alta: se recortan arriba y abajo
        BufferedImage corte = origen.getSubimage((w - cw) / 2, (h - ch) / 2, cw, ch);
        BufferedImage rgb = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(corte, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", out);
        return out.toByteArray();
    }

    // ── internos ──

    private ServicioSubproceso subproceso(Long id, Usuario actor) {
        ServicioSubproceso sp = subprocesoRepository.findById(id).orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        AccesoGestor.validar(sp, actor);
        if (actor.getRol() == Rol.POLIGRAFISTA && !esElPoligrafista(sp, actor)) {
            throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
        }
        return sp;
    }

    private static boolean esElPoligrafista(ServicioSubproceso sp, Usuario actor) {
        return actor.getRol() == Rol.POLIGRAFISTA && sp.getUsuarioAsignado() != null && sp.getUsuarioAsignado().getIdUsuario().equals(actor.getIdUsuario());
    }

    private static boolean esCoordinador(Usuario actor) {
        return actor.getRol() == Rol.GESTOR || actor.getRol() == Rol.ADMIN_POLYGRAPH;
    }

    private static boolean cerrado(ServicioSubproceso sp) {
        return sp.getEstado() == EstadoSubproceso.FINALIZADO || sp.getEstado() == EstadoSubproceso.PUBLICADO;
    }

    /** PF-07: lo que el servidor exige del formato de poligrafía para enviarlo a revisión (aparte de los campos obligatorios de la plantilla).
     *  Solo aplica si la plantilla del subproceso tiene el campo ev_foto; vacío = nada falta. */
    @Transactional(readOnly = true)
    public List<String> faltantesParaEnviar(Long idSubproceso) {
        List<String> faltantes = new java.util.ArrayList<>();
        Boolean esFormato = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM servicio_subprocesos ss JOIN formulario_secciones s ON s.id_tipo_progreso = ss.id_tipo_progreso"
                        + " JOIN formulario_columnas c ON c.id_seccion = s.id_seccion WHERE ss.id = ? AND c.clave = 'ev_foto' AND c.activo AND s.activo)",
                Boolean.class, idSubproceso);
        if (!Boolean.TRUE.equals(esFormato)) return faltantes;
        Foto foto = vigente(idSubproceso);
        if (foto == null) faltantes.add("Foto del evaluado");
        else if (!foto.cedulaVerificada()) faltantes.add("Verificación de la cédula del evaluado");
        return faltantes;
    }

    private Foto vigente(Long idSubproceso) {
        return jdbc.query("SELECT id, nombre_usuario, fecha, verificacion_cedula, vigente, motivo_reemplazo, bloqueada_en, origen FROM fotos_evaluado_poligrafia WHERE id_servicio_subproceso = ? AND vigente",
                (rs, n) -> new Foto(rs.getLong("id"), rs.getString("nombre_usuario"), rs.getTimestamp("fecha").toLocalDateTime(), rs.getBoolean("verificacion_cedula"),
                        true, rs.getString("motivo_reemplazo"), rs.getTimestamp("bloqueada_en") == null ? null : rs.getTimestamp("bloqueada_en").toLocalDateTime(), rs.getString("origen")), idSubproceso)
                .stream().findFirst().orElse(null);
    }

    private Optional<String> rutaVigente(Long idSubproceso) {
        return jdbc.query("SELECT ruta FROM fotos_evaluado_poligrafia WHERE id_servicio_subproceso = ? AND vigente", (rs, n) -> rs.getString(1), idSubproceso).stream().findFirst();
    }

    private void insertar(Long idSubproceso, String ruta, Usuario actor, boolean cedula, String motivo, LocalDateTime bloqueadaEn, String origen) {
        String nombre = ((actor.getNombre() == null ? "" : actor.getNombre()) + " " + (actor.getApellido() == null ? "" : actor.getApellido())).trim();
        jdbc.update("INSERT INTO fotos_evaluado_poligrafia (id_servicio_subproceso, ruta, id_usuario, nombre_usuario, fecha, verificacion_cedula, motivo_reemplazo, bloqueada_en, origen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                idSubproceso, ruta, actor.getIdUsuario(), nombre, java.sql.Timestamp.valueOf(LocalDateTime.now(ZONA)), cedula, motivo,
                bloqueadaEn == null ? null : java.sql.Timestamp.valueOf(bloqueadaEn), origen);
    }

    /** CAMARA (por defecto) o ARCHIVO. */
    private static String origenValido(String origen) {
        return "ARCHIVO".equalsIgnoreCase(origen) ? "ARCHIVO" : "CAMARA";
    }

    /** Tipo REAL de la imagen según sus bytes ("jpg" o "png"), o null si no es una foto JPG/PNG. No se confía en la extensión ni en el tipo que declara el navegador. */
    static String tipoReal(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) return null;
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) return null;
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in);
                lector.getWidth(0);   // si la cabecera está rota, falla aquí y no se acepta
                String formato = lector.getFormatName().toLowerCase();
                if (formato.equals("jpeg") || formato.equals("jpg")) return "jpg";
                return formato.equals("png") ? "png" : null;
            } finally {
                lector.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Solo fotos JPG o PNG reales y de máximo 5 MB. El detalle técnico de un fallo va al log; el mensaje al usuario dice qué hacer. */
    private String guardarImagen(MultipartFile archivo, Long idSubproceso) {
        if (archivo == null || archivo.isEmpty()) throw new ApiException("No llegó la foto. Inténtalo de nuevo.", HttpStatus.BAD_REQUEST);
        if (archivo.getSize() > MAX_BYTES) throw new ApiException("La foto es demasiado pesada. Elige una de menos de 5 MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            log.error("No se pudo leer la foto del subproceso {}", idSubproceso, e);
            throw new ApiException("No se pudo guardar la foto. Revisa tu conexión e inténtalo de nuevo.", HttpStatus.BAD_REQUEST);
        }
        String tipo = tipoReal(bytes);
        if (tipo == null) throw new ApiException("El archivo debe ser una foto JPG o PNG.", HttpStatus.BAD_REQUEST);
        try {
            return almacenamiento.guardar(new FotoBytes(bytes, "foto." + tipo, "png".equals(tipo) ? "image/png" : "image/jpeg"), CATEGORIA, String.valueOf(idSubproceso));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la foto del subproceso {}", idSubproceso, e);
            throw new ApiException("No se pudo guardar la foto. Revisa tu conexión e inténtalo de nuevo.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** La foto ya validada, con un nombre propio según su tipo real. */
    private record FotoBytes(byte[] contenido, String nombre, String tipoContenido) implements MultipartFile {
        @Override public String getName() { return "foto"; }
        @Override public String getOriginalFilename() { return nombre; }
        @Override public String getContentType() { return tipoContenido; }
        @Override public boolean isEmpty() { return contenido.length == 0; }
        @Override public long getSize() { return contenido.length; }
        @Override public byte[] getBytes() { return contenido; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(contenido); }
        @Override public void transferTo(java.io.File destino) throws IOException { java.nio.file.Files.write(destino.toPath(), contenido); }
    }
}
