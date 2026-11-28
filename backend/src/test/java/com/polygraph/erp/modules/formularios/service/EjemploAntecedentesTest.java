package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.ColumnaResponse;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.entity.ParametrosRiesgo;
import com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** El estudio de ejemplo (docs/antecedentes/Ejemplo_Antecedentes_ficticio.pdf) se carga sobre la
 *  plantilla real (V207 + V208) y da lo mismo que el PDF: 30 + 15 + 15 = 60, MEDIO. Y un formulario
 *  nuevo trae ya puestas las fuentes que siempre se consultan (filas sugeridas de V208). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({EjemploAntecedentesService.class, FormularioService.class, CandidatoInfoService.class, FormularioDocumentoService.class,
        FormularioPlantillaWordService.class, CalculoSubprocesoService.class, CalculoSubservicioService.class, com.polygraph.erp.modules.riesgo.service.CentralRiesgoService.class,
        CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("Estudio de ejemplo de Antecedentes (PDF)")
class EjemploAntecedentesTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private EjemploAntecedentesService ejemplo;
    @Autowired private FormularioService formularioService;
    @MockBean private NotificacionService notificacionService;
    @MockBean private AlmacenamientoDocumentosService almacenamiento;

    private Usuario admin;
    private TipoProgreso antecedentes;
    private Proceso proceso;

    @BeforeEach
    void preparar() throws Exception {
        antecedentes = PlantillaAntecedentesPrueba.aplicarV207(jdbc, em);
        PlantillaAntecedentesPrueba.UMBRALES.forEach((c, u) -> em.persist(UmbralRiesgo.builder().criticidad(c)
                .limiteBajo(u.limiteBajo()).limiteMedio(u.limiteMedio()).minimoMedio(u.minimoMedio()).build()));
        em.persist(ParametrosRiesgo.builder().id(1).umbralServicioAlto(new BigDecimal("70")).umbralServicioModerado(new BigDecimal("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());
        admin = persistir(Usuario.builder().nombre("Admin").apellido("Prueba").email("admin.ejemplo@test.com").password("x")
                .rol(Rol.ADMIN_POLYGRAPH).activo(true).emailVerificado(true).build());
        persistir(Usuario.builder().nombre("Analista").apellido("Ejemplo").email("analista.ejemplo@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST").nombre("Estudios").activo(true).build());
        proceso = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false)
                .coberturaMinima(new BigDecimal("60")).criticidadDefecto(CriticidadCargo.ESTANDAR).build());
        antecedentes = em.find(TipoProgreso.class, antecedentes.getIdTipoProgreso());
        persistir(ProcesoTipoProgreso.builder().proceso(proceso).tipoProgreso(antecedentes).habilitado(true).obligatorio(true)
                .peso(new BigDecimal("25")).build());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Carga el caso del PDF: 60,0 puntos, MEDIO, y no se duplica")
    void cargaElEjemplo() {
        var cargado = ejemplo.cargar(admin.getEmail());
        em.flush();
        em.clear();
        assertThat(cargado.nuevo()).isTrue();
        assertThat(cargado.analista()).isEqualTo("Analista Ejemplo");

        FormularioResponse f = formularioService.obtener(cargado.idServicioSubproceso(), em.find(Usuario.class, admin.getIdUsuario()));
        assertThat(f.avisoCalculo()).isNull();
        assertThat(campo(f, "srv_puntaje")).isEqualTo("60,0");
        assertThat(campo(f, "srv_formula")).isEqualTo("30 + 15 + 15 = 60 de 100 posibles");
        assertThat(campo(f, "sem_medio")).isEqualTo("▲ 60,0 puntos");
        assertThat(campo(f, "procesos_nota")).isEqualTo("No se encontraron procesos judiciales en Rama Judicial.");
        assertThat(campo(f, "transito_nota")).startsWith("Informativo: el cargo no implica conducción");
        assertThat(tabla(f, "h_ref")).extracting(x -> x.get("h_ref") + " " + x.get("h_detalle")).containsExactly(
                "LEG-18 Multa tipo 2, espacio público (2021)",
                "LEG-19 Proceso N° 1: Hurto (art. 239) (Inactivo)",
                "LEG-19 Proceso N° 2: Violencia intrafamiliar (art. 229) (Inactivo)");
        assertThat(tabla(f, "ln_fuente")).hasSize(9);

        var otraVez = ejemplo.cargar(admin.getEmail());
        em.flush();
        assertThat(otraVez.nuevo()).isFalse();
        assertThat(otraVez.idServicioSubproceso()).isEqualTo(cargado.idServicioSubproceso());
        assertThat(em.createQuery("select count(s) from Servicio s", Long.class).getSingleResult()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Un formulario nuevo trae las fuentes del ejemplo ya puestas y los valores por defecto")
    void filasSugeridas() {
        Usuario analista = em.createQuery("select u from Usuario u where u.rol = :r", Usuario.class)
                .setParameter("r", Rol.ANALISTA_INTERNO).getSingleResult();
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Otra S.A.S.").emailPrincipal("otra@test.com").build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000000099").tipoDocumento("CC").nombres("Otra").apellidos("Persona").build());
        Servicio servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(em.find(Proceso.class, proceso.getIdProceso()))
                .usuarioSolicita(admin).fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        ServicioSubproceso sp = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(em.find(TipoProgreso.class, antecedentes.getIdTipoProgreso()))
                .usuarioAsignado(analista).estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).versionPlantilla(1).build());
        em.flush();
        em.clear();

        FormularioResponse f = formularioService.obtener(sp.getId(), em.find(Usuario.class, analista.getIdUsuario()));
        assertThat(tabla(f, "ln_fuente")).extracting(x -> x.get("ln_fuente") + " / " + x.get("ln_resultado") + " / " + x.get("ln_ref"))
                .containsExactly("Registraduría / Sin novedad / —", "Procuraduría / Sin novedad / —", "Policía Nacional / Sin novedad / —",
                        "INPEC / Sin novedad / —", "Medidas correctivas (RNMC) / Sin novedad / —", "Rama Judicial / Sin novedad / —",
                        "Contraloría / Sin novedad / —", "RUES / Sin novedad / —");
        assertThat(tabla(f, "li_lista")).extracting(x -> x.get("li_lista")).containsExactly("OFAC – Lista Clinton",
                "Consejo de Seguridad ONU (vinculante)", "Interpol", "Noticias criminales", "PEP nacionales");
        assertThat(f.secciones()).extracting(FormularioResponse.SeccionDatosResponse::nombre)
                .contains("6. Declaración del evaluado · Pregunta 1", "13. Concepto del analista");
    }

    private <T> T persistir(T e) {
        em.persist(e);
        return e;
    }

    private static String campo(FormularioResponse f, String clave) {
        for (var s : f.secciones()) {
            if (!"CAMPOS".equals(s.tipo())) continue;
            for (ColumnaResponse c : s.columnas()) if (clave.equals(c.clave())) return s.valores().get(c.idColumna());
        }
        throw new AssertionError("No existe el campo " + clave);
    }

    /** Filas de la tabla que tiene la columna dada, por clave. */
    private static List<Map<String, String>> tabla(FormularioResponse f, String columna) {
        for (var s : f.secciones()) {
            if (!"TABLA".equals(s.tipo()) || s.columnas().stream().noneMatch(c -> columna.equals(c.clave()))) continue;
            List<Map<String, String>> filas = new ArrayList<>();
            for (var fila : s.filas()) {
                Map<String, String> m = new HashMap<>();
                for (ColumnaResponse c : s.columnas()) m.put(c.clave(), fila.valores().get(c.idColumna()));
                filas.add(m);
            }
            return filas;
        }
        throw new AssertionError("No existe la tabla con " + columna);
    }
}
