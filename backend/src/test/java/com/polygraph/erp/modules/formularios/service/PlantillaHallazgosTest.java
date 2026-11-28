package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.CalificacionColumnaDto;
import com.polygraph.erp.modules.formularios.dto.CalificacionColumnaDto.HallazgoOpcion;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.ColumnaRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.SeccionRequest;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.dto.HallazgoCatalogoDtos.HallazgoRequest;
import com.polygraph.erp.modules.riesgo.service.CatalogoHallazgosService;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catálogo de hallazgos + constructor: el admin crea hallazgos con su área, marca en la plantilla
 *  qué campo los aplica (y qué opción genera cuál) y el cálculo usa los puntos del catálogo al
 *  momento — cambiar 40 → 35 se ve de inmediato. Una poligrafía sirve de ejemplo de otro servicio. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FormularioPlantillaService.class, CandidatoInfoService.class, CatalogoHallazgosService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class})
@DisplayName("Catálogo de hallazgos y campos de hallazgo en la plantilla")
class PlantillaHallazgosTest {

    @Autowired private FormularioPlantillaService plantillaService;
    @Autowired private CatalogoHallazgosService catalogo;
    @Autowired private CalculoSubservicioService calculo;
    @Autowired private com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository seccionRepository;
    @Autowired private EntityManager em;
    @MockBean private FormularioDocumentoService documentoService;
    @MockBean private FormularioPlantillaWordService plantillaWordService;
    @MockBean private AlmacenamientoDocumentosService almacenamientoService;

    private TipoProgreso poligrafia;

    @BeforeEach
    void preparar() {
        poligrafia = TipoProgreso.builder().nombreProgreso("Poligrafía de rutina").activo(true).rolResponsable(Rol.POLIGRAFISTA)
                .requiereDocumento(false).versionPlantilla(1).build();
        em.persist(poligrafia);
        PlantillaAntecedentesPrueba.UMBRALES.forEach((c, u) -> em.persist(com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo.builder()
                .criticidad(c).limiteBajo(u.limiteBajo()).limiteMedio(u.limiteMedio()).minimoMedio(u.minimoMedio()).build()));
        catalogo.crear(new HallazgoRequest("POL-01", "Reacción significativa en consumo de sustancias", "PUNTOS", 40, false, false, true));
        catalogo.crear(new HallazgoRequest("POL-02", "Admite hurto en el empleo anterior", "CRITICO", null, false, false, true));
        catalogo.crear(new HallazgoRequest("VIS-01", "Vivienda no corresponde a la declarada", "PUNTOS", 20, false, false, true));
        catalogo.crear(new HallazgoRequest("GEN-01", "Inconsistencia en la información entregada", "PUNTOS", 10, false, false, true));
        em.flush();
    }

    private static ColumnaRequest columna(String nombre, String clave, String tipo, List<String> opciones, CalificacionColumnaDto cal) {
        return new ColumnaRequest(null, nombre, tipo, null, null, clave, opciones, false, null, null, null, null, null, cal);
    }

    private static ColumnaRequest calculada(String nombre, String clave, String calculo) {
        return new ColumnaRequest(null, nombre, "TEXTO", null, null, clave, null, false, null, null, true, null, null,
                new CalificacionColumnaDto(null, calculo, null, null, null, null, null));
    }

    private void guardar(List<SeccionRequest> secciones) {
        plantillaService.guardar(poligrafia.getIdTipoProgreso(), new GuardarPlantillaRequest(secciones));
        em.flush();
        em.clear();
    }

    private void plantillaPoligrafia() {
        guardar(List.of(
                new SeccionRequest(null, "Resultado", "CAMPOS", "resultado", null, null, List.of(
                        calculada("Puntaje", "puntaje", "PUNTAJE"), calculada("Nivel", "nivel", "NIVEL")), null),
                new SeccionRequest(null, "Preguntas", "TABLA", "preguntas", null, null, List.of(
                        columna("Pregunta", "pregunta", "TEXTO", null, null),
                        columna("Hallazgo", "hallazgo", "LISTA", List.of("—", "POL-01", "POL-02", "GEN-01"),
                                new CalificacionColumnaDto("HALLAZGO", null, null, null, "Poligrafía", "{pregunta}", null))), null),
                new SeccionRequest(null, "Cierre", "CAMPOS", "cierre", null, null, List.of(
                        columna("¿Coincide con lo declarado?", "coincide", "LISTA", List.of("Sí", "No"),
                                new CalificacionColumnaDto(null, null, null, null, null, null, List.of(new HallazgoOpcion("No", "GEN-01"))))), null)));
    }

    @Test
    @DisplayName("El catálogo no se parte por rol: lo que sale lo define la plantilla")
    void catalogoCompleto() {
        assertThat(catalogo.listar(true)).extracting(h -> h.codigo()).contains("POL-01", "POL-02", "VIS-01", "GEN-01");
    }

    @Test
    @DisplayName("Plantilla con campo de hallazgo: califica y cambiar los puntos se usa de inmediato")
    void calculaConElCatalogo() {
        plantillaPoligrafia();
        assertThat(em.find(TipoProgreso.class, poligrafia.getIdTipoProgreso()).getGrupoCalificacion()).isEqualTo("HALLAZGOS");

        var secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(poligrafia.getIdTipoProgreso());
        Map<String, String> campos = Map.of("coincide", "No");
        var tablas = Map.of("preguntas", List.<Map<String, String>>of(Map.of("pregunta", "¿Ha consumido drogas?", "hallazgo", "POL-01")));
        // Sin campo de criticidad en la plantilla: usa la del estudio.
        var c = calculo.calcular(secciones, CriticidadCargo.ESTANDAR, campos, tablas).orElseThrow();
        assertThat(c.resultado().formula()).isEqualTo("40 + 10 = 50 de 100 posibles");
        assertThat(c.resultado().hallazgos().get(0).detalle()).isEqualTo("¿Ha consumido drogas?");
        assertThat(c.campos()).containsEntry("puntaje", "50,0").containsEntry("nivel", "MEDIO");

        var pol01 = catalogo.listar(false).stream().filter(h -> h.codigo().equals("POL-01")).findFirst().orElseThrow();
        catalogo.actualizar(pol01.id(), new HallazgoRequest("POL-01", pol01.descripcion(), "PUNTOS", 35, false, false, true));
        em.flush();
        c = calculo.calcular(secciones, CriticidadCargo.ESTANDAR, campos, tablas).orElseThrow();
        assertThat(c.resultado().formula()).isEqualTo("35 + 10 = 45 de 100 posibles");
    }

    @Test
    @DisplayName("Un hallazgo en uso no se borra (se desactiva) y el código no cambia")
    void enUso() {
        plantillaPoligrafia();
        var pol01 = catalogo.listar(false).stream().filter(h -> h.codigo().equals("POL-01")).findFirst().orElseThrow();
        assertThat(pol01.enUso()).isTrue();
        assertThatThrownBy(() -> catalogo.eliminar(pol01.id())).isInstanceOf(ApiException.class).hasMessageContaining("desactívalo");
        assertThatThrownBy(() -> catalogo.actualizar(pol01.id(),
                new HallazgoRequest("POL-09", "x", "PUNTOS", 1, false, false, true)))
                .isInstanceOf(ApiException.class).hasMessageContaining("no se puede cambiar");
        var vis01 = catalogo.listar(false).stream().filter(h -> h.codigo().equals("VIS-01")).findFirst().orElseThrow();
        catalogo.eliminar(vis01.id());
        assertThat(catalogo.listar(false)).extracting(h -> h.codigo()).doesNotContain("VIS-01");
    }

    @Test
    @DisplayName("Máximo de caracteres: se guarda en campos de texto y no puede pasar de 10.000")
    void longitudMaxima() {
        var texto = new ColumnaRequest(null, "Observación", "TEXTO", null, null, "obs", null, false, null, null, null, null, null, null, 500);
        guardar(List.of(new SeccionRequest(null, "S", "CAMPOS", "s", null, null, List.of(texto), null)));
        var columna = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(poligrafia.getIdTipoProgreso()).get(0).getColumnas().get(0);
        assertThat(columna.getLongitudMaxima()).isEqualTo(500);
        assertThat(columna.longitudMaximaEfectiva()).isEqualTo(500);

        var exagerado = new ColumnaRequest(null, "Observación", "TEXTO", null, null, "obs", null, false, null, null, null, null, null, null, 20000);
        assertThatThrownBy(() -> guardar(List.of(new SeccionRequest(null, "S", "CAMPOS", "s", null, null, List.of(exagerado), null))))
                .isInstanceOf(ApiException.class).hasMessageContaining("entre 1 y 10.000");
    }

    @Test
    @DisplayName("El constructor no deja usar un código que no está en el catálogo, ni dos criticidades")
    void validaciones() {
        assertThatThrownBy(() -> guardar(List.of(new SeccionRequest(null, "S", "CAMPOS", "s", null, null, List.of(
                columna("Ref", "ref", "LISTA", List.of("—", "XXX-99"), new CalificacionColumnaDto("HALLAZGO", null, null, null, null, null, null))), null))))
                .isInstanceOf(ApiException.class).hasMessageContaining("XXX-99 no está en el catálogo");
        var criticidad = new CalificacionColumnaDto("CRITICIDAD", null, null, null, null, null, null);
        assertThatThrownBy(() -> guardar(List.of(new SeccionRequest(null, "S", "CAMPOS", "s", null, null, List.of(
                columna("Criticidad", "crit1", "LISTA", List.of("Estándar", "Sensible", "Crítico"), criticidad),
                columna("Criticidad 2", "crit2", "LISTA", List.of("Estándar", "Sensible", "Crítico"), criticidad)), null))))
                .isInstanceOf(ApiException.class).hasMessageContaining("Solo puede haber un campo de criticidad");
    }
}
