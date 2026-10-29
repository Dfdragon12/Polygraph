package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Entrada al portal del candidato con su número de documento (pedido del usuario 2026-10-14). */
@DisplayName("Acceso al portal del candidato con su documento")
class AccesoCandidatoServiceTest {

    private static final String TOKEN = "abc123";
    private LinkCandidatoRepository linkRepository;
    private EvaluadoService evaluadoService;
    private AccesoCandidatoService service;
    private LinkCandidato link;

    @BeforeEach
    void setUp() {
        linkRepository = mock(LinkCandidatoRepository.class);
        evaluadoService = mock(EvaluadoService.class);
        service = new AccesoCandidatoService(linkRepository, evaluadoService);
        ReflectionTestUtils.setField(service, "secreto", "clave-de-prueba-de-al-menos-32-caracteres");
        Servicio servicio = Servicio.builder().idServicio(7).candidato(Candidato.builder().cedula("1019122167").build()).build();
        link = LinkCandidato.builder().token(TOKEN).servicio(servicio).intentosFallidos((short) 0).build();
        when(evaluadoService.servicioConAcceso(TOKEN)).thenReturn(servicio);
        when(linkRepository.findByToken(TOKEN)).thenReturn(Optional.of(link));
        when(linkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Documento correcto (con puntos o espacios): entrega una llave que sirve en las demás peticiones")
    void documentoCorrecto() {
        String acceso = service.verificar(TOKEN, " 1.019.122.167 ").acceso();
        assertThat(acceso).isNotBlank().doesNotContain("1019122167");
        assertThatCode(() -> service.exigir(TOKEN, acceso)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Documento equivocado: avisa cuántos intentos quedan y al tercero bloquea el link")
    void documentoEquivocado() {
        assertThatThrownBy(() -> service.verificar(TOKEN, "123"))
                .isInstanceOf(ApiException.class).hasMessageContaining("quedan 2 intentos");
        assertThatThrownBy(() -> service.verificar(TOKEN, "123"))
                .isInstanceOf(ApiException.class).hasMessageContaining("queda 1 intento");
        assertThatThrownBy(() -> service.verificar(TOKEN, "123"))
                .isInstanceOf(ApiException.class).hasMessageContaining("se bloqueó")
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.GONE);
        assertThat(link.getIntentosFallidos()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("Acertar después de un error vuelve a cero los intentos")
    void aciertoReiniciaIntentos() {
        assertThatThrownBy(() -> service.verificar(TOKEN, "999")).isInstanceOf(ApiException.class);
        service.verificar(TOKEN, "1019122167");
        assertThat(link.getIntentosFallidos()).isZero();
    }

    @Test
    @DisplayName("Sin llave, o con la de otro link, no se entra")
    void sinLlave() {
        assertThatThrownBy(() -> service.exigir(TOKEN, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.exigir(TOKEN, "inventada")).isInstanceOf(ApiException.class);
        verify(linkRepository, never()).save(any());
    }
}
