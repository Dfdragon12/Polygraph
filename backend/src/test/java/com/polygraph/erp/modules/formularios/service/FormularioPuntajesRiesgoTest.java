package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.ColumnaResponse;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.ColumnaRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.PuntajeOpcionRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.SeccionRequest;
import com.polygraph.erp.modules.formularios.dto.SeccionPlantillaResponse;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fase B del motor de riesgo (docs/modelo-calificacion-riesgo.md §3), contra H2 real: puntos por
 *  respuesta en el constructor de plantillas y justificación obligatoria de respuestas críticas. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FormularioPlantillaService.class, FormularioService.class, CandidatoInfoService.class})
@DisplayName("Puntos de riesgo por respuesta y justificación de respuestas críticas")
class FormularioPuntajesRiesgoTest {

    @Autowired private FormularioPlantillaService plantillaService;
    @Autowired private FormularioService formularioService;
    @Autowired private jakarta.persistence.EntityManager em;
    @MockBean private NotificacionService notificacionService;
    @MockBean private AlmacenamientoDocumentosService almacenamientoService;
    // Cálculo propio de subservicios (docs/antecedentes): estas plantillas no tienen grupo, así que
    // el simulado devuelve vacío, igual que en producción.
    @MockBean private com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService calculoSubservicioService;
    @MockBean private FormularioDocumentoService documentoService;
    @MockBean private FormularioPlantillaWordService plantillaWordService;

    private TipoProgreso tipoProgreso;
    private Usuario analista;
    private ServicioSubproceso subproceso;

    @BeforeEach
    void setUp() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("SEG").nombre("Seguridad").activo(true).build());
        Proceso proceso = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        tipoProgreso = persistir(TipoProgreso.builder().nombreProgreso("Validación Laboral")
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(false).build());
        analista = persistir(Usuario.builder().nombre("Ana").email("ana.riesgo@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Usuario solicitante = persistir(Usuario.builder().nombre("Sis").email("sis.riesgo@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Cliente S.A.S.").emailPrincipal("cli.riesgo@test.com").idGestor(1L).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000111222").tipoDocumento("CC").nombres("Luis").apellidos("Prueba").build());
        Servicio servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante)
                .fechaSolicitud(LocalDate.now()).estado(EstadoServicio.PENDIENTE).build());
        subproceso = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tipoProgreso).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(java.time.LocalDateTime.now()).build());
        em.flush();
        em.clear();
    }

    /** Plantilla: una sección con "Resultado" (Lista, califica; "Falso" es crítica) y "Listas
     *  restrictivas" (Sí/No, califica; Sí es crítica). */
    private List<SeccionPlantillaResponse> guardarPlantillaCalificable(List<PuntajeOpcionRequest> puntajesResultado) {
        var resultado = new ColumnaRequest(null, "Resultado", "LISTA", null, null, "resultado",
                List.of("Confirmada", "Inconsistencias", "Falso"), true, puntajesResultado);
        var listas = new ColumnaRequest(null, "Listas restrictivas", "BOOLEANO", null, null, "listas", null, true,
                List.of(new PuntajeOpcionRequest("true", BigDecimal.ZERO, true), new PuntajeOpcionRequest("false", BigDecimal.ZERO, false)));
        var r = plantillaService.guardar(tipoProgreso.getIdTipoProgreso(), new GuardarPlantillaRequest(List.of(
                new SeccionRequest(null, "Verificación", "CAMPOS", "verificacion", null, null, List.of(resultado, listas), null))));
        em.flush();
        em.clear();
        return r;
    }

    @Test
    @DisplayName("Constructor: guarda puntos por respuesta y descarta los de opciones que ya no existen")
    void plantilla_guardaPuntos_descartaOpcionesInexistentes() {
        var r = guardarPlantillaCalificable(List.of(
                new PuntajeOpcionRequest("Confirmada", BigDecimal.ZERO, false),
                new PuntajeOpcionRequest("Inconsistencias", new BigDecimal("15"), false),
                new PuntajeOpcionRequest("Falso", BigDecimal.ZERO, true),
                new PuntajeOpcionRequest("Opción vieja renombrada", new BigDecimal("30"), false)));

        ColumnaResponse resultado = r.get(0).columnas().get(0);
        assertThat(resultado.calificaRiesgo()).isTrue();
        assertThat(resultado.puntajes()).extracting(ColumnaResponse.PuntajeOpcion::opcion)
                .containsExactlyInAnyOrder("Confirmada", "Inconsistencias", "Falso");
        assertThat(resultado.opcionesCriticas()).containsExactly("Falso");
    }

    @Test
    @DisplayName("Constructor: un campo de texto no puede calificar riesgo")
    void plantilla_textoNoCalifica_400() {
        var texto = new ColumnaRequest(null, "Observación", "TEXTO", null, null, "obs", null, true, List.of());
        assertThatThrownBy(() -> plantillaService.guardar(tipoProgreso.getIdTipoProgreso(), new GuardarPlantillaRequest(List.of(
                new SeccionRequest(null, "Verificación", "CAMPOS", "verificacion", null, null, List.of(texto), null)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("solo los campos Lista o Sí/No");
    }

    @Test
    @DisplayName("/estructura no trae puntos; el formulario sí (para sumar por fila)")
    void estructuraParaEjecutor_sinPuntos() {
        guardarPlantillaCalificable(List.of(new PuntajeOpcionRequest("Falso", BigDecimal.ZERO, true)));

        ColumnaResponse paraEjecutor = plantillaService.listarEstructura(tipoProgreso.getIdTipoProgreso(), false).get(0).columnas().get(0);
        assertThat(paraEjecutor.puntajes()).isNull();
        assertThat(paraEjecutor.opcionesCriticas()).containsExactly("Falso");

        // El formulario sí trae los puntos, para sumar por fila (decisión 25-sep-2026)
        ColumnaResponse enFormulario = formularioService.obtener(subproceso.getId(), analista).secciones().get(0).columnas().get(0);
        assertThat(enFormulario.puntajes()).extracting(ColumnaResponse.PuntajeOpcion::opcion).containsExactly("Falso");
    }

    @Test
    @DisplayName("Respuesta crítica sin explicación bloquea el envío; con explicación queda guardada")
    void respuestaCritica_exigeJustificacion() {
        var plantilla = guardarPlantillaCalificable(List.of(new PuntajeOpcionRequest("Falso", BigDecimal.ZERO, true)));
        Integer idSeccion = plantilla.get(0).idSeccion();
        Integer idResultado = plantilla.get(0).columnas().get(0).idColumna();
        Integer idListas = plantilla.get(0).columnas().get(1).idColumna();

        // 1) Dos respuestas críticas, sin explicación
        formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new GuardarFormularioRequest.SeccionDatosRequest(idSeccion, Map.of(idResultado, "Falso", idListas, "true"), null, Map.of()))), analista);
        em.flush(); em.clear();
        assertThat(formularioService.respuestasCriticasSinJustificar(subproceso.getId()))
                .containsExactlyInAnyOrder("Verificación › Resultado", "Verificación › Listas restrictivas");

        // 2) Se explican las dos
        FormularioResponse r = formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new GuardarFormularioRequest.SeccionDatosRequest(idSeccion, Map.of(idResultado, "Falso", idListas, "true"), null,
                        Map.of(idResultado, "El certificado no tiene firma del empleador.", idListas, "Coincide en OFAC.")))), analista);
        em.flush(); em.clear();
        assertThat(formularioService.respuestasCriticasSinJustificar(subproceso.getId())).isEmpty();
        assertThat(r.secciones().get(0).justificaciones()).containsEntry(idResultado, "El certificado no tiene firma del empleador.");

        // 3) Cambia a una respuesta normal: la explicación vieja se descarta
        FormularioResponse r2 = formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new GuardarFormularioRequest.SeccionDatosRequest(idSeccion, Map.of(idResultado, "Confirmada", idListas, "false"), null,
                        Map.of(idResultado, "texto viejo")))), analista);
        assertThat(r2.secciones().get(0).justificaciones()).isEmpty();
    }

    @Test
    @DisplayName("Campo eliminado de la plantilla: el gestor no lo ve ni con el subproceso completado; el admin sí")
    void campoEliminado_soloLoVeElAdmin() {
        var plantilla = guardarPlantillaCalificable(List.of(new PuntajeOpcionRequest("Falso", BigDecimal.ZERO, true)));
        Integer idSeccion = plantilla.get(0).idSeccion();
        Integer idResultado = plantilla.get(0).columnas().get(0).idColumna();
        Integer idListas = plantilla.get(0).columnas().get(1).idColumna();
        formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new GuardarFormularioRequest.SeccionDatosRequest(idSeccion, Map.of(idResultado, "Confirmada", idListas, "false"), null, Map.of()))), analista);
        em.flush(); em.clear();

        // El admin elimina "Listas restrictivas" (tiene datos → queda archivada) y el analista completa
        var resultado = new ColumnaRequest(idResultado, "Resultado", "LISTA", null, null, "resultado",
                List.of("Confirmada", "Inconsistencias", "Falso"), true, List.of(new PuntajeOpcionRequest("Falso", BigDecimal.ZERO, true)));
        plantillaService.guardar(tipoProgreso.getIdTipoProgreso(), new GuardarPlantillaRequest(List.of(
                new SeccionRequest(idSeccion, "Verificación", "CAMPOS", "verificacion", null, null, List.of(resultado), null))));
        em.flush(); em.clear();
        ServicioSubproceso sp = em.find(ServicioSubproceso.class, subproceso.getId());
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setVersionPlantilla(em.find(TipoProgreso.class, tipoProgreso.getIdTipoProgreso()).getVersionPlantilla());
        em.flush(); em.clear();

        Usuario gestor = persistir(Usuario.builder().nombre("Gina").email("gina.archivo@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        Usuario admin = persistir(Usuario.builder().nombre("Adm").email("adm.archivo@test.com").password("x")
                .rol(Rol.ADMIN_POLYGRAPH).activo(true).emailVerificado(true).build());
        Usuario otroGestor = persistir(Usuario.builder().nombre("Otro").email("otro.archivo@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        em.find(ServicioSubproceso.class, subproceso.getId()).getServicio().getCliente().setIdGestor(gestor.getIdUsuario());
        em.flush(); em.clear();

        // Un gestor que no es el del cliente no puede abrir el formulario (AccesoGestor)
        assertThatThrownBy(() -> formularioService.obtener(subproceso.getId(), otroGestor))
                .isInstanceOf(ApiException.class).hasMessageContaining("no gestionas");

        for (Usuario noAdmin : List.of(gestor, analista)) {
            assertThat(formularioService.obtener(subproceso.getId(), noAdmin).secciones().get(0).columnas())
                    .extracting(ColumnaResponse::idColumna).containsExactly(idResultado);
        }
        assertThat(formularioService.obtener(subproceso.getId(), admin).secciones().get(0).columnas())
                .extracting(ColumnaResponse::idColumna).containsExactlyInAnyOrder(idResultado, idListas);
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
