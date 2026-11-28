package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.formularios.service.ConclusionFormatoService;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** PC-02: al enviar a revisión la conclusión es obligatoria, no dice «Favorable/Desfavorable» y queda copiada donde la lee la ventana Publicar. */
@ExtendWith(MockitoExtension.class)
@DisplayName("PC-02 — enviar a revisión exige la conclusión")
class PC02EnviarConclusionTest {

    @Mock private FormularioSeccionRepository formularioSeccionRepository;
    @Mock private ConclusionFormatoService conclusionFormato;

    @InjectMocks
    private AsignacionService service;

    private ServicioSubproceso sp;

    @BeforeEach
    void setUp() {
        TipoProgreso tipo = TipoProgreso.builder().idTipoProgreso(5).nombreProgreso("Antecedentes").rolResponsable(Rol.ANALISTA_INTERNO).build();
        sp = ServicioSubproceso.builder().id(500L).tipoProgreso(tipo).estado(EstadoSubproceso.EN_EJECUCION).build();
        when(formularioSeccionRepository.existsByTipoProgreso_IdTipoProgreso(5)).thenReturn(true);
    }

    @Test
    @DisplayName("Sin conclusión no se envía; se dice qué falta")
    void sinConclusionNoSeEnvia() {
        when(conclusionFormato.efectiva(sp)).thenReturn(null);
        assertThatThrownBy(() -> service.exigirConclusion(sp))
                .isInstanceOf(ApiException.class).hasMessageContaining("Escribe la conclusión")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(sp.getConclusion()).isNull();
    }

    @Test
    @DisplayName("Con «FAVORABLE» o «desfavorable» no se envía: se pide quitar la palabra")
    void conFavorableNoSeEnvia() {
        when(conclusionFormato.efectiva(sp)).thenReturn("Se emite concepto FAVORABLE para el cargo de Conductor de reparto.");
        assertThatThrownBy(() -> service.exigirConclusion(sp))
                .isInstanceOf(ApiException.class).hasMessageContaining("Favorable").hasMessageContaining("Quita esa palabra")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        when(conclusionFormato.efectiva(sp)).thenReturn("Resultado desfavorable.");
        assertThatThrownBy(() -> service.exigirConclusion(sp)).isInstanceOf(ApiException.class);
        assertThat(sp.getConclusion()).isNull();
    }

    @Test
    @DisplayName("Con una conclusión válida queda copiada en el subproceso: la ventana Publicar y el historial leen un solo lugar")
    void laConclusionQuedaCopiada() {
        when(conclusionFormato.efectiva(sp)).thenReturn("Con base en las consultas realizadas no registra antecedentes.");
        service.exigirConclusion(sp);
        assertThat(sp.getConclusion()).isEqualTo("Con base en las consultas realizadas no registra antecedentes.");
    }

    @Test
    @DisplayName("Un tipo de subproceso sin plantilla no tiene dónde escribir la conclusión: no se le exige")
    void sinPlantillaNoSeExige() {
        when(formularioSeccionRepository.existsByTipoProgreso_IdTipoProgreso(5)).thenReturn(false);
        service.exigirConclusion(sp);
        assertThat(sp.getConclusion()).isNull();
    }
}
