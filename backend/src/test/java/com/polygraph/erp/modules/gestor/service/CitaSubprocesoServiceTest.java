package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.service.MotivoSubprocesoService;
import com.polygraph.erp.modules.catalogo.service.MotivoSubprocesoService.MotivoResuelto;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.gestor.dto.CitaNoRealizadaRequest;
import com.polygraph.erp.modules.gestor.dto.ReasignarCitaRequest;
import com.polygraph.erp.modules.gestor.dto.ReprogramarCitaRequest;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.service.RegistroCambiosService;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.AccionHistorialAsignacion;
import com.polygraph.erp.shared.enums.EstadoConfirmacionAsistencia;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoMotivo;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Accion;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Marca;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SE-07 con la máquina de estados REAL (la que valida permisos y estados) y el resto simulado: confirmar la cita, «no ha llegado»,
 * «no se presentó» a los 15 minutos, reprogramar y reasignar, y la bandeja «Por confirmar».
 */
@DisplayName("CitaSubprocesoService — citas de poligrafía, visita y VerifEye")
class CitaSubprocesoServiceTest {

    private static final LocalDateTime CITA = LocalDateTime.of(2026, 10, 27, 9, 0);

    private final AsignacionService asignacion = mock(AsignacionService.class);
    private final ServicioSubprocesoRepository repository = mock(ServicioSubprocesoRepository.class);
    private final MotivoSubprocesoService motivos = mock(MotivoSubprocesoService.class);
    private final RegistroCambiosService registro = mock(RegistroCambiosService.class);
    private final HistorialAsignacionService historial = mock(HistorialAsignacionService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final UsuariosIntenosRepository empleados = mock(UsuariosIntenosRepository.class);
    private final CitaSubprocesoService service = new CitaSubprocesoService(asignacion, repository, motivos, registro, historial, notificaciones, usuarios, empleados);

    private Usuario profesional;
    private Usuario programador;
    private Usuario gestor;
    private ServicioSubproceso cita;
    private LocalDateTime ahora = CITA.plusMinutes(5);

    @BeforeEach
    void setUp() {
        profesional = Usuario.builder().idUsuario(10L).nombre("Pola").apellido("Grafista").rol(Rol.POLIGRAFISTA).activo(true).build();
        programador = Usuario.builder().idUsuario(20L).nombre("Pablo").rol(Rol.PROGRAMADOR).activo(true).build();
        gestor = Usuario.builder().idUsuario(30L).nombre("Gina").rol(Rol.GESTOR).activo(true).build();
        Cliente cliente = Cliente.builder().idCliente(7).idGestor(30L).build();
        Candidato candidato = Candidato.builder().nombres("Eva").apellidos("Luada").build();
        Servicio servicio = Servicio.builder().idServicio(40).cliente(cliente).candidato(candidato).build();
        TipoProgreso tipo = TipoProgreso.builder().idTipoProgreso(11).nombreProgreso("Poligrafia Pre Empleo").rolResponsable(Rol.POLIGRAFISTA).build();
        cita = ServicioSubproceso.builder().id(100L).servicio(servicio).tipoProgreso(tipo).usuarioAsignado(profesional).programadorResponsable(programador)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaProgramada(CITA).build();

        when(asignacion.obtenerOFallar(100L)).thenReturn(cita);
        when(asignacion.ahoraEnColombia()).thenAnswer(i -> ahora);
        when(asignacion.toResponse(any())).thenReturn(null);
        // la máquina de estados real y la regla de «es el responsable»
        when(asignacion.evaluar(any(), any(), any(), any())).thenCallRealMethod();
        doCallRealMethod().when(asignacion).validarEsDelEjecutor(any(), any());
        when(asignacion.duracionEfectiva(any())).thenReturn(60);
    }

    private MotivoResuelto motivo(String nombre, String codigo) {
        MotivoResuelto m = new MotivoResuelto(5, nombre, null, nombre, codigo);
        when(motivos.resolver(eq(5), eq(TipoMotivo.REPROGRAMACION), any())).thenReturn(m);
        return m;
    }

    // ───────────── confirmar al iniciar el informe ─────────────

    @Test
    @DisplayName("Iniciar el informe confirma la cita: queda «iniciado» y en EN_EJECUCION")
    void iniciarInformeConfirmaLaCita() {
        assertThat(cita.getIniciado()).isFalse();
        service.iniciarInforme(100L, profesional);
        assertThat(cita.getIniciado()).isTrue();
        assertThat(cita.getFechaIniciado()).isEqualTo(ahora);
        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.EN_EJECUCION);
        // repetirlo no cambia la fecha en que se inició
        ahora = ahora.plusMinutes(30);
        service.iniciarInforme(100L, profesional);
        assertThat(cita.getFechaIniciado()).isEqualTo(CITA.plusMinutes(5));
    }

    @Test
    @DisplayName("Solo el profesional asignado inicia el informe, y solo de una cita en ejecución")
    void iniciarInformeSoloElResponsable() {
        Usuario otro = Usuario.builder().idUsuario(11L).rol(Rol.POLIGRAFISTA).email("otro@x").build();
        assertThatThrownBy(() -> service.iniciarInforme(100L, otro)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        cita.setEstado(EstadoSubproceso.REPROGRAMADO);
        assertThatThrownBy(() -> service.iniciarInforme(100L, profesional)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ───────────── «El evaluado no ha llegado» ─────────────

    @Test
    @DisplayName("«El evaluado no ha llegado» notifica al programador, una sola vez")
    void noHaLlegadoNotificaAlProgramador() {
        service.evaluadoNoHaLlegado(100L, profesional);
        assertThat(cita.getAvisoNoLlegadoEn()).isEqualTo(ahora);
        verify(notificaciones).crearParaUsuario(eq(20L), eq("EVALUADO_NO_HA_LLEGADO"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(100L));
        assertThatThrownBy(() -> service.evaluadoNoHaLlegado(100L, profesional)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Ya le avisaste");
    }

    @Test
    @DisplayName("«No ha llegado» no se puede antes de la hora de la cita ni después de iniciar el informe")
    void noHaLlegadoSoloEnSuMomento() {
        ahora = CITA.minusMinutes(1);
        assertThatThrownBy(() -> service.evaluadoNoHaLlegado(100L, profesional)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        ahora = CITA.plusMinutes(5);
        cita.setIniciado(true);
        assertThatThrownBy(() -> service.evaluadoNoHaLlegado(100L, profesional)).isInstanceOf(ApiException.class);
        verify(notificaciones, never()).crearParaUsuario(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    // ───────────── «No se presentó» a los 15 minutos ─────────────

    @Test
    @DisplayName("«No se presentó» antes de los 15 minutos se rechaza")
    void noSePresentoAntesDeLos15Minutos() {
        motivo("El candidato no se presentó (pasaron 15 min)", "NO_SE_PRESENTO");
        ahora = CITA.plusMinutes(14);
        assertThatThrownBy(() -> service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), profesional))
                .isInstanceOf(ApiException.class).hasMessageContaining("15 minutos");
        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.EN_EJECUCION);
    }

    @Test
    @DisplayName("«No se presentó» a los 15 minutos: REPROGRAMADO, el reloj del servicio se recalcula (pausa) y se avisa al cliente y al programador")
    void noSePresentoPasaAReprogramadoYPausaElReloj() {
        motivo("El candidato no se presentó (pasaron 15 min)", "NO_SE_PRESENTO");
        ahora = CITA.plusMinutes(16);
        service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), profesional);

        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.REPROGRAMADO);
        assertThat(cita.getUsuarioAsignado()).isSameAs(profesional);          // conserva al profesional para reprogramar
        verify(registro).registrar(eq(cita), eq(EstadoSubproceso.EN_EJECUCION), eq(EstadoSubproceso.REPROGRAMADO), isNull(), anyString(), any(), eq(profesional));
        // recalcularEstadoServicio es quien sincroniza la pausa del reloj (SE-05): se invoca tras el cambio
        verify(asignacion).recalcularEstadoServicio(eq(cita.getServicio()), eq(profesional), anyString());
        verify(notificaciones).crearParaCliente(eq(7), eq("CITA_REPROGRAMANDO"), anyString(), anyString());
        verify(notificaciones).crearParaUsuario(eq(20L), eq("CITA_REPROGRAMADO"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(100L));
        // quien lo registró no se notifica a sí mismo
        verify(notificaciones, never()).crearParaUsuario(eq(10L), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Una cita ya iniciada (el informe empezó) no se puede dar por no realizada")
    void citaIniciadaNoSePuedeDarPorNoRealizada() {
        motivo("Falla técnica o de conexión", null);
        cita.setIniciado(true);
        ahora = CITA.plusMinutes(60);
        assertThatThrownBy(() -> service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), programador)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ───────────── el evaluado avisó que no asistirá ─────────────

    @Test
    @DisplayName("El evaluado avisó que no asistirá: lo registra el programador, pasa a REPROGRAMADO y se avisa al profesional")
    void avisoDelEvaluadoLoRegistraElProgramador() {
        motivo("El candidato avisó que no asistirá", "AVISO_NO_ASISTIRA");
        ahora = CITA.minusHours(3);                                            // no hace falta esperar la tolerancia
        service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), programador);
        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.REPROGRAMADO);
        verify(notificaciones).crearParaUsuario(eq(10L), eq("CITA_REPROGRAMADO"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(100L));
    }

    @Test
    @DisplayName("El gestor también registra el aviso del evaluado; el profesional no")
    void avisoDelEvaluadoNoLoRegistraElProfesional() {
        motivo("El candidato avisó que no asistirá", "AVISO_NO_ASISTIRA");
        assertThatThrownBy(() -> service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), profesional)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), gestor);
        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.REPROGRAMADO);
    }

    // ───────────── reprogramar y reasignar ─────────────

    private void citaReprogramada() {
        cita.setEstado(EstadoSubproceso.REPROGRAMADO);
        cita.setAvisoNoLlegadoEn(CITA.plusMinutes(10));
        cita.setAvisoPorConfirmarEn(CITA.plusMinutes(16));
        cita.setEstadoConfirmacion(EstadoConfirmacionAsistencia.CONFIRMADO);
    }

    @Test
    @DisplayName("Reprogramar con el mismo profesional: EN_EJECUCION con la marca «reprogramada» (suma 1), nueva fecha y avisos limpios")
    void reprogramarSumaLaMarca() {
        citaReprogramada();
        motivo("El candidato pidió cambiar la fecha", null);
        LocalDateTime nueva = LocalDateTime.of(2026, 10, 29, 10, 30);
        service.reprogramar(100L, new ReprogramarCitaRequest(nueva, 5, null, null), programador);

        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.EN_EJECUCION);
        assertThat(cita.getFechaProgramada()).isEqualTo(nueva);
        assertThat(cita.getReprogramaciones()).isEqualTo(1);
        assertThat(cita.getUsuarioAsignado()).isSameAs(profesional);
        assertThat(cita.getAvisoNoLlegadoEn()).isNull();
        assertThat(cita.getAvisoPorConfirmarEn()).isNull();
        assertThat(cita.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacionAsistencia.SIN_LLAMAR);
        verify(registro).registrar(eq(cita), eq(EstadoSubproceso.REPROGRAMADO), eq(EstadoSubproceso.EN_EJECUCION), eq(Marca.REPROGRAMADA), anyString(), any(), eq(programador));
        verify(historial).registrar(eq(cita), eq(AccionHistorialAsignacion.REPROGRAMADO), any(), any(), eq(CITA), eq(nueva), eq(programador), anyString());
        verify(asignacion).recalcularEstadoServicio(eq(cita.getServicio()), eq(programador), anyString());
        verify(notificaciones).crearParaCliente(eq(7), eq("CITA_REPROGRAMADA"), anyString(), org.mockito.ArgumentMatchers.contains("29 de octubre de 2026"));
        verify(notificaciones).crearParaUsuario(eq(10L), eq("CITA_REPROGRAMADA"), anyString(), anyString(), any(), any());

        // una segunda reprogramación suma otra
        citaReprogramada();
        service.reprogramar(100L, new ReprogramarCitaRequest(nueva.plusDays(1), 5, null, null), programador);
        assertThat(cita.getReprogramaciones()).isEqualTo(2);
    }

    @Test
    @DisplayName("Solo se reprograma una cita que está en REPROGRAMADO, con motivo y por el programador")
    void reprogramarSoloDesdeReprogramado() {
        motivo("El candidato pidió cambiar la fecha", null);
        LocalDateTime nueva = LocalDateTime.of(2026, 10, 29, 10, 30);
        // la cita sigue EN_EJECUCION: no se edita a mano
        assertThatThrownBy(() -> service.reprogramar(100L, new ReprogramarCitaRequest(nueva, 5, null, null), programador)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        citaReprogramada();
        assertThatThrownBy(() -> service.reprogramar(100L, new ReprogramarCitaRequest(nueva, 5, null, null), gestor)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.REPROGRAMADO);
    }

    @Test
    @DisplayName("Reasignar deja «asignada – no realizada» del primero en el historial y la cita queda EN_EJECUCION con el nuevo profesional")
    void reasignarDejaElHistorial() {
        citaReprogramada();
        motivo("El profesional no asistió", null);
        Usuario nuevo = Usuario.builder().idUsuario(12L).nombre("Nuevo").rol(Rol.POLIGRAFISTA).activo(true).idEmpleado(77).build();
        when(usuarios.findById(12L)).thenReturn(Optional.of(nuevo));
        when(empleados.findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(11)).thenReturn(List.of(UsuariosInternos.builder().idEmpleado(77).build()));
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 28, 8, 0);

        service.reasignar(100L, new ReasignarCitaRequest(12L, fecha, 5, null, null), programador);

        assertThat(cita.getUsuarioAsignado()).isSameAs(nuevo);
        assertThat(cita.getEstado()).isEqualTo(EstadoSubproceso.EN_EJECUCION);
        assertThat(cita.getFechaProgramada()).isEqualTo(fecha);
        // en el registro, el responsable del cambio es el PRIMER profesional con la marca ASIGNADA_NO_REALIZADA
        verify(registro).registrar(eq(cita), eq(profesional), eq(EstadoSubproceso.REPROGRAMADO), eq(EstadoSubproceso.EN_EJECUCION),
                eq(Marca.ASIGNADA_NO_REALIZADA), anyString(), any(), eq(programador));
        verify(historial).registrar(eq(cita), eq(AccionHistorialAsignacion.REASIGNADO), eq(profesional), eq(nuevo), eq(CITA), eq(fecha), eq(programador),
                org.mockito.ArgumentMatchers.contains("Asignada – no realizada"));
        verify(notificaciones).crearParaUsuario(eq(12L), eq("SUBPROCESO_ASIGNADO"), anyString(), anyString(), any(), any());
        verify(notificaciones).crearParaUsuario(eq(10L), eq("SUBPROCESO_DESASIGNADO"), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Reasignar exige OTRO profesional y que tenga el subproceso como capacidad")
    void reasignarValida() {
        citaReprogramada();
        motivo("El profesional no asistió", null);
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 28, 8, 0);
        assertThatThrownBy(() -> service.reasignar(100L, new ReasignarCitaRequest(10L, fecha, 5, null, null), programador))
                .isInstanceOf(ApiException.class).hasMessageContaining("Reprogramar");
        Usuario sinCapacidad = Usuario.builder().idUsuario(13L).rol(Rol.POLIGRAFISTA).idEmpleado(88).build();
        when(usuarios.findById(13L)).thenReturn(Optional.of(sinCapacidad));
        when(empleados.findBySubprocesosAsignados_IdTipoProgresoAndActivoTrue(anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> service.reasignar(100L, new ReasignarCitaRequest(13L, fecha, 5, null, null), programador))
                .isInstanceOf(ApiException.class).hasMessageContaining("capacidad");
        assertThat(cita.getUsuarioAsignado()).isSameAs(profesional);
    }

    // ───────────── bandeja «Por confirmar» ─────────────

    @Test
    @DisplayName("Una cita sin registro 15 minutos después de su hora aparece en «Por confirmar» del programador y del profesional, no la de otro")
    void citaSinRegistroEnPorConfirmar() {
        ahora = CITA.plusMinutes(16);
        when(repository.citasSinConfirmar(eq(EstadoSubproceso.EN_EJECUCION), any(), any())).thenReturn(List.of(cita));
        when(asignacion.toResponse(cita)).thenReturn(mock(com.polygraph.erp.modules.gestor.dto.AsignacionResponse.class));

        assertThat(service.porConfirmar(programador)).hasSize(1);
        assertThat(service.porConfirmar(profesional)).hasSize(1);
        Usuario otroProgramador = Usuario.builder().idUsuario(21L).rol(Rol.PROGRAMADOR).build();
        Usuario otroProfesional = Usuario.builder().idUsuario(11L).rol(Rol.VISITADOR).build();
        assertThat(service.porConfirmar(otroProgramador)).isEmpty();
        assertThat(service.porConfirmar(otroProfesional)).isEmpty();
        assertThat(service.resumen(programador).porConfirmar()).isEqualTo(1);
    }

    @Test
    @DisplayName("El contador «Por reprogramar» del programador cuenta los subprocesos en REPROGRAMADO que coordina")
    void porReprogramar() {
        citaReprogramada();
        when(repository.porEstadoYRoles(eq(EstadoSubproceso.REPROGRAMADO), any())).thenReturn(List.of(cita));
        assertThat(service.resumen(programador).porReprogramar()).isEqualTo(1);
        assertThat(service.resumen(new Usuario[]{Usuario.builder().idUsuario(21L).rol(Rol.PROGRAMADOR).build()}[0]).porReprogramar()).isZero();
    }

    @Test
    @DisplayName("El aviso de «Por confirmar» sale una sola vez por cita, al programador y al profesional")
    void avisoPorConfirmarUnaSolaVez() {
        ahora = CITA.plusMinutes(16);
        when(repository.citasSinConfirmar(eq(EstadoSubproceso.EN_EJECUCION), any(), any())).thenReturn(List.of(cita));
        assertThat(service.avisarPorConfirmar()).isEqualTo(1);
        assertThat(cita.getAvisoPorConfirmarEn()).isEqualTo(ahora);
        verify(notificaciones).crearParaUsuario(eq(20L), eq("CITA_POR_CONFIRMAR"), anyString(), anyString(), any(), any());
        verify(notificaciones).crearParaUsuario(eq(10L), eq("CITA_POR_CONFIRMAR"), anyString(), anyString(), any(), any());
        assertThat(service.avisarPorConfirmar()).isZero();           // ya está avisada
    }

    @Test
    @DisplayName("Un análisis (sin cita) no entra a ninguna de estas acciones")
    void soloLasCitas() {
        cita.getTipoProgreso().setRolResponsable(Rol.ANALISTA_INTERNO);
        assertThatThrownBy(() -> service.iniciarInforme(100L, profesional)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.citaNoRealizada(100L, new CitaNoRealizadaRequest(5, null), programador)).isInstanceOf(ApiException.class);
        verify(registro, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
        // evita el aviso de variable sin usar de Accion en el import
        assertThat(Accion.values()).isNotEmpty();
    }
}
