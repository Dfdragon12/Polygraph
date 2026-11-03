package com.polygraph.erp.modules.gestor.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** PF-09 · Reglas puras del enlace del proveedor externo (sin Spring ni base de datos): cómo se arma y se guarda el token,
 *  en qué estado está un enlace y, sobre todo, a qué rutas de la API da acceso. Es lo que más importa de la seguridad,
 *  por eso vive aparte y con pruebas: un enlace abre SOLO lo que necesita la pantalla de la evaluación a la que pertenece. */
public final class ReglasEnlaceProveedor {

    /** Vigencia máxima del enlace; antes vence al enviar el informe. */
    public static final Duration VIGENCIA = Duration.ofHours(72);

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private ReglasEnlaceProveedor() {}

    /** 256 bits aleatorios en texto seguro para una URL. */
    public static String nuevoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lo único que se guarda del token. */
    public static String huella(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un token bien formado: solo el alfabeto que generamos y un largo razonable (evita procesar basura). */
    public static boolean tokenBienFormado(String token) {
        return token != null && token.length() >= 20 && token.length() <= 100 && token.matches("[A-Za-z0-9_-]+");
    }

    public enum Estado { VIGENTE, VENCIDO, REVOCADO, ENVIADO, REEMPLAZADO }

    public static Estado estado(OffsetDateTime expiraEn, OffsetDateTime revocadoEn, OffsetDateTime cerradoEn, String motivoCierre, OffsetDateTime ahora) {
        if (revocadoEn != null) return Estado.REVOCADO;
        if (cerradoEn != null) return "ENVIADO".equals(motivoCierre) ? Estado.ENVIADO : Estado.REEMPLAZADO;
        return expiraEn.isAfter(ahora) ? Estado.VIGENTE : Estado.VENCIDO;
    }

    private static final String FORMULARIO = "/api/v1/gestor/formularios/subproceso/";
    private static final Pattern TRABAJO = Pattern.compile("/api/v1/trabajos/[0-9a-fA-F-]{36}(/resultado)?");

    /** Rutas (método + ruta exacta) que abre un enlace de la evaluación {@code idSubproceso}. Todo lo demás es 403. */
    public static boolean rutaPermitida(String metodo, String ruta, long idSubproceso) {
        if (metodo == null || ruta == null || ruta.contains("%") || ruta.contains(";") || ruta.contains("..") || ruta.contains("//")) return false;
        String base = FORMULARIO + idSubproceso;
        // La evaluación en sí: leer y guardar el formulario, el historial de preguntas y la ficha del candidato.
        List<String> lecturas = List.of(base, base + "/preguntas-historial", base + "/candidato", base + "/archivo", base + "/foto", base + "/foto/imagen");
        List<String> escrituras = List.of(base + "/documento-pdf", base + "/archivo", base + "/foto", base + "/foto/bloquear");
        switch (metodo) {
            case "GET":
                return lecturas.contains(ruta)
                        || ruta.matches(Pattern.quote(base + "/soportes/") + "[0-9]+")
                        || ruta.equals("/api/v1/catalogo/frases-estandar")
                        || ruta.equals("/api/v1/proveedor/sesion")
                        || TRABAJO.matcher(ruta).matches();
            case "PUT":
                return ruta.equals(base);
            case "POST":
                return escrituras.contains(ruta);
            case "PATCH":
                return ruta.equals("/api/v1/gestor/asignaciones/" + idSubproceso + "/completar");
            default:
                return false;
        }
    }
}
