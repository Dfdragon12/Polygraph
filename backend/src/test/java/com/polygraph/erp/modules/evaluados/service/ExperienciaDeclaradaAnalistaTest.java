package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.modules.evaluados.dto.GuardarTablaFijaRequest;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** LB-02: lo que el evaluado declaró como experiencia laboral no lo cambia el analista (esa fila la leen otros servicios de la misma persona). */
class ExperienciaDeclaradaAnalistaTest {

    private final CandidatoRepository candidatos = mock(CandidatoRepository.class);
    private final HojaVidaEvaluadoRepository hojas = mock(HojaVidaEvaluadoRepository.class);
    private final ServicioSubprocesoRepository subprocesos = mock(ServicioSubprocesoRepository.class);
    private final CandidatoHojaVidaService service = new CandidatoHojaVidaService(candidatos, hojas, subprocesos);

    private Usuario analista() {
        Usuario u = new Usuario();
        u.setIdUsuario(5L);
        u.setRol(Rol.ANALISTA_INTERNO);
        return u;
    }

    @Test
    @DisplayName("El analista no puede guardar la experiencia laboral declarada: 403 y no se toca nada")
    void analistaNoEditaExperiencia() {
        when(candidatos.findById(anyLong())).thenReturn(Optional.of(Candidato.builder().idCandidato(47L).build()));
        when(subprocesos.existsByServicio_Candidato_IdCandidatoAndUsuarioAsignado_IdUsuario(anyLong(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> service.guardarTabla(47L, "experiencia_laboral", new GuardarTablaFijaRequest(List.of()), analista()))
                .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(hojas, never()).save(any());
    }

    @Test
    @DisplayName("AC-02: el analista tampoco puede guardar los estudios declarados (educación): 403 y no se toca nada")
    void analistaNoEditaEducacion() {
        when(candidatos.findById(anyLong())).thenReturn(Optional.of(Candidato.builder().idCandidato(47L).build()));
        when(subprocesos.existsByServicio_Candidato_IdCandidatoAndUsuarioAsignado_IdUsuario(anyLong(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> service.guardarTabla(47L, "educacion", new GuardarTablaFijaRequest(List.of()), analista()))
                .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(hojas, never()).save(any());
    }
}
