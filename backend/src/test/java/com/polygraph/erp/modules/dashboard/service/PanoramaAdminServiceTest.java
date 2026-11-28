package com.polygraph.erp.modules.dashboard.service;

import com.polygraph.erp.modules.dashboard.dto.PanoramaAdminResponse;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El panorama y el detalle arman muchas consultas JPQL: aquí corren todas contra la base de pruebas. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PanoramaAdminService.class, com.polygraph.erp.shared.service.DiasHabilesService.class, com.polygraph.erp.shared.service.TiempoServicioService.class,
        com.polygraph.erp.shared.service.CalendarioHabilService.class, com.polygraph.erp.shared.service.FestivoService.class,
        com.polygraph.erp.shared.service.ParametrosTiempoService.class, com.polygraph.erp.config.RelojConfig.class})
@DisplayName("Panorama del administrador — todas las consultas corren")
class PanoramaAdminServiceTest {

    @Autowired private PanoramaAdminService service;

    @Test
    @DisplayName("Arma el panorama de 7, 30 y 90 días con series completas y números coherentes")
    void armaElPanorama() {
        for (int dias : List.of(7, 30, 90)) {
            PanoramaAdminResponse r = service.obtener(dias);

            assertThat(r.dias()).isEqualTo(dias);
            assertThat(r.hasta()).isEqualTo(LocalDate.now());
            assertThat(r.solicitudes().porDia()).hasSize(dias);
            assertThat(r.solicitudes().porDia().get(dias - 1).fecha()).isEqualTo(LocalDate.now());
            assertThat(r.solicitudes().porEstado().values().stream().mapToLong(Long::longValue).sum())
                    .isEqualTo(r.solicitudes().creadas());
            assertThat(r.solicitudes().porcentajePublicadas()).isBetween(0, 100);
            assertThat(r.topProcesos().size()).isLessThanOrEqualTo(PanoramaAdminService.TOPE_RANKING);
            assertThat(r.clientes().prepago() + r.clientes().pospago()).isLessThanOrEqualTo(r.clientes().total());
            assertThat(r.clientes().activos()).isLessThanOrEqualTo(r.clientes().total());
            assertThat(r.sistema().procesosActivos()).isLessThanOrEqualTo(r.sistema().procesosTotal());
            assertThat(r.dinero().montoAprobadoCentavos()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("Un periodo que no es 7, 30 o 90 se rechaza")
    void periodoInvalido() {
        assertThatThrownBy(() -> service.obtener(15)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Cada tema del detalle corre su consulta y devuelve a lo sumo 8 filas")
    void detalleDeCadaTema() {
        List<String> temas = List.of("reversiones", "documentos", "novedades", "trabajos", "facturas", "pagos",
                "ciudades", "sinGestor", "vencidas", "porVencer", "estado-PENDIENTE", "estado-PUBLICADO", "estado-EN_EJECUCION");
        for (String tema : temas) {
            PanoramaAdminResponse.Detalle d = service.detalle(tema);
            assertThat(d.tema()).isEqualTo(tema);
            assertThat(d.items().size()).isLessThanOrEqualTo(PanoramaAdminService.TOPE_DETALLE);
            assertThat(d.total()).isGreaterThanOrEqualTo(d.items().size());
            assertThat(d.rutaVerTodo()).startsWith("/admin/");
        }
    }

    @Test
    @DisplayName("Un tema o estado desconocido da 404")
    void temaDesconocido() {
        assertThatThrownBy(() -> service.detalle("inventado")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.detalle("estado-NADA")).isInstanceOf(ApiException.class);
    }
}
