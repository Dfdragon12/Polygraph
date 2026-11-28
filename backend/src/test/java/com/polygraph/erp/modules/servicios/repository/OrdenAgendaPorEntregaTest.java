package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** SE-08: la lista de la Agenda se ordena por lo que vence antes al cliente (fecha de compromiso ajustada del SERVICIO). Que el orden por una
 *  propiedad del servicio ejecuta en la base (listado paginado y con filtros) es lo que se verifica. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Agenda — orden por entrega al cliente")
class OrdenAgendaPorEntregaTest {

    @Autowired private ServicioSubprocesoRepository repository;

    @Test
    @DisplayName("Ordenar por servicio.fechaCompromisoAjustada ejecuta con los filtros de la Agenda")
    void ordenaPorLaEntregaDelServicio() {
        var spec = ServicioSubprocesoSpecifications.conFiltros(EstadoSubproceso.EN_EJECUCION, null, 1L, null, null);
        var pagina = repository.findAll(spec, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "servicio.fechaCompromisoAjustada")));
        assertThat(pagina).isNotNull();
        assertThat(repository.count(spec)).isGreaterThanOrEqualTo(0);
    }
}
