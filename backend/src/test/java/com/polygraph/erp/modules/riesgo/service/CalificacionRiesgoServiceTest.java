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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    /** Cálculo propio de subservicios (docs/antecedentes). Sin stub devuelve vacío: los subprocesos
     *  de este caso no tienen grupo, igual que antes. */
    @MockBean private com.polygraph.erp.modules.formularios.service.CalculoSubprocesoService calculoSubprocesoService;

    private Usuario gestor;
    private Servicio servicio;
    private ServicioSubproceso antecedentes;
    private ServicioSubproceso poligrafia;
    private FormularioDato respuesta;
    private FormularioColumna columnaPoligrafia;

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
                .estado(EstadoSubproceso.FINALIZADO).fechaCreacion(LocalDateTime.now()).build());
        poligrafia = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tPol)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(LocalDateTime.now()).build());

        // Plantilla de Antecedentes: "Resultado" (Lista, califica) — Multas = 40 pts, OFAC = crítica.
        FormularioSeccion seccion = persistir(FormularioSeccion.builder().tipoProgreso(tAnt).nombre("Consulta")
                .tipo(TipoSeccionFormulario.CAMPOS).orden(0).clave("consulta").build());
        FormularioColumna columna = FormularioColumna.builder().seccion(seccion).nombre("Resultado").tipoDato(TipoDatoFormulario.LISTA)
                .orden(0).clave("resultado").opciones("Limpio\nMultas\nOFAC").calificaRiesgo(true).build();
        columna.getPuntajes().add(FormularioColumnaPuntaje.builder().columna(columna).opcion("Multas").puntos(bd("40")).build());
        columna.getPuntajes().add(FormularioColumnaPuntaje.builder().columna(columna).opcion("OFAC").puntos(bd("0")).esCritico(true).build());
        persistir(columna);
        seccion.getColumnas().add(columna); // la relación en memoria también (la sección sigue en caché)
        // Poligrafía también califica (un Sí/No con puntos, sin responder = 0). Sin ningún campo que
        // califique quedaría SIN_CALIFICACION y fuera del estudio (decisión 06-oct-2026, opción b).
        FormularioSeccion seccionPol = persistir(FormularioSeccion.builder().tipoProgreso(tPol).nombre("Entrevista")
                .tipo(TipoSeccionFormulario.CAMPOS).orden(0).clave("entrevista").build());
        columnaPoligrafia = FormularioColumna.builder().seccion(seccionPol).nombre("Indicadores de engaño").tipoDato(TipoDatoFormulario.BOOLEANO)
                .orden(0).clave("engano").calificaRiesgo(true).build();
        columnaPoligrafia.getPuntajes().add(FormularioColumnaPuntaje.builder().columna(columnaPoligrafia).opcion("true").puntos(bd("50")).build());
        persistir(columnaPoligrafia);
        seccionPol.getColumnas().add(columnaPoligrafia);
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
        antecedentes.setEstado(EstadoSubproceso.EN_EJECUCION); // todavía llenándolo
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
        poligrafia.setEstado(EstadoSubproceso.CANCELADO);
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
        poligrafia.setEstado(EstadoSubproceso.FINALIZADO);
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
    @DisplayName("Opción b: un subservicio sin cómo calificar queda fuera — no deja el caso en proceso y su peso se reparte")
    void sinCalificacion_quedaFueraYRepartePeso() {
        // Poligrafía sigue ASIGNADA, pero ya no tiene ningún campo que califique.
        columnaPoligrafia = em.find(FormularioColumna.class, columnaPoligrafia.getIdColumna());
        columnaPoligrafia.setActivo(false);
        em.flush();

        CalificacionRiesgo r = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());

        assertThat(r.getEstado()).isEqualTo(EstadoResultado.CALCULADO); // no queda "en proceso"
        assertThat(r.getPuntajePonderado()).isEqualByComparingTo("40.0"); // solo Antecedentes (40 × 60 / 60)
        assertThat(r.getNivelFinal()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(r.getCobertura()).isEqualByComparingTo("60.00"); // el peso de Poligrafía no cuenta
    }

    @Test
    @DisplayName("Manda la criticidad de los formularios; si difieren, la más exigente (aunque el gestor haya puesto otra)")
    void criticidadDeLosFormularios_laMasExigente() {
        poligrafia.setEstado(EstadoSubproceso.FINALIZADO); // 40 × 60 % + 0 × 40 % = 24
        em.flush();
        org.mockito.Mockito.when(calculoSubprocesoService.calcular(org.mockito.ArgumentMatchers.argThat(sp -> sp != null && sp.getId().equals(antecedentes.getId()))))
                .thenReturn(java.util.Optional.of(conCriticidad(CriticidadCargo.SENSIBLE)));
        org.mockito.Mockito.when(calculoSubprocesoService.calcular(org.mockito.ArgumentMatchers.argThat(sp -> sp != null && sp.getId().equals(poligrafia.getId()))))
                .thenReturn(java.util.Optional.of(conCriticidad(CriticidadCargo.CRITICO)));

        // El gestor pone ESTÁNDAR, pero los formularios dicen SENSIBLE y CRÍTICO: vale CRÍTICO.
        CalificacionRiesgoResponse r = service.cambiarCriticidad(servicio.getIdServicio(), "ESTANDAR", gestor);
        assertThat(r.criticidad()).isEqualTo("CRITICO");
        assertThat(r.nivelFinal()).isEqualTo("MEDIO"); // 24 > 20 (límite BAJO de Crítico)
    }

    private static com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario conCriticidad(CriticidadCargo c) {
        return new com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario(
                java.util.Map.of(), java.util.Map.of(), null, null, c, java.util.List.of());
    }

    @Test
    @DisplayName("Criticidad del caso: CRITICO cambia los umbrales (24 ponderado → MEDIO en vez de BAJO)")
    void criticidadDelCaso() {
        poligrafia.setEstado(EstadoSubproceso.FINALIZADO); // 40 × 60 % + 0 × 40 % = 24
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
        poligrafia.setEstado(EstadoSubproceso.FINALIZADO); // 24 → BAJO
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

        poligrafia.setEstado(EstadoSubproceso.FINALIZADO);
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

        poligrafia.setEstado(EstadoSubproceso.FINALIZADO);
        em.flush();
        service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        service.decidir(servicio.getIdServicio(), "ALTO", "Criterio del gestor.", gestor);

        var calculado = service.marcadoresWord(servicio.getIdServicio(), poligrafia.getId());
        assertThat(calculado.riesgo()).containsEntry("nivel", "ALTO").containsEntry("puntaje", "24,0");
        assertThat(calculado.calificacion()).containsEntry("nivel", "BAJO").containsEntry("puntaje", "0,0");
    }

    @Test
    @DisplayName("Marcadores sin cálculo guardado: se calculan en memoria y NO escriben en la BD")
    void marcadoresWord_sinCalculo_noEscribe() {
        Long antes = em.createQuery("select count(c) from CalificacionRiesgo c", Long.class).getSingleResult();

        var marcadores = service.marcadoresWord(servicio.getIdServicio(), antecedentes.getId());

        assertThat(marcadores.riesgo()).containsEntry("nivel", "En proceso");
        em.flush();
        Long despues = em.createQuery("select count(c) from CalificacionRiesgo c", Long.class).getSingleResult();
        // Antes hacía un INSERT, que en la transacción readOnly del documento fallaba y el PDF no salía.
        assertThat(despues).isEqualTo(antes);
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

    // ───────────────────────── SE-06: «Sin calificar», nunca «BAJO 0» ─────────────────────────

    @Test
    @DisplayName("Un subproceso cuyo formulario no se ha diligenciado queda «Sin calificar»: sin nivel ni puntaje (no «BAJO 0»)")
    void sinFormularioEsSinCalificar() {
        em.clear();
        gestor = em.find(Usuario.class, gestor.getIdUsuario());
        var r = service.resumenSubproceso(poligrafia.getId(), gestor);
        assertThat(r.califica()).isTrue();
        assertThat(r.calculado()).isFalse();
        assertThat(r.nivel()).isNull();
        assertThat(r.puntaje()).isNull();
        assertThat(r.tieneCritica()).isFalse();
        assertThat(r.motivoSinCalcular()).isEqualTo("Todavía no se ha diligenciado el formulario.");
    }

    @Test
    @DisplayName("Con calificación por hallazgos y sin criticidad del cargo no hay puntaje: «Sin calificar» con el motivo, y el estudio no se cuenta como 0")
    void sinCriticidadEsSinCalificar() {
        antecedentes.getTipoProgreso().setGrupoCalificacion("HALLAZGOS");
        em.flush();
        when(calculoSubprocesoService.calcular(any())).thenReturn(Optional.of(new ModeloCodigos.CalculoFormulario(
                Map.of(), Map.of(), null, "Falta la criticidad del cargo.", null, List.of())));

        var r = service.resumenSubproceso(antecedentes.getId(), gestor);
        assertThat(r.calculado()).isFalse();
        assertThat(r.nivel()).isNull();
        assertThat(r.puntaje()).isNull();
        assertThat(r.motivoSinCalcular()).isEqualTo("Falta la criticidad del cargo.");

        // Antecedentes está FINALIZADO pero sin puntaje: el estudio lo espera (EN_PROCESO), no lo cuenta como realizado con 0 puntos.
        poligrafia.setEstado(EstadoSubproceso.CANCELADO);
        poligrafia.setNoRealizado(true);
        em.flush();
        CalificacionRiesgo estudio = service.recalcular(servicio.getIdServicio(), gestor.getIdUsuario());
        assertThat(estudio.getEstado()).isNotEqualTo(EstadoResultado.CALCULADO);
        assertThat(service.resultadoParaCliente(servicio.getIdServicio())).isEmpty();
    }

    @Test
    @DisplayName("Con la criticidad elegida el cálculo corre y el puntaje 0 es un resultado real (BAJO), no «Sin calificar»")
    void conCriticidadElPuntajeCeroEsReal() {
        antecedentes.getTipoProgreso().setGrupoCalificacion("HALLAZGOS");
        em.flush();
        var limpio = new ModeloCodigos.ResultadoCalificacionCodigos(List.of(), 0.0, NivelRiesgo.BAJO, false, List.of(), "0 de 100 posibles", List.of());
        when(calculoSubprocesoService.calcular(any())).thenReturn(Optional.of(new ModeloCodigos.CalculoFormulario(
                Map.of(), Map.of(), limpio, null, CriticidadCargo.ESTANDAR, List.of())));
        var r = service.resumenSubproceso(antecedentes.getId(), gestor);
        assertThat(r.calculado()).isTrue();
        assertThat(r.motivoSinCalcular()).isNull();
        assertThat(r.nivel()).isEqualTo("BAJO");
        assertThat(r.puntaje()).isEqualByComparingTo("0");
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
