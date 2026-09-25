package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.GuardarCalificacionProcesoRequest;
import com.polygraph.erp.modules.catalogo.dto.GuardarCalificacionProcesoRequest.PesoSubproceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalificacionProcesoService — pesos de riesgo por subproceso en el catálogo")
class CalificacionProcesoServiceTest {

    @Mock private ProcesoRepository procesoRepository;
    @Mock private ProcesoTipoProgresoRepository pasoRepository;
    @Mock private NotificacionService notificacionService;

    @InjectMocks
    private CalificacionProcesoService service;

    private Proceso proceso;
    private List<ProcesoTipoProgreso> pasos;

    @BeforeEach
    void setUp() {
        proceso = Proceso.builder().idProceso(16).nombreProceso("AVANZADO").build();
        pasos = new ArrayList<>();
        pasos.add(paso(1, "Antecedentes", null, true, true));
        pasos.add(paso(2, "Poligrafía", null, true, true));
        pasos.add(paso(3, "Visita", null, true, true));
        lenient().when(procesoRepository.findById(16)).thenReturn(Optional.of(proceso));
        lenient().when(pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(16)).thenReturn(pasos);
    }

    @Test
    @DisplayName("Pesos que suman 100 → califica y sube la versión")
    void guardar_suma100_califica() {
        var r = service.guardar(16, req("CRITICO", "60", peso(1, "50"), peso(2, "30"), peso(3, "20")));

        assertThat(r.califica()).isTrue();
        assertThat(r.sumaPesos()).isEqualByComparingTo("100");
        assertThat(r.criticidadDefecto()).isEqualTo("CRITICO");
        assertThat(proceso.getVersionCalificacion()).isEqualTo(2);
        assertThat(proceso.getCoberturaMinima()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("Pesos que pasan de 100 → se rechaza y no se guarda nada del proceso")
    void guardar_pasaDe100_400() {
        assertThatThrownBy(() -> service.guardar(16, req("ESTANDAR", "100", peso(1, "50"), peso(2, "40"), peso(3, "15"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("105")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(proceso.getVersionCalificacion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Menos de 100 → se guarda, pero no califica y dice cuánto suma")
    void guardar_menosDe100_noCalifica() {
        var r = service.guardar(16, req("ESTANDAR", "100", peso(1, "50"), peso(2, "30"), peso(3, "15")));

        assertThat(r.califica()).isFalse();
        assertThat(r.motivoNoCalifica()).contains("95");
    }

    @Test
    @DisplayName("Un subproceso sin peso impide calificar aunque el resto sume 100")
    void guardar_unoSinPeso_noCalifica() {
        var r = service.guardar(16, req("ESTANDAR", "100", peso(1, "60"), peso(2, "40"), peso(3, null)));

        assertThat(r.califica()).isFalse();
        assertThat(r.motivoNoCalifica()).isEqualTo("1 subproceso sin peso");
    }

    @Test
    @DisplayName("Peso 0 es válido: el subproceso se hace pero no califica")
    void guardar_pesoCero_califica() {
        var r = service.guardar(16, req("ESTANDAR", "100", peso(1, "60"), peso(2, "40"), peso(3, "0")));

        assertThat(r.califica()).isTrue();
    }

    @Test
    @DisplayName("Un subproceso deshabilitado no cuenta en la suma ni exige peso")
    void deshabilitado_noCuenta() {
        pasos.get(2).setHabilitado(false);
        var r = service.guardar(16, req("ESTANDAR", "100", peso(1, "60"), peso(2, "40")));

        assertThat(r.califica()).isTrue();
        assertThat(r.subprocesos()).filteredOn(s -> s.idPaso() == 3).allMatch(s -> !s.cuenta());
    }

    @Test
    @DisplayName("Guardar sin cambios no sube la versión ni notifica")
    void guardar_sinCambios_noVersiona() {
        pasos.forEach(p -> p.setPeso(null));
        service.guardar(16, req("ESTANDAR", "100", peso(1, null), peso(2, null), peso(3, null)));

        assertThat(proceso.getVersionCalificacion()).isEqualTo(1);
        verify(notificacionService, never()).crearParaAdmins(eq("CATALOGO"), anyString(), anyString());
    }

    @Test
    @DisplayName("Un subproceso de otro proceso se rechaza")
    void guardar_pasoAjeno_400() {
        assertThatThrownBy(() -> service.guardar(16, req("ESTANDAR", "100", peso(99, "10"))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Habilitar un subproceso que hace pasar la suma de 100 se rechaza")
    void composicion_pasaDe100_400() {
        pasos.get(0).setPeso(new BigDecimal("60"));
        pasos.get(1).setPeso(new BigDecimal("40"));
        pasos.get(2).setPeso(new BigDecimal("20"));

        assertThatThrownBy(() -> service.trasCambioDeComposicion(proceso))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("120");
    }

    @Test
    @DisplayName("Quitar/deshabilitar un subproceso sube la versión de calificación")
    void composicion_subeVersion() {
        service.trasCambioDeComposicion(proceso);
        assertThat(proceso.getVersionCalificacion()).isEqualTo(2);
        assertThat(proceso.getCriticidadDefecto()).isEqualTo(CriticidadCargo.ESTANDAR);
    }

    // ── Apoyo ──

    private static ProcesoTipoProgreso paso(int id, String nombre, String peso, boolean habilitado, boolean activo) {
        TipoProgreso tipo = TipoProgreso.builder().idTipoProgreso(100 + id).nombreProgreso(nombre).activo(activo).build();
        return ProcesoTipoProgreso.builder().id(id).tipoProgreso(tipo).habilitado(habilitado).obligatorio(true)
                .peso(peso != null ? new BigDecimal(peso) : null).build();
    }

    private static PesoSubproceso peso(int idPaso, String peso) {
        return new PesoSubproceso(idPaso, peso != null ? new BigDecimal(peso) : null, null);
    }

    private static GuardarCalificacionProcesoRequest req(String criticidad, String cobertura, PesoSubproceso... pesos) {
        return new GuardarCalificacionProcesoRequest(criticidad, new BigDecimal(cobertura), List.of(pesos));
    }
}
