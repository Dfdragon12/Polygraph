package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.EstadoServicio;

import java.util.Set;

/** ÚNICA regla del estado de un link del candidato — la usan el portal del candidato
 *  (EvaluadoService: ¿puede entrar?) y el gestor (LinkCandidatoGestorService: qué estado mostrar),
 *  así nunca se contradicen. Desde V197:
 *
 *    REVOCADO  — el gestor lo cerró a propósito.                    → sin acceso
 *    BLOQUEADO — demasiados intentos fallidos.                      → sin acceso
 *    CERRADO   — el servicio se finalizó, publicó o canceló.        → sin acceso
 *    USADO     — el candidato ya envió el formulario ("Enviado").   → SIGUE con acceso (edita, ve citas)
 *    PENDIENTE — activo, todavía sin enviar.                        → con acceso
 *
 *  Las 36 h de antes ya no aplican (fecha_expiracion queda como dato histórico). */
public enum EstadoLink {
    PENDIENTE, USADO, REVOCADO, BLOQUEADO, CERRADO;

    public static final short MAX_INTENTOS = 3;

    /** Estados del servicio en los que el candidato ya no tiene nada que hacer. */
    public static final Set<EstadoServicio> SERVICIO_CERRADO =
            Set.of(EstadoServicio.FINALIZADO, EstadoServicio.PUBLICADO, EstadoServicio.CANCELADO);

    public static EstadoLink de(LinkCandidato l) {
        if (Boolean.TRUE.equals(l.getRevocado())) return REVOCADO;
        if (l.getIntentosFallidos() != null && l.getIntentosFallidos() >= MAX_INTENTOS) return BLOQUEADO;
        Servicio s = l.getServicio();
        if (s != null && SERVICIO_CERRADO.contains(s.getEstado())) return CERRADO;
        return Boolean.TRUE.equals(l.getUsado()) ? USADO : PENDIENTE;
    }

    public boolean conAcceso() {
        return this == PENDIENTE || this == USADO;
    }
}
