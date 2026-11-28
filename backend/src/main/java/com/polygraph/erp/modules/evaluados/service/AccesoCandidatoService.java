package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.EstadoLink;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Entrada al portal del candidato con su número de documento (pedido del usuario 2026-10-14).
 *  El link solo no basta: quien lo abra debe escribir el documento del candidato de ese estudio.
 *  Si acierta recibe una llave de acceso (HMAC del token y el documento, nunca el documento) que el
 *  portal manda en el encabezado X-Acceso-Candidato de cada petición (AccesoCandidatoInterceptor).
 *  Cada documento equivocado suma un intento fallido; al tercero el link queda BLOQUEADO
 *  (EstadoLink.MAX_INTENTOS) y solo un link nuevo del gestor lo reabre. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccesoCandidatoService {

    public static final String ENCABEZADO = "X-Acceso-Candidato";

    private final LinkCandidatoRepository linkRepository;
    private final EvaluadoService evaluadoService;

    @Value("${app.jwt.secret}")
    private String secreto;

    public record Verificacion(String acceso) {}

    /** Compara el documento escrito con el del candidato. noRollbackFor: el intento fallido queda
     *  guardado aunque la respuesta sea un error. */
    @Transactional(noRollbackFor = ApiException.class)
    public Verificacion verificar(String token, String documento) {
        Servicio servicio = evaluadoService.servicioConAcceso(token); // 404/410 si no existe o no tiene acceso
        LinkCandidato link = linkRepository.findByToken(token).orElseThrow();
        String esperado = normalizar(cedulaDe(servicio));
        if (esperado.isEmpty()) {
            throw new ApiException("Este estudio no tiene el documento del candidato registrado. Comunícate con quien te envió el enlace.",
                    HttpStatus.CONFLICT);
        }
        if (!esperado.equals(normalizar(documento))) {
            short intentos = (short) ((link.getIntentosFallidos() == null ? 0 : link.getIntentosFallidos()) + 1);
            link.setIntentosFallidos(intentos);
            linkRepository.save(link);
            int quedan = EstadoLink.MAX_INTENTOS - intentos;
            log.warn("Documento equivocado en el link del servicio {} (intento {} de {})",
                    servicio.getIdServicio(), intentos, EstadoLink.MAX_INTENTOS);
            if (quedan <= 0) {
                throw new ApiException("Este enlace se bloqueó por seguridad después de " + EstadoLink.MAX_INTENTOS
                        + " intentos. Comunícate con quien te lo envió para que te genere uno nuevo.", HttpStatus.GONE);
            }
            throw new ApiException("El número de documento no coincide. Te " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos."),
                    HttpStatus.UNAUTHORIZED);
        }
        if (link.getIntentosFallidos() != null && link.getIntentosFallidos() > 0) {
            link.setIntentosFallidos((short) 0);
            linkRepository.save(link);
        }
        return new Verificacion(firma(token, esperado));
    }

    /** Lo llama el interceptor en cada petición del portal (salvo la de verificar). */
    @Transactional(readOnly = true)
    public void exigir(String token, String acceso) {
        Servicio servicio = evaluadoService.servicioConAcceso(token);
        String esperado = normalizar(cedulaDe(servicio));
        if (acceso == null || acceso.isBlank() || esperado.isEmpty()
                || !MessageDigest.isEqual(firma(token, esperado).getBytes(StandardCharsets.UTF_8), acceso.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException("Escribe tu número de documento para entrar.", HttpStatus.UNAUTHORIZED);
        }
    }

    private static String cedulaDe(Servicio servicio) {
        Candidato c = servicio.getCandidato();
        return c != null ? c.getCedula() : null;
    }

    /** Solo letras y números, en mayúscula: "1.019.122.167" = "1019122167", "ab-123" = "AB123". */
    static String normalizar(String documento) {
        return documento == null ? "" : documento.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String firma(String token, String documento) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] firma = mac.doFinal(("candidato:" + token + ":" + documento).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el acceso del candidato", e);
        }
    }
}
