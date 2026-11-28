package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.Hallazgo;
import com.polygraph.erp.modules.riesgo.hallazgos.MotorHallazgos;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Casos C1–C5 de docs/antecedentes/casos_prueba.py con la plantilla REAL de V207 (campos de
 *  hallazgo) y el catálogo REAL de V202: deben dar lo mismo que motor_ant.py. Si una prueba no da,
 *  se corrige el motor o la plantilla, no el valor esperado. Datos inventados. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Antecedentes por hallazgos = motor_ant.py")
class AntecedentesHallazgosTest {

    private static final String NOTA_TRANSITO = "Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos.";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private FormularioSeccionRepository seccionRepository;
    @Autowired private CodigoCalificacionRepository codigoRepository;

    private List<FormularioSeccion> secciones;
    private CalculoSubservicioService calculo;

    @BeforeEach
    void preparar() throws Exception {
        TipoProgreso t = PlantillaAntecedentesPrueba.aplicarV207(jdbc, em);
        secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(t.getIdTipoProgreso());
        var catalogo = CalculoSubservicioService.catalogoDe(codigoRepository.findAll());
        calculo = new CalculoSubservicioService(() -> catalogo, () -> PlantillaAntecedentesPrueba.UMBRALES);
    }

    private CalculoFormulario calcular(Map<String, String> f, Map<String, List<Map<String, String>>> t) {
        return calculo.calcular(secciones, null, f, t).orElseThrow();
    }

    private static Map<String, String> campos(String criticidad, String conduccion) {
        Map<String, String> f = new HashMap<>(Map.of("ev_criticidad", criticidad, "ev_conduccion", conduccion, "ev_estado_doc", "Vigente",
                "dec1_coincide", "Sí", "dec2_coincide", "Sí", "dec3_coincide", "Sí"));
        return f;
    }

    private static Map<String, String> fila(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("La plantilla califica por hallazgos")
    void califica() {
        assertThat(MotorHallazgos.califica(CalculoSubservicioService.plantilla(secciones))).isTrue();
    }

    @Test
    @DisplayName("C1 Limpio (Estándar)")
    void c1() {
        CalculoFormulario c = calcular(campos("Estándar", "No"), Map.of());
        assertThat(c.resultado().puntajeTexto()).isEqualTo("0,0");
        assertThat(c.resultado().nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(c.resultado().formula()).isEqualTo("Sin hallazgos que sumen puntos");
        assertThat(c.resultado().hallazgos()).isEmpty();
        assertThat(c.campos()).containsEntry("sem_bajo", "▲ 0,0 puntos").containsEntry("sem_medio", "").containsEntry("sem_alto", "")
                .containsEntry("transito_nota", NOTA_TRANSITO).containsEntry("alertas_id_nota", "Sin alertas reportadas.")
                .containsEntry("procesos_nota", "No se encontraron procesos judiciales en Rama Judicial.")
                .containsEntry("procesos_priv_nota", "No se encontraron procesos en bases privadas.")
                .containsEntry("srv_puntaje", "0,0");
        assertThat(c.tablas().get("hallazgos")).isEmpty();
    }

    @Test
    @DisplayName("C2 Archivados + RNMC repetida + SIMIT sin conducción (Estándar)")
    void c2() {
        Map<String, String> f = campos("Estándar", "No");
        f.put("simit_ref", "LEG-17");
        f.put("simit_multas", "2 pendientes");
        var t = Map.of(
                "listas_nacionales", List.of(
                        fila("ln_fuente", "Medidas correctivas (RNMC)", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 2 (2021)"),
                        fila("ln_fuente", "Medidas correctivas (RNMC)", "ln_ref", "LEG-18", "ln_observacion", "Multa tipo 1 (2019), en proceso")),
                "procesos_privados", List.of(
                        fila("pp_delito", "Hurto", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19"),
                        fila("pp_delito", "Violencia intrafamiliar", "pp_estado", "Inactivo", "pp_calificacion", "LEG-19")));
        CalculoFormulario c = calcular(f, t);

        var r = c.resultado();
        assertThat(r.formula()).isEqualTo("30 + 15 + 15 = 60 de 100 posibles");
        assertThat(r.puntajeTexto()).isEqualTo("60,0");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(r.hallazgos()).containsExactly(
                new Hallazgo("LEG-18", "Medida correctiva registrada (RNMC), en cualquier estado", "Medidas correctivas (RNMC)", "Multa tipo 2 (2021)", "30"),
                new Hallazgo("LEG-19", "Proceso penal terminado sin condena (archivo, prescripción, desistimiento, conciliación o principio de oportunidad)",
                        "Bases privadas", "Proceso N° 1: Hurto (Inactivo)", "15"),
                new Hallazgo("LEG-19", "Proceso penal terminado sin condena (archivo, prescripción, desistimiento, conciliación o principio de oportunidad)",
                        "Bases privadas", "Proceso N° 2: Violencia intrafamiliar (Inactivo)", "15"));
        assertThat(r.avisos()).containsExactly("LEG-17 ignorado: el cargo no implica conducción");
        assertThat(c.avisos()).extracting(a -> a.texto()).containsExactly("LEG-17 no suma porque el cargo no implica conducción.");
        assertThat(c.campos()).containsEntry("sem_medio", "▲ 60,0 puntos").containsEntry("procesos_priv_nota", "")
                .containsEntry("transito_nota", NOTA_TRANSITO);
        assertThat(c.tablas().get("procesos_privados")).extracting(x -> x.get("pp_numero")).containsExactly("1", "2");
        assertThat(c.tablas().get("hallazgos")).hasSize(3).first()
                .isEqualTo(Map.of("h_ref", "LEG-18", "h_descripcion", "Medida correctiva registrada (RNMC), en cualquier estado",
                        "h_fuente", "Medidas correctivas (RNMC)", "h_detalle", "Multa tipo 2 (2021)", "h_puntos", "30"));
    }

    @Test
    @DisplayName("C3 Crítico: sanción disciplinaria vigente (Sensible)")
    void c3() {
        var t = Map.of(
                "listas_nacionales", List.of(fila("ln_fuente", "Procuraduría", "ln_ref", "LEG-09", "ln_observacion", "Suspensión vigente hasta 2027")),
                "procesos", List.of(fila("p_delito", "Hurto", "p_estado", "Terminado", "p_calificacion", "LEG-21")));
        CalculoFormulario c = calcular(campos("Sensible", "No"), t);
        var r = c.resultado();
        assertThat(r.formula()).isEqualTo("Hallazgo crítico (LEG-09): nivel ALTO");
        assertThat(r.puntajeTexto()).isEqualTo("100,0");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(r.hallazgos()).extracting(Hallazgo::ref, Hallazgo::puntos).containsExactly(
                org.assertj.core.groups.Tuple.tuple("LEG-09", "Crítico"), org.assertj.core.groups.Tuple.tuple("LEG-21", "20"));
        assertThat(r.hallazgos().get(1).detalle()).isEqualTo("Proceso N° 1: Hurto (Terminado)");
        assertThat(c.campos()).containsEntry("sem_alto", "▲ Hallazgo crítico").containsEntry("procesos_nota", "");
    }

    @Test
    @DisplayName("C4 Tope 100: proceso activo no relacionado + noticias (Crítico)")
    void c4() {
        Map<String, String> f = campos("Crítico", "No");
        f.put("dec1_coincide", "No");
        var t = Map.of(
                "listas_internacionales", List.of(fila("li_lista", "Noticias criminales", "li_ref", "LEG-13", "li_observacion", "Nota de prensa 2025")),
                "procesos", List.of(fila("p_delito", "Lesiones personales", "p_estado", "Activo", "p_calificacion", "LEG-11")));
        var r = calcular(f, t).resultado();
        assertThat(r.hallazgos()).extracting(Hallazgo::ref).containsExactly("LEG-12", "LEG-13", "LEG-11");
        assertThat(r.hallazgos().get(0).fuente()).isEqualTo("Declaración del evaluado");
        assertThat(r.hallazgos().get(0).detalle()).isEqualTo("La respuesta a la pregunta 1 no coincide con lo verificado");
        assertThat(r.hallazgos().get(1).fuente()).isEqualTo("Noticias criminales");
        assertThat(r.formula()).isEqualTo("25 + 40 + 70 = 135 → 100 (tope máximo)");
        assertThat(r.puntajeTexto()).isEqualTo("100,0");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(r.critico()).isFalse();
    }

    @Test
    @DisplayName("C5 Cargo de conducción (Estándar)")
    void c5() {
        Map<String, String> f = campos("Estándar", "Sí");
        f.put("simit_ref", "LEG-17");
        f.put("simit_multas", "3 pendientes");
        var t = Map.of("runt", List.of(fila("runt_categoria", "C2", "runt_estado", "Vencida", "runt_ref", "LEG-14")));
        CalculoFormulario c = calcular(f, t);
        var r = c.resultado();
        assertThat(r.hallazgos()).containsExactly(
                new Hallazgo("LEG-17", "Multas de tránsito pendientes sin acuerdo de pago (solo cargos de conducción)", "SIMIT", "Multas: 3 pendientes", "20"),
                new Hallazgo("LEG-14", "Licencia vencida o categoría que no corresponde al cargo (solo cargos de conducción)", "RUNT", "Categoría C2: Vencida", "40"));
        assertThat(r.formula()).isEqualTo("20 + 40 = 60 de 100 posibles");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(r.avisos()).isEmpty();
        assertThat(c.campos()).containsEntry("transito_nota", "");
    }

    @Test
    @DisplayName("Sin criticidad no hay puntaje, pero sí notas y numeración")
    void sinCriticidad() {
        Map<String, String> f = campos("", "No");
        CalculoFormulario c = calcular(f, Map.of("procesos", List.of(fila("p_delito", "Hurto", "p_calificacion", "LEG-21"))));
        assertThat(c.resultado()).isNull();
        assertThat(c.aviso()).isEqualTo("Falta la criticidad del cargo.");
        assertThat(c.campos()).containsEntry("srv_puntaje", "").containsEntry("transito_nota", NOTA_TRANSITO);
        assertThat(c.tablas().get("procesos")).extracting(x -> x.get("p_numero")).containsExactly("1");
        assertThat(c.criticidad()).isNull();
    }

    @Test
    @DisplayName("NS-07: la criticidad de la solicitud llega al motor aunque el formulario no la tenga: sin aviso 'Falta la criticidad'")
    void criticidadDeLaSolicitud() {
        Map<String, String> f = campos("", "No");
        var tablas = Map.of("procesos", List.of(fila("p_delito", "Hurto", "p_calificacion", "LEG-21")));
        CalculoFormulario c = calculo.calcular(secciones, null, Map.of(CalculoSubservicioService.CRITICIDAD_SOLICITUD, "SENSIBLE"), f, tablas).orElseThrow();
        assertThat(c.aviso()).isNull();
        assertThat(c.resultado()).isNotNull();
        assertThat(c.criticidad()).isEqualTo(CriticidadCargo.SENSIBLE);
    }

    @Test
    @DisplayName("NS-07: la criticidad de la solicitud manda sobre lo que haya en el formulario")
    void criticidadDeLaSolicitudMandaSobreElFormulario() {
        Map<String, String> f = campos("Estándar", "No");
        var tablas = Map.of("procesos", List.of(fila("p_delito", "Hurto", "p_calificacion", "LEG-21")));
        CalculoFormulario c = calculo.calcular(secciones, null, Map.of(CalculoSubservicioService.CRITICIDAD_SOLICITUD, "CRITICO"), f, tablas).orElseThrow();
        assertThat(c.criticidad()).isEqualTo(CriticidadCargo.CRITICO);
    }

    @Test
    @DisplayName("Estado del documento y dos preguntas que no coinciden: LEG-01 crítico y LEG-12 una sola vez")
    void opcionesQueGeneranHallazgo() {
        Map<String, String> f = campos("Estándar", "No");
        f.put("ev_estado_doc", "No encontrada");
        f.put("dec1_coincide", "No");
        f.put("dec3_coincide", "No");
        var r = calcular(f, Map.of()).resultado();
        assertThat(r.hallazgos()).extracting(Hallazgo::ref).containsExactly("LEG-01", "LEG-12");
        assertThat(r.hallazgos().get(0).detalle()).isEqualTo("Estado del documento: No encontrada");
        assertThat(r.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(calcular(f, Map.of()).criticidad()).isEqualTo(CriticidadCargo.ESTANDAR);
    }

    @Test
    @DisplayName("Las alertas (ALR) y el guion no suman")
    void alertasYGuion() {
        var t = Map.of("listas_internacionales", List.of(
                fila("li_lista", "PEP nacionales", "li_ref", "ALR-01"),
                fila("li_lista", "OFAC – Lista Clinton", "li_ref", "—")));
        var r = calcular(campos("Estándar", "No"), t).resultado();
        assertThat(r.hallazgos()).isEmpty();
        assertThat(r.puntajeTexto()).isEqualTo("0,0");
    }
}
