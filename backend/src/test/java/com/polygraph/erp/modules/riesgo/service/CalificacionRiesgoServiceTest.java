package com.polygraph.erp.modules.riesgo.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.CalificacionProcesoService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.formularios.entity.*;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.riesgo.dto.CalificacionRiesgoResponse;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.entity.ParametrosRiesgo;
import com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import com.polygraph.erp.shared.exceptions.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fase D del motor de riesgo, contra H2 real: un caso de punta a punta — respuestas con puntos,
 *  subproceso pendiente, "No realizado", historial sin duplicados, decisión del gestor y bloqueo
 *  al publicar. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({CalificacionRiesgoService.class, CalificacionProcesoService.class})
@DisplayName("CalificacionRiesgoService — el motor conectado a un caso real")
class CalificacionRiesgoServiceTest {

    @Autowired private CalificacionRiesgoService service;
    @Autowired private jakarta.persistence.EntityManager em;
    @MockBean private NotificacionService notificacionService;

    private Usuario gestor;
    private Servicio servicio;
    private ServicioSubproceso antecedentes;
    private ServicioSubproceso poligrafia;
    private FormularioDato respuesta;

    @BeforeEach
    void setUp() {
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.ESTANDAR).limiteBajo(bd("30")).limiteMedio(bd("60")).minimoMedio(bd("31")).build());
        persistir(UmbralRiesgo.builder().criticidad(CriticidadCargo.CRITICO).limiteBajo(bd("20")).limiteMedio(bd("40")).minimoMedio(bd("21")).build());
        persistir(ParametrosRiesgo.builder().id(1).umbralServicioAlto(bd("70")).umbralServicioModerado(bd("50"))
                .cantidadServiciosModerados(2).version(1).fechaActualizacion(LocalDateTime.now()).build());

        gestor = persistir(Usuario.builder().nombre("Gina").email("gina.riesgo@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        Usuario analista = persistir(Usuario.builder().nombre("Ana").email("ana.caso@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Cliente S.A.S.").emailPrincipal("cli.caso@test.com").idGestor(gestor.getIdUsuario()).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000333444").tipoDocumento("CC").nombres("Juan").apellidos("Prueba").build());

        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("EST").nombre("Estudios").activo(true).build());
        Proceso proceso = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion)
                .aplicaPrecioCiudad(false).coberturaMinima(bd("60")).build());
        TipoProgreso tAnt = persistir(TipoProgreso.builder().nombreProgreso("Antecedentes").orden(1).activo(true)
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(false).build());
        TipoProgreso tPol = persistir(TipoProgreso.builder().nombreProgreso("Poligrafía").orden(2).activo(true)
                .rolResponsable(Rol.POLIGRAFISTA).requiereDocumento(false).build());
        persistir(ProcesoTipoProgreso.builder().proceso(proceso).tipoProgreso(tAnt).habilitado(true).obligatorio(true).peso(bd("60")).build());
        persistir(ProcesoTipoProgreso.builder().proceso(proceso).tipoProgreso(tPol).habilitado(true).obligatorio(true).peso(bd("40")).build());

        servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(gestor)
                .fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        antecedentes = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tAnt).usuarioAsignado(analista)
                .estado(EstadoAsignacion.COMPLETADO).fechaCreacion(LocalDateTime.now()).build());
        poligrafia = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tPol)
                .estado(EstadoAsignacion.ASIGNADO).fechaCreacion(LocalDateTime.now()).build());

        // Plantilla de Antecedentes: "Resultado" (Lista, califica) — Multas = 40 pts, OFAC = crítica.
        FormularioSeccion seccion = persistir(FormularioSeccion.builder().tipoProgreso(tAnt).nombre("Consulta")
                .tipo(TipoSeccionFormulario.CAMPOS).orden(0).clave("consulta").build());
        FormularioColumna columna = FormularioColumna.builder().seccion(seccion).nombre("Resultado").tipoDato(TipoDatoFormulario.LISTA)
                .orden(0).clave("resultado").opciones("Limpio,Multas,OFAC").calificaRiesgo(true).build();
        columna.getPuntajes().add(FormularioColumnaPuntaje.builder().columna(columna).opcion("Multas").puntos(bd("40")).build());
        columna.getPuntajes().add(FormularioColumnaPuntaje.builder().columna(columna).opcion("OFAC").puntos(bd("0")).esCritico(true).build());
        persistir(columna);
        Formulario formulario = persistir(Formulario.builder().servicioSubproceso(antecedentes).fechaCreacion(LocalDateTime.now()).build());
        respuesta = persistir(FormularioDato.builder().formulario(formulario).columna(columna).valor("Multas").build());
        em.flush();
    }

    @Test
    @DisplayName("Con un subproceso pendiente queda EN_PROCESO, pero el subproceso realizado ya tiene su puntaje")
    void pendiente_enProceso() {
        CalificacionRiesgo r = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(r.getEstado()).isEqualTo(EstadoResultado.EN_PROCESO);

        CalificacionRiesgoResponse resp = service.obtener(servicio.getIdServicio(), gestor);
        var ant = resp.subprocesos().stream().filter(s -> s.nombre().equals("Antecedentes")).findFirst().orElseThrow();
        assertThat(ant.puntajeFinal()).isEqualByComparingTo("40.0");
        assertThat(ant.nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    @DisplayName("Encabezado del formulario: quien diligencia ve su puntaje parcial (sin el estudio); el gestor ve también el estudio")
    void resumenSubproceso_porRol() {
        antecedentes.setEstado(EstadoAsignacion.ASIGNADO); // todavía llenándolo
        em.flush();
        em.clear(); // la sección se recarga con su columna calificable
        antecedentes = em.find(ServicioSubproceso.class, antecedentes.getId());
        gestor = em.find(Usuario.class, gestor.getIdUsuario());
        Usuario analista = antecedentes.getUsuarioAsignado();

        var delAnalista = service.resumenSubproceso(antecedentes.getId(), analista);
        assertThat(delAnalista.califica()).isTrue();
        assertThat(delAnalista.estado()).isEqualTo("EN_CURSO");
        assertThat(delAnalista.nivel()).isEqualTo("MEDIO");
        assertThat(delAnalista.puntaje()).isEqualByComparingTo("40.0");
        assertThat(delAnalista.estudio()).isNull();

        var delGestor = service.resumenSubproceso(antecedentes.getId(), gestor);
        assertThat(delGestor.estudio()).isNotNull();
        assertThat(delGestor.estudio().estado()).isEqualTo("EN_PROCESO");

        Usuario otro = persistir(Usuario.builder().nombre("Otro").email("otro.sp@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        assertThatThrownBy(() -> service.resumenSubproceso(antecedentes.getId(), otro))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Poligrafía no realizada: su peso se reparte (40 × 100 % = 40, MEDIO) y pide revisión por el obligatorio")
    void noRealizado_repartePeso() {
        poligrafia.setEstado(EstadoAsignacion.CANCELADO);
        poligrafia.setNoRealizado(true);
        em.flush();

        CalificacionRiesgo r = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(r.getEstado()).isEqualTo(EstadoResultado.CALCULADO);
        assertThat(r.getPuntajeFinal()).isEqualByComparingTo("40.0");
        assertThat(r.getNivelFinal()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(service.obtener(servicio.getIdServicio(), gestor).motivosRevision())
                .contains("Subproceso obligatorio no realizado: Poligrafía");
    }

    @Test
    @DisplayName("Recalcular sin cambios no agrega historial; cambiar una respuesta sí (y la anterior deja de ser vigente)")
    void historial_soloSiCambia() {
        poligrafia.setEstado(EstadoAsignacion.COMPLETADO);
        em.flush();
        CalificacionRiesgo primero = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        CalificacionRiesgo igual = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(igual.getId()).isEqualTo(primero.getId());

        respuesta.setValor("OFAC");
        em.flush();
        CalificacionRiesgo nuevo = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(nuevo.getId()).isNotEqualTo(primero.getId());
        assertThat(nuevo.getNivelFinal()).isEqualTo(NivelRiesgo.ALTO); // respuesta crítica
        assertThat(em.find(CalificacionRiesgo.class, primero.getId()).getVigente()).isFalse();
        assertThat(service.obtener(servicio.getIdServicio(), gestor).historial()).hasSize(2);
    }

    @Test
    @DisplayName("Criticidad del caso: CRITICO cambia los umbrales (24 ponderado → MEDIO en vez de BAJO)")
    void criticidadDelCaso() {
        poligrafia.setEstado(EstadoAsignacion.COMPLETADO); // 40 × 60 % + 0 × 40 % = 24
        em.flush();
        assertThat(service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario()).getNivelFinal()).isEqualTo(NivelRiesgo.BAJO);

        CalificacionRiesgoResponse r = service.cambiarCriticidad(servicio.getIdServicio(), "CRITICO", gestor);
        assertThat(r.criticidadAjustada()).isTrue();
        assertThat(r.puntajeFinal()).isEqualByComparingTo("24.0");
        assertThat(r.nivelFinal()).isEqualTo("MEDIO");
    }

    @Test
    @DisplayName("Nivel automático: sin tocar nada vale la recomendación; cambiarlo exige justificación y se puede volver al automático")
    void decisionAutomaticaConOpcionDeCambiar() {
        poligrafia.setEstado(EstadoAsignacion.COMPLETADO); // 24 → BAJO
        em.flush();

        CalificacionRiesgoResponse auto = service.obtener(servicio.getIdServicio(), gestor);
        assertThat(auto.nivelEfectivo()).isEqualTo("BAJO");
        assertThat(auto.cambiadoPorGestor()).isFalse();

        assertThatThrownBy(() -> service.decidir(servicio.getIdServicio(), "ALTO", "  ", gestor))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        CalificacionRiesgoResponse cambiado = service.decidir(servicio.getIdServicio(), "ALTO", "Antecedentes recientes de riesgo.", gestor);
        assertThat(cambiado.nivelEfectivo()).isEqualTo("ALTO");
        assertThat(cambiado.cambiadoPorGestor()).isTrue();
        assertThat(cambiado.nivelFinal()).isEqualTo("BAJO"); // la recomendación del motor no se borra

        CalificacionRiesgoResponse deVuelta = service.decidir(servicio.getIdServicio(), null, null, gestor);
        assertThat(deVuelta.nivelEfectivo()).isEqualTo("BAJO");
        assertThat(deVuelta.cambiadoPorGestor()).isFalse();
    }

    @Test
    @DisplayName("Publicar NO exige decisión (vale la automática) y bloquea el resultado; en proceso no deja publicar")
    void publicacion() {
        assertThatThrownBy(() -> service.prepararPublicacion(servicio, gestor)) // poligrafía sigue asignada
                .isInstanceOf(ApiException.class).hasMessageContaining("en proceso");

        poligrafia.setEstado(EstadoAsignacion.COMPLETADO);
        em.flush();
        service.prepararPublicacion(servicio, gestor);
        servicio.setEstado(EstadoServicio.PUBLICADO); // lo que hace SolicitudService.cambiarEstado

        respuesta.setValor("OFAC"); // un cambio posterior ya no mueve el resultado bloqueado
        em.flush();
        CalificacionRiesgo bloqueado = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(bloqueado.getBloqueado()).isTrue();
        assertThat(bloqueado.getNivelFinal()).isEqualTo(NivelRiesgo.BAJO);

        // Se devuelve la solicitud: el riesgo se desbloquea y toma el cambio pendiente
        servicio.setEstado(EstadoServicio.FINALIZADO);
        em.flush();
        CalificacionRiesgo desbloqueado = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(desbloqueado.getBloqueado()).isFalse();
        assertThat(desbloqueado.getNivelFinal()).isNotEqualTo(NivelRiesgo.BAJO);
        assertThat(service.obtener(servicio.getIdServicio(), gestor).puedeDecidir()).isTrue();
    }

    @Test
    @DisplayName("Marcadores del Word: nivel que vale + puntaje del estudio, y los del propio subproceso")
    void marcadoresWord() {
        var enProceso = service.marcadoresWord(servicio.getIdServicio(), antecedentes.getId());
        assertThat(enProceso.riesgo()).containsEntry("nivel", "En proceso").containsEntry("puntaje", "");
        assertThat(enProceso.calificacion()).containsEntry("nivel", "MEDIO").containsEntry("puntaje", "40,0");

        poligrafia.setEstado(EstadoAsignacion.COMPLETADO);
        em.flush();
        service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        service.decidir(servicio.getIdServicio(), "ALTO", "Criterio del gestor.", gestor);

        var calculado = service.marcadoresWord(servicio.getIdServicio(), poligrafia.getId());
        assertThat(calculado.riesgo()).containsEntry("nivel", "ALTO").containsEntry("puntaje", "24,0");
        assertThat(calculado.calificacion()).containsEntry("nivel", "BAJO").containsEntry("puntaje", "0,0");
    }

    @Test
    @DisplayName("Otro gestor no puede ver ni decidir el riesgo del caso")
    void otroGestor_403() {
        Usuario otro = persistir(Usuario.builder().nombre("Otro").email("otro.gestor@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        assertThatThrownBy(() -> service.obtener(servicio.getIdServicio(), otro))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
