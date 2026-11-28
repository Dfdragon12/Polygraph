package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.service.FestivoAdminService.FestivoDto;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.FestivoService;
import com.polygraph.erp.shared.service.FestivosColombiaGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({FestivoAdminService.class, FestivoService.class})
@DisplayName("Festivos de Colombia que ajusta el administrador")
class FestivoAdminServiceTest {

    @Autowired private FestivoAdminService servicio;
    @Autowired private FestivoService festivos;

    private static final LocalDate NAVIDAD = LocalDate.of(2026, 12, 25);
    private static final LocalDate CIERRE = LocalDate.of(2026, 12, 31);

    private FestivoDto buscar(LocalDate fecha) {
        return servicio.listar(2026).stream().filter(f -> f.fecha().equals(fecha)).findFirst().orElse(null);
    }

    private boolean esFestivo(LocalDate fecha) {
        return festivos.activosDelAnio(fecha.getYear()).contains(fecha);
    }

    @Test
    @DisplayName("Un año sin guardar se calcula al vuelo; al listarlo se guardan los de la ley (AUTO) y activos")
    void deLey() {
        assertThat(esFestivo(NAVIDAD)).isTrue();   // antes de guardar: calculado
        assertThat(servicio.listar(2026)).hasSize(FestivosColombiaGenerator.generar(2026).size());
        FestivoDto navidad = buscar(NAVIDAD);
        assertThat(navidad.nombre()).isEqualTo("Navidad");
        assertThat(navidad.origen()).isEqualTo("AUTO");
        assertThat(navidad.activo()).isTrue();
        assertThat(servicio.listar(2026)).hasSize(FestivosColombiaGenerator.generar(2026).size());   // no duplica
        assertThat(esFestivo(NAVIDAD)).isTrue();
    }

    @Test
    @DisplayName("Desactivar un festivo de la ley lo quita del calendario hábil, y reactivarlo lo devuelve")
    void desactivarYReactivar() {
        servicio.cambiar(NAVIDAD, false, null, "admin@polygraph.co");
        assertThat(esFestivo(NAVIDAD)).isFalse();
        assertThat(buscar(NAVIDAD).activo()).isFalse();
        assertThat(servicio.listar(2026)).hasSize(FestivosColombiaGenerator.generar(2026).size());

        servicio.cambiar(NAVIDAD, true, null, "admin@polygraph.co");
        assertThat(esFestivo(NAVIDAD)).isTrue();
        assertThat(buscar(NAVIDAD).activo()).isTrue();
    }

    @Test
    @DisplayName("Un día no laborable propio se agrega con descripción (MANUAL), cuenta como festivo y se puede desactivar")
    void agregarPropio() {
        assertThat(esFestivo(CIERRE)).isFalse();
        servicio.cambiar(CIERRE, true, "Cierre de fin de año", "admin@polygraph.co");
        assertThat(esFestivo(CIERRE)).isTrue();
        FestivoDto propio = buscar(CIERRE);
        assertThat(propio.origen()).isEqualTo("MANUAL");
        assertThat(propio.nombre()).isEqualTo("Cierre de fin de año");

        servicio.cambiar(CIERRE, false, null, "admin@polygraph.co");
        assertThat(esFestivo(CIERRE)).isFalse();
        assertThat(buscar(CIERRE).activo()).isFalse();
    }

    @Test
    @DisplayName("Agregar un día propio exige descripción, y el año debe ser razonable")
    void validaciones() {
        assertThatThrownBy(() -> servicio.cambiar(CIERRE, true, " ", "admin")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> servicio.cambiar(CIERRE, false, null, "admin")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> servicio.listar(1999)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("La carga inicial guarda 2025–2030 sin duplicar si se repite")
    void cargaInicial() {
        festivos.cargaInicial();
        festivos.cargaInicial();
        for (int anio = 2025; anio <= 2030; anio++) assertThat(servicio.listar(anio)).as("año %d", anio).hasSize(FestivosColombiaGenerator.generar(anio).size());
    }
}
