package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.gestor.dto.GraficasGestorResponse;
import com.polygraph.erp.modules.gestor.service.BandejaGestorService;
import com.polygraph.erp.modules.gestor.service.EnvioEnlaceService;
import com.polygraph.erp.modules.gestor.service.NovedadesTableroService;
import com.polygraph.erp.modules.mensajes.service.MensajeService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.TipoAjuste;
import com.polygraph.erp.modules.riesgo.repository.CalificacionRiesgoRepository;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.estados.PeriodoEstadistica;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.TiempoServicioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La bandeja del gestor y el inicio del cliente contra una base real (H2): las consultas, el alcance (solo sus clientes), los pasos que suman lo
 * mismo que el semáforo, el control de calidad por riesgo, las alertas, «Avisar al programador» y el seguimiento del enlace del cliente.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("PB-05 — bandeja del gestor e inicio del cliente")
class PB05BandejaTest {

    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ServicioSubprocesoRepository subprocesoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HojaVidaEvaluadoRepository hojaVidaRepository;
    @Autowired private CalificacionRiesgoRepository calificacionRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EnvioEnlaceService envios = mock(EnvioEnlaceService.class);
    private BandejaGestorService bandeja;
    private InicioClienteService inicio;

    private Usuario gestor, otroGestor, analista, programador, usuarioCliente;
    private Cliente cliente, ajeno;
    private Proceso avanzado, antecedentes;
    private TipoProgreso tipoAnalisis, tipoCita;
    private Servicio s1, s2, s3, s4, s5, sOtro;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), ZoneId.of("America/Bogota"));
        var semaforo = new SemaforoEstadoService(servicioRepository, subprocesoRepository, mock(TiempoServicioService.class), mock(NovedadSubprocesoRepository.class), reloj);
        ReflectionTestUtils.setField(semaforo, "em", em);
        var estadisticas = new EstadisticasEstudiosService(reloj);
        ReflectionTestUtils.setField(estadisticas, "em", em);

        var calificaciones = mock(CalificacionRiesgoService.class);
        when(calificaciones.resumenSubproceso(anyLong(), any())).thenReturn(
                new CalificacionRiesgoService.ResumenSubproceso(true, "REALIZADO", "ALTO", new BigDecimal("72.0"), false, null, true, null));
        var novedades = mock(NovedadesTableroService.class);
        when(novedades.deGestor(anyLong())).thenReturn(new GraficasGestorResponse.Novedades(0, 0, null, List.of(), List.of(), List.of(), List.of()));
        var mensajes = mock(MensajeService.class);
        when(mensajes.misConversaciones(anyLong())).thenReturn(List.of());
        var tiempo = mock(TiempoServicioService.class);

        bandeja = new BandejaGestorService(semaforo, servicioRepository, hojaVidaRepository, calificacionRepository, calificaciones, novedades, mensajes,
                tiempo, notificaciones, usuarioRepository, estadisticas, jdbc);
        ReflectionTestUtils.setField(bandeja, "em", em);
        inicio = new InicioClienteService(usuarioRepository, subprocesoRepository, calificacionRepository, tiempo, envios, estadisticas, reloj);
        ReflectionTestUtils.setField(inicio, "em", em);
        when(envios.seguimientoParaCliente(any())).thenReturn(new EnvioEnlaceService.SeguimientoCliente("COMPLETO",
                LocalDateTime.of(2026, 11, 9, 9, 0), LocalDateTime.of(2026, 11, 9, 10, 0), 2, 5, null, LocalDate.of(2026, 11, 11)));

        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("SEG").nombre("Seguridad").activo(true).build());
        avanzado = persistir(Proceso.builder().nombreProceso("Avanzado").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        antecedentes = persistir(Proceso.builder().nombreProceso("Antecedentes").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        tipoAnalisis = persistir(TipoProgreso.builder().nombreProgreso("Antecedentes").rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(false).build());
        tipoCita = persistir(TipoProgreso.builder().nombreProgreso("Poligrafía").rolResponsable(Rol.POLIGRAFISTA).requiereDocumento(false).build());
        gestor = usuario("gestor@pb05.test", Rol.GESTOR);
        otroGestor = usuario("otro@pb05.test", Rol.GESTOR);
        analista = usuario("analista@pb05.test", Rol.ANALISTA_INTERNO);
        programador = usuario("prog@pb05.test", Rol.PROGRAMADOR);

        cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA).razonSocial("Cliente PB05")
                .emailPrincipal("pb05@test.com").idGestor(gestor.getIdUsuario()).build());
        ajeno = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA).razonSocial("Ajeno")
                .emailPrincipal("ajeno@test.com").idGestor(otroGestor.getIdUsuario()).build());
        usuarioCliente = persistir(Usuario.builder().nombre("Cliente").email("cliente@pb05.test").password("x").rol(Rol.ADMIN_CLIENTE).activo(true)
                .emailVerificado(true).idCliente(cliente.getIdCliente()).build());

        // #1 Avanzado: poligrafía PROGRAMANDO (programador) + antecedentes FINALIZADO (analista) — vencida; la cita es mañana
        s1 = servicio(cliente, avanzado, 1, EstadoServicio.EN_EJECUCION, EstadoTiempo.VENCIDA, -1);
        sp(s1, tipoCita, EstadoSubproceso.PROGRAMANDO, null, programador).setFechaProgramada(LocalDateTime.of(2026, 11, 11, 15, 0));
        sp(s1, tipoAnalisis, EstadoSubproceso.FINALIZADO, analista, null);
        // #2 Antecedentes: todo publicado, pasta PENDIENTE (falta emitirla)
        s2 = servicio(cliente, antecedentes, 2, EstadoServicio.FINALIZADO, EstadoTiempo.POR_VENCER, 0);
        sp(s2, tipoAnalisis, EstadoSubproceso.PUBLICADO, analista, null).setFechaPublicado(LocalDateTime.of(2026, 11, 10, 8, 0));
        // #3 Antecedentes: pendiente sin responsable, el candidato aún no termina (tiene enlace)
        s3 = servicio(cliente, antecedentes, 3, EstadoServicio.PENDIENTE, EstadoTiempo.EN_PAUSA, null);
        sp(s3, tipoAnalisis, EstadoSubproceso.PENDIENTE, null, null);
        persistir(LinkCandidato.builder().servicio(s3).token("tok-3").fechaCreacion(LocalDateTime.now()).fechaExpiracion(LocalDateTime.now().plusDays(2)).usado(false).build());
        // #4 Antecedentes: estudio completo (pasta emitida, nivel BAJO)
        s4 = servicio(cliente, antecedentes, 4, EstadoServicio.PUBLICADO, EstadoTiempo.ENTREGADA_A_TIEMPO, null);
        s4.setPastaEstado(PastaEstado.EMITIDA);
        s4.setEmitidoEn(LocalDateTime.of(2026, 11, 9, 16, 0));
        s4.setCumplimiento(Cumplimiento.A_TIEMPO);
        sp(s4, tipoAnalisis, EstadoSubproceso.PUBLICADO, analista, null).setFechaPublicado(LocalDateTime.of(2026, 11, 9, 15, 0));
        persistir(CalificacionRiesgo.builder().servicio(s4).vigente(true).estado(EstadoResultado.CALCULADO).puntajeFinal(new BigDecimal("10.0")).nivelFinal(NivelRiesgo.BAJO)
                .tipoAjuste(TipoAjuste.NINGUNO).requiereRevision(false).criticidad(CriticidadCargo.ESTANDAR).versionConfiguracion("t").detalleJson("{}").fechaCalculo(LocalDateTime.now()).build());
        // #5 cancelado
        s5 = servicio(cliente, antecedentes, 5, EstadoServicio.CANCELADO, null, null);
        sp(s5, tipoAnalisis, EstadoSubproceso.CANCELADO, null, null);
        // de otro gestor
        sOtro = servicio(ajeno, antecedentes, 6, EstadoServicio.EN_EJECUCION, EstadoTiempo.VENCIDA, -3);
        sp(sOtro, tipoAnalisis, EstadoSubproceso.FINALIZADO, analista, null);
        em.flush();
        em.clear();
    }

    // ───────────────────────── bandeja del gestor ─────────────────────────

    @Test
    @DisplayName("Los cinco pasos salen del mismo semáforo que Solicitudes y solo cuentan los clientes del gestor")
    void pasos() {
        var b = bandeja.bandeja(gestor.getIdUsuario());
        assertThat(b.estudios()).isEqualTo(5);                                   // #1 a #5; el de otro gestor no cuenta
        var p = b.pasos();
        assertThat(p).extracting(BandejaGestorService.PasoTrabajo::numero).containsExactly(1, 2, 3, 4, 5);
        assertThat(p.get(0).servicios()).isEqualTo(1);                           // Pendiente: #3
        assertThat(p.get(1).servicios()).isEqualTo(1);                           // Programando + Reprogramado: #1
        assertThat(p.get(1).estados()).isEqualTo("PROGRAMANDO,REPROGRAMADO");
        assertThat(p.get(2).servicios()).isZero();                               // En ejecución
        assertThat(p.get(3).servicios()).isEqualTo(1);                           // Finalizado: #1
        assertThat(p.get(3).resaltado()).isTrue();
        assertThat(p.get(4).servicios()).isEqualTo(2);                           // Publicado: #2 y #4
        assertThat(p.get(4).detalle()).contains("pasta pendiente en 1");
        assertThat(b.cancelado().servicios()).isEqualTo(1);
        assertThat(p.get(0).detalle()).contains("1 subprocesos sin ejecutor o sin programador").contains("1 enlaces por enviar");
    }

    @Test
    @DisplayName("Control de calidad: el informe por publicar y la pasta pendiente, con el riesgo más alto primero")
    void controlCalidad() {
        var cc = bandeja.bandeja(gestor.getIdUsuario()).controlCalidad();
        assertThat(cc.finalizadosPorPublicar()).isEqualTo(1);
        assertThat(cc.pastasPendientes()).isEqualTo(1);
        assertThat(cc.filas()).hasSize(2);
        var primera = cc.filas().get(0);                                         // ALTO (el simulado del subproceso) va antes que la pasta sin nivel
        assertThat(primera.tipo()).isEqualTo("SUBPROCESO");
        assertThat(primera.idServicio()).isEqualTo(s1.getIdServicio());
        assertThat(primera.riesgoNivel()).isEqualTo("ALTO");
        assertThat(primera.accion()).isEqualTo("REVISAR");
        assertThat(primera.accionEtiqueta()).isEqualTo("Revisar informe");
        var pasta = cc.filas().get(1);
        assertThat(pasta.tipo()).isEqualTo("PASTA");
        assertThat(pasta.idServicio()).isEqualTo(s2.getIdServicio());
        assertThat(pasta.accion()).isEqualTo("EMITIR_PASTA");
        assertThat(pasta.responsable()).isEqualTo("Tú");
    }

    @Test
    @DisplayName("Requiere tu atención: sin asignar, citas sin agendar y candidatos sin terminar")
    void atencion() {
        var a = bandeja.bandeja(gestor.getIdUsuario()).atencion();
        assertThat(a.sinAsignar().cantidad()).isEqualTo(1);                      // el mismo número que las alertas del semáforo
        assertThat(a.sinAsignar().muestras()).extracting(BandejaGestorService.Muestra::idServicio).containsExactly(s3.getIdServicio());
        assertThat(a.citasSinAgendar().cantidad()).isEqualTo(1);                 // la poligrafía en Programando
        assertThat(a.novedades().cantidad()).isZero();
    }

    @Test
    @DisplayName("Tiempos: los cinco grupos y las vencidas por responsable del retraso (la #1 tiene algo por revisar: es del gestor)")
    void tiempos() {
        var t = bandeja.bandeja(gestor.getIdUsuario()).tiempos();
        assertThat(t.conteo().get("VENCIDAS")).isEqualTo(1);
        assertThat(t.conteo().get("VENCE_HOY")).isEqualTo(1);
        assertThat(t.conteo().get("EN_PAUSA")).isEqualTo(1);
        assertThat(t.conteo().get("A_TIEMPO")).isZero();
        assertThat(t.vencidasPorResponsable().get("REVISION_GESTOR")).isEqualTo(1);
        assertThat(t.vencidasPorResponsable().get("CAMPO")).isZero();
        // con un filtro de estudio que no la incluye, no hay vencidas
        assertThat(bandeja.tiempos(gestor.getIdUsuario(), null, antecedentes.getIdProceso(), null).conteo().get("VENCIDAS")).isZero();
    }

    @Test
    @DisplayName("Mi equipo: asignados, vencidos y en revisión por persona")
    void equipo() {
        var equipo = bandeja.bandeja(gestor.getIdUsuario()).equipo();
        assertThat(equipo).hasSize(1);
        var a = equipo.get(0);
        assertThat(a.nombre()).isEqualTo("analista@pb05.test");
        assertThat(a.rol()).isEqualTo("Analista");
        assertThat(a.asignados()).isEqualTo(1);                                  // el FINALIZADO de #1 (lo del otro gestor no cuenta)
        assertThat(a.vencidos()).isEqualTo(1);
        assertThat(a.enRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Riesgo detectado: estudios cerrados del gestor por periodo")
    void riesgo() {
        var r = bandeja.riesgo(gestor.getIdUsuario(), PeriodoEstadistica.TOTAL);
        assertThat(r.cerrados()).isEqualTo(1);
        assertThat(r.riesgo().pctBajo()).isEqualTo(100);
        assertThat(bandeja.riesgo(otroGestor.getIdUsuario(), PeriodoEstadistica.TOTAL).cerrados()).isZero();
    }

    @Test
    @DisplayName("Avisar al programador: una notificación por programador con la lista de citas, y queda registrado")
    void avisarProgramador() {
        var r = bandeja.avisarProgramador(gestor.getIdUsuario(), null);
        assertThat(r.programadores()).isEqualTo(1);
        assertThat(r.citas()).isEqualTo(1);
        verify(notificaciones).crearParaUsuario(eq(programador.getIdUsuario()), eq("CITAS_SIN_AGENDAR"), any(), contains("#" + s1.getIdServicio()));
        verify(jdbc).update(contains("INSERT INTO avisos_programador"), eq(gestor.getIdUsuario()), eq(programador.getIdUsuario()), eq(1), any(), any());
    }

    @Test
    @DisplayName("Avisar al programador de un servicio sin citas por agendar da 409 y no envía nada; el de otro gestor tampoco")
    void avisarSinCitas() {
        assertThatThrownBy(() -> bandeja.avisarProgramador(gestor.getIdUsuario(), s2.getIdServicio())).isInstanceOf(ApiException.class).hasMessageContaining("No hay citas");
        assertThatThrownBy(() -> bandeja.avisarProgramador(otroGestor.getIdUsuario(), null)).isInstanceOf(ApiException.class);
        verify(notificaciones, never()).crearParaUsuario(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Opciones de los filtros: solo los clientes del gestor, sus estudios y su gente")
    void opciones() {
        var o = bandeja.opcionesFiltro(gestor.getIdUsuario());
        assertThat(o.clientes()).extracting(BandejaGestorService.Opcion::nombre).containsExactly("Cliente PB05");
        assertThat(o.estudios()).extracting(BandejaGestorService.Opcion::nombre).containsExactlyInAnyOrder("Avanzado", "Antecedentes");
        assertThat(o.responsables()).extracting(BandejaGestorService.Opcion::nombre).contains("analista@pb05.test", "prog@pb05.test");
    }

    // ───────────────────────── inicio del cliente ─────────────────────────

    @Test
    @DisplayName("Inicio del cliente: informes listos (completo con nivel, parcial sin nivel), candidato sin terminar con su seguimiento, cita y tiempos")
    void inicioDelCliente() {
        var i = inicio.inicio("cliente@pb05.test", LocalDateTime.of(2026, 11, 9, 12, 0));
        // #4 completo (emitido ayer 16:00) y #2 / #1 con algo publicado; #4 y #2 son nuevos desde ayer a mediodía
        assertThat(i.informes()).extracting(InicioClienteService.Informe::idServicio).contains(s4.getIdServicio(), s2.getIdServicio());
        var completo = i.informes().stream().filter(x -> x.idServicio().equals(s4.getIdServicio())).findFirst().orElseThrow();
        assertThat(completo.completo()).isTrue();
        assertThat(completo.nivel()).isEqualTo("BAJO");
        assertThat(completo.nuevo()).isTrue();
        var sinPasta = i.informes().stream().filter(x -> x.idServicio().equals(s2.getIdServicio())).findFirst().orElseThrow();
        assertThat(sinPasta.completo()).isFalse();
        assertThat(sinPasta.nivel()).isNull();                                   // sin pasta emitida no se muestra nivel
        assertThat(i.informesNuevos()).isEqualTo(2);                             // #2 (hoy) y #4 (ayer 15:00) se publicaron después de la última visita
        assertThat(i.candidatos()).hasSize(1);
        assertThat(i.candidatos().get(0).idServicio()).isEqualTo(s3.getIdServicio());
        assertThat(i.candidatos().get(0).seguimiento().seccionesHechas()).isEqualTo(2);
        assertThat(i.citas()).hasSize(1);
        assertThat(i.citas().get(0).subproceso()).isEqualTo("Poligrafía");
        assertThat(i.cosasPorAtender()).isEqualTo(2);
        assertThat(i.tiempos().atrasadas()).isEqualTo(1);                        // #1 (la del otro cliente no cuenta)
        assertThat(i.tiempos().venceHoyOManana()).isEqualTo(1);                  // #2
        assertThat(i.tiempos().enPausa()).isEqualTo(1);                          // #3
    }

    @Test
    @DisplayName("Sin última visita no hay marcas de «nuevo»; y un usuario sin empresa recibe 403")
    void sinUltimaVisita() {
        var i = inicio.inicio("cliente@pb05.test", null);
        assertThat(i.informesNuevos()).isZero();
        assertThat(i.informes()).allMatch(x -> !x.nuevo());
        assertThatThrownBy(() -> inicio.inicio("gestor@pb05.test", null)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Estadísticas del cliente por periodo: anillos que suman 100 y vacío si no hay estudios")
    void estadisticasCliente() {
        var total = inicio.estadisticas("cliente@pb05.test", PeriodoEstadistica.TOTAL);
        assertThat(total.emitidos()).isEqualTo(1);
        assertThat(total.riesgo().pctBajo()).isEqualTo(100);
        assertThat(total.solicitados()).isEqualTo(4);                            // #1 a #4 (la cancelada no cuenta)
        var hoy = inicio.estadisticas("cliente@pb05.test", PeriodoEstadistica.HOY);
        assertThat(hoy.riesgo().total() == 0 || hoy.riesgo().pctBajo() + hoy.riesgo().pctMedio() + hoy.riesgo().pctAlto() == 100).isTrue();
    }

    // ───────────────────────── apoyo ─────────────────────────

    private Servicio servicio(Cliente c, Proceso proceso, int n, EstadoServicio estado, EstadoTiempo tiempo, Integer dias) {
        Candidato cand = persistir(Candidato.builder().cedula("3000000" + n).tipoDocumento("CC").nombres("Eval" + n).apellidos("PB05").build());
        Usuario solicita = usuario("solicita" + n + "@pb05.test", Rol.GESTOR);
        Servicio s = persistir(Servicio.builder().cliente(c).candidato(cand).proceso(proceso).usuarioSolicita(solicita).fechaSolicitud(HOY).estado(estado).build());
        s.setEstadoTiempo(tiempo);
        s.setDiasRestantesHabiles(dias);
        return s;
    }

    private ServicioSubproceso sp(Servicio s, TipoProgreso tipo, EstadoSubproceso estado, Usuario responsable, Usuario programadorResponsable) {
        return persistir(ServicioSubproceso.builder().servicio(s).tipoProgreso(tipo).estado(estado).usuarioAsignado(responsable).programadorResponsable(programadorResponsable).build());
    }

    private Usuario usuario(String email, Rol rol) {
        return persistir(Usuario.builder().nombre(email).email(email).password("x").rol(rol).activo(true).emailVerificado(true).build());
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
