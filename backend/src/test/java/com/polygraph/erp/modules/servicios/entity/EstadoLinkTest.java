package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.EstadoServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EstadoLink — acceso del candidato a su link (V197)")
class EstadoLinkTest {

    private static LinkCandidato link(EstadoServicio estadoServicio) {
        return LinkCandidato.builder()
                .servicio(Servicio.builder().idServicio(1).estado(estadoServicio).build())
                .usado(false)
                .fechaExpiracion(LocalDateTime.now().minusDays(10)) // las 36 h ya no cuentan
                .build();
    }

    @Test
    @DisplayName("Sin enviar y con el servicio abierto: PENDIENTE, con acceso aunque hayan pasado las 36 h")
    void pendiente() {
        assertThat(EstadoLink.de(link(EstadoServicio.PROGRAMANDO))).isEqualTo(EstadoLink.PENDIENTE);
        assertThat(EstadoLink.PENDIENTE.conAcceso()).isTrue();
    }

    @Test
    @DisplayName("Enviado: USADO y SIGUE con acceso (edita, ve citas y documentos)")
    void enviadoSigueConAcceso() {
        LinkCandidato l = link(EstadoServicio.EN_EJECUCION);
        l.setUsado(true);
        assertThat(EstadoLink.de(l)).isEqualTo(EstadoLink.USADO);
        assertThat(EstadoLink.de(l).conAcceso()).isTrue();
    }

    @Test
    @DisplayName("Revocado por el gestor: sin acceso, aunque el servicio siga abierto")
    void revocado() {
        LinkCandidato l = link(EstadoServicio.PROGRAMANDO);
        l.setRevocado(true);
        assertThat(EstadoLink.de(l)).isEqualTo(EstadoLink.REVOCADO);
        assertThat(EstadoLink.de(l).conAcceso()).isFalse();
    }

    @Test
    @DisplayName("Servicio finalizado, publicado o cancelado: CERRADO, sin acceso")
    void servicioCerrado() {
        for (EstadoServicio e : new EstadoServicio[]{EstadoServicio.FINALIZADO, EstadoServicio.PUBLICADO, EstadoServicio.CANCELADO}) {
            assertThat(EstadoLink.de(link(e))).as(e.name()).isEqualTo(EstadoLink.CERRADO);
        }
    }

    @Test
    @DisplayName("Demasiados intentos fallidos: BLOQUEADO (tiene prioridad sobre el estado del servicio)")
    void bloqueado() {
        LinkCandidato l = link(EstadoServicio.PUBLICADO);
        l.setIntentosFallidos((short) 3);
        assertThat(EstadoLink.de(l)).isEqualTo(EstadoLink.BLOQUEADO);
    }
}
