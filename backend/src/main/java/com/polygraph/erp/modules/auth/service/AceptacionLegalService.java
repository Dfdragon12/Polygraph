package com.polygraph.erp.modules.auth.service;

import com.polygraph.erp.modules.auth.entity.AceptacionLegal;
import com.polygraph.erp.modules.auth.entity.AceptacionLegal.Tipo;
import com.polygraph.erp.modules.auth.repository.AceptacionLegalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** Guarda la evidencia de cada aceptación de textos legales (Ley 1581: autorización demostrable). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AceptacionLegalService {

    /** Versión de los textos vigentes; debe coincidir con VERSION_LEGAL del frontend (config/empresa.js). */
    public static final String VERSION_TEXTOS = "1.0";

    private final AceptacionLegalRepository repository;

    @Value("${app.jwt.secret}")
    private String sal;

    /** Registra términos y privacidad (obligatorios) y, aparte, el marketing con su respuesta real. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarRegistro(Long idUsuario, String email, String versionTexto, String ip,
                                  boolean marketing) {
        String version = versionOValida(versionTexto);
        registrar(Tipo.TERMINOS, idUsuario, email, null, version, true, ip);
        registrar(Tipo.PRIVACIDAD, idUsuario, email, null, version, true, ip);
        registrar(Tipo.MARKETING, idUsuario, email, null, version, marketing, ip);
    }

    @Transactional
    public void registrarAutorizacionCandidato(Long idCandidato, Integer idServicio, String ip) {
        registrar(Tipo.AUTORIZACION_CANDIDATO, null, null,
                "candidato:" + idCandidato + ";servicio:" + idServicio, VERSION_TEXTOS, true, ip);
    }

    private void registrar(Tipo tipo, Long idUsuario, String email, String referencia,
                           String version, boolean aceptado, String ip) {
        repository.save(AceptacionLegal.builder()
                .tipo(tipo).idUsuario(idUsuario).email(email).referencia(referencia)
                .versionTexto(version).aceptado(aceptado).fecha(LocalDateTime.now())
                .ipHash(hash(ip)).build());
        log.info("Aceptación legal registrada: {} v{} aceptado={}", tipo, version, aceptado);
    }

    private static String versionOValida(String v) {
        return v == null || v.isBlank() || v.length() > 20 ? VERSION_TEXTOS : v.trim();
    }

    /** SHA-256 con sal: sirve como evidencia sin guardar la IP en claro. */
    private String hash(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((sal + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            return null;
        }
    }
}
