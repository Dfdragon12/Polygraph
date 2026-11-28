package com.polygraph.erp.modules.dashboard.service;

import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse;
import com.polygraph.erp.modules.tiemporeal.CanalCambios;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** El tablero del administrador arma ~25 consultas JPQL: aquí se ejecutan todas contra la base de
 *  pruebas, así un campo mal escrito falla en la suite y no en la pantalla del administrador. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DashboardAdminService.class, com.polygraph.erp.modules.servicios.service.ResumenTiempoService.class, CanalCambios.class, com.polygraph.erp.shared.service.DiasHabilesService.class, com.polygraph.erp.shared.service.TiempoServicioService.class,
        com.polygraph.erp.shared.service.CalendarioHabilService.class, com.polygraph.erp.shared.service.FestivoService.class,
        com.polygraph.erp.shared.service.ParametrosTiempoService.class, com.polygraph.erp.config.RelojConfig.class})
@DisplayName("Dashboard del administrador — todas las consultas corren")
class DashboardAdminServiceTest {

    @Autowired private DashboardAdminService service;

    @Test
    @DisplayName("Arma todas las secciones y los números son coherentes entre sí")
    void armaTodoElTablero() {
        DashboardAdminResponse r = service.obtenerDashboard();

        assertThat(r.generadoEn()).isNotNull();

        var s = r.servicios();
        assertThat(s.porEstado()).containsKeys("PENDIENTE", "PROGRAMANDO", "EN_EJECUCION", "FINALIZADO", "PUBLICADO", "CANCELADO");
        assertThat(s.porEstado().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(s.total());
        // en curso = pendiente + programando + en ejecución + reprogramado (mismo criterio que el Semáforo)
        assertThat(s.enCurso()).isEqualTo(s.porEstado().get("PENDIENTE") + s.porEstado().get("PROGRAMANDO") + s.porEstado().get("EN_EJECUCION")
                + s.porEstado().get("REPROGRAMADO"));
        // 14 días seguidos, el último es hoy, incluso los días sin solicitudes
        assertThat(s.porDia()).hasSize(14);
        assertThat(s.porDia().get(13).fecha()).isEqualTo(java.time.LocalDate.now());
        assertThat(s.nuevasSemana()).isGreaterThanOrEqualTo(s.nuevasHoy());
        assertThat(s.nuevasMes()).isGreaterThanOrEqualTo(0);

        var t = r.trabajo();
        assertThat(t.porEstado()).containsKeys("PENDIENTE", "EN_EJECUCION", "FINALIZADO", "PUBLICADO");
        assertThat(t.carga().size()).isLessThanOrEqualTo(6);

        var a = r.atencion();
        assertThat(a.novedadesViejas()).isLessThanOrEqualTo(a.novedadesAbiertas());
        assertThat(a.reversionesPendientes()).isGreaterThanOrEqualTo(0);

        var rg = r.riesgo();
        assertThat(rg.total()).isEqualTo(rg.bajo() + rg.medio() + rg.alto());

        var f = r.finanzas();
        assertThat(f.montoAprobadoMesCentavos()).isGreaterThanOrEqualTo(f.montoAprobadoHoyCentavos());
        assertThat(f.montoFacturasPendientes()).isNotNull();
        assertThat(f.montoFacturasVencidas()).isNotNull();

        var e = r.equipo();
        assertThat(e.internosPorRol()).containsKeys("GESTOR", "ANALISTA_INTERNO", "PROGRAMADOR", "POLIGRAFISTA", "VISITADOR", "ADMIN_POLYGRAPH");
        assertThat(e.conectadosInternos()).isZero(); // en la prueba nadie tiene la plataforma abierta
    }
}
