package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los filtros de "Por hacer" (documentos por revisar, devueltos, novedades, por vencer) usan
 *  subconsultas: esta prueba las ejecuta contra la base real de pruebas para asegurar que el JPQL
 *  que genera Hibernate es válido, tanto en el listado (con paginación) como en el conteo. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Mis Asignaciones — filtros por indicador ejecutan en la base")
class ServicioSubprocesoIndicadoresTest {

    @Autowired private ServicioSubprocesoRepository repository;

    @Test
    @DisplayName("Cada indicador corre en listado paginado y en conteo, y un indicador desconocido no filtra")
    void cadaIndicadorEjecuta() {
        for (String indicador : List.of(
                ServicioSubprocesoSpecifications.IND_POR_REVISAR, ServicioSubprocesoSpecifications.IND_DEVUELTOS,
                ServicioSubprocesoSpecifications.IND_NOVEDADES, ServicioSubprocesoSpecifications.IND_POR_VENCER)) {
            var spec = ServicioSubprocesoSpecifications.conFiltros(EstadoSubproceso.EN_EJECUCION, null, 1L, null, null)
                    .and(ServicioSubprocesoSpecifications.conIndicador(indicador, 1L));
            assertThat(repository.findAll(spec, PageRequest.of(0, 10))).isNotNull();
            assertThat(repository.count(spec)).isGreaterThanOrEqualTo(0);
        }
        assertThat(ServicioSubprocesoSpecifications.conIndicador("inexistente", 1L)).isNull();
        assertThat(ServicioSubprocesoSpecifications.conIndicador(null, 1L)).isNull();
    }
}
