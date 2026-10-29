package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.DiaPersona;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.EquipoHoy;
import com.polygraph.erp.modules.gestor.dto.AgendaEquipoDtos.MiembroEquipo;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AgendaEquipoService — equipo hoy, día de una persona, atrasado en agenda y días no hábiles")
class AgendaEquipoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 27);              // martes
    private static final Set<LocalDate> FESTIVOS = Set.of(LocalDate.of(2026, 11, 2));

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ServicioSubprocesoRepository repository = mock(ServicioSubprocesoRepository.class);
    private final AsignacionService asignacion = mock(AsignacionService.class);
    private final CalendarioHabilService calendario = mock(CalendarioHabilService.class);
    private final AgendaEquipoService service = new AgendaEquipoService(usuarios, repository, asignacion, calendario);

    private Usuario programador, gestor, otroGestor, pola, vera, ana;
    private TipoProgreso poligrafia, visita, antecedentes;

    @BeforeEach
    void setUp() {
        programador = Usuario.builder().idUsuario(1L).nombre("Pablo").rol(Rol.PROGRAMADOR).activo(true).build();
        gestor = Usuario.builder().idUsuario(2L).nombre("Gina").rol(Rol.GESTOR).activo(true).build();
        otroGestor = Usuario.builder().idUsuario(3L).nombre("Otro").rol(Rol.GESTOR).activo(true).build();
        pola = Usuario.builder().idUsuario(10L).nombre("Pola").apellido("Grafista").rol(Rol.POLIGRAFISTA).activo(true).build();
        vera = Usuario.builder().idUsuario(11L).nombre("Vera").apellido("Visitadora").rol(Rol.VISITADOR).activo(true).build();
        ana = Usuario.builder().idUsuario(12L).nombre("Ana").apellido("Analista").rol(Rol.ANALISTA_INTERNO).activo(true).build();
        poligrafia = TipoProgreso.builder().idTipoProgreso(1).nombreProgreso("Poligrafia Pre Empleo").rolResponsable(Rol.POLIGRAFISTA).minutosEstimados(85).build();
        visita = TipoProgreso.builder().idTipoProgreso(2).nombreProgreso("Visita Domiciliaria").rolResponsable(Rol.VISITADOR).minutosEstimados(110).build();
        antecedentes = TipoProgreso.builder().idTipoProgreso(3).nombreProgreso("Antecedentes").rolResponsable(Rol.ANALISTA_INTERNO).minutosEstimados(30).build();

        when(calendario.hoy()).thenReturn(HOY);
        when(calendario.esDiaHabil(any())).thenAnswer(i -> {
            LocalDate d = i.getArgument(0);
            return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !FESTIVOS.contains(d);
        });
        when(asignacion.ahoraEnColombia()).thenReturn(HOY.atTime(10, 0));
        when(asignacion.duracionEfectiva(any())).thenAnswer(i -> ((ServicioSubproceso) i.getArgument(0)).getTipoProgreso().getMinutosEstimados());
        when(asignacion.toResponse(any())).thenReturn(null);
        when(usuarios.findByRolIn(any())).thenAnswer(i -> {
            List<Rol> roles = i.getArgument(0);
            return List.of(programador, gestor, pola, vera, ana).stream().filter(u -> roles.contains(u.getRol())).toList();
        });
        when(repository.findByFechaProgramadaBetween(any(), any())).thenReturn(List.of());
        when(repository.porEstadoYRoles(any(), any())).thenReturn(List.of());
    }

    private ServicioSubproceso cita(long id, TipoProgreso tipo, Usuario quien, Usuario coordina, LocalDateTime cuando, long idGestorDelCliente, int idServicio) {
        Servicio s = Servicio.builder().idServicio(idServicio).cliente(Cliente.builder().idCliente(1).idGestor(idGestorDelCliente).build())
                .candidato(Candidato.builder().nombres("Eva").apellidos("Luada").build()).build();
        return ServicioSubproceso.builder().id(id).servicio(s).tipoProgreso(tipo).usuarioAsignado(quien).programadorResponsable(coordina)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaProgramada(cuando).build();
    }

    // ───────────── Equipo hoy ─────────────

    @Test
    @DisplayName("El equipo lista a TODAS las personas del rol, aunque hoy no tengan ninguna cita (antes solo salían las vistas esta semana)")
    void listaATodosAunqueNoTenganTrabajo() {
        EquipoHoy r = service.equipoHoy(programador, HOY, null);
        assertThat(r.equipo()).extracting(MiembroEquipo::idUsuario).containsExactlyInAnyOrder(10L, 11L);
        // sin citas: toda la jornada libre salvo el almuerzo
        MiembroEquipo m = r.equipo().get(0);
        assertThat(m.minutosOcupados()).isZero();
        assertThat(m.minutosBloqueados()).isEqualTo(60);
        assertThat(m.minutosLibres()).isEqualTo(11 * 60 - 60);
        assertThat(m.citas()).isZero();
        assertThat(m.atrasados()).isZero();
        assertThat(r.esDiaHabil()).isTrue();
    }

    @Test
    @DisplayName("Cada persona con sus horas ocupadas y libres del día, y sus atrasados en agenda")
    void horasOcupadasYLibresYAtrasados() {
        ServicioSubproceso hoy1 = cita(100, poligrafia, pola, programador, HOY.atTime(9, 0), 2L, 40);       // 85 min
        ServicioSubproceso hoy2 = cita(101, poligrafia, pola, programador, HOY.atTime(14, 0), 2L, 41);      // 85 min
        when(repository.findByFechaProgramadaBetween(any(), any())).thenReturn(List.of(hoy1, hoy2));
        ServicioSubproceso vieja = cita(102, visita, vera, programador, HOY.minusDays(3).atTime(9, 0), 2L, 42);
        when(repository.porEstadoYRoles(any(), any())).thenReturn(List.of(vieja, hoy1));

        EquipoHoy r = service.equipoHoy(programador, HOY, null);
        MiembroEquipo p = r.equipo().stream().filter(x -> x.idUsuario() == 10L).findFirst().orElseThrow();
        assertThat(p.minutosOcupados()).isEqualTo(170);
        assertThat(p.minutosLibres()).isEqualTo(11 * 60 - 60 - 170);
        assertThat(p.citas()).isEqualTo(2);
        assertThat(p.atrasados()).isZero();
        MiembroEquipo v = r.equipo().stream().filter(x -> x.idUsuario() == 11L).findFirst().orElseThrow();
        assertThat(v.atrasados()).isEqualTo(1);
        assertThat(v.citas()).isZero();
    }

    @Test
    @DisplayName("Filtro por rol: solo poligrafistas, solo visitadores; un rol que no es de su equipo se rechaza")
    void filtroPorRol() {
        assertThat(service.equipoHoy(programador, HOY, Rol.POLIGRAFISTA).equipo()).extracting(MiembroEquipo::idUsuario).containsExactly(10L);
        assertThat(service.equipoHoy(programador, HOY, Rol.VISITADOR).equipo()).extracting(MiembroEquipo::idUsuario).containsExactly(11L);
        assertThatThrownBy(() -> service.equipoHoy(programador, HOY, Rol.ANALISTA_INTERNO)).isInstanceOf(ApiException.class);
        // el gestor ve a los analistas; el administrador, a los tres roles
        assertThat(service.equipoHoy(gestor, HOY, null).equipo()).extracting(MiembroEquipo::idUsuario).containsExactly(12L);
        assertThat(service.equipoHoy(Usuario.builder().idUsuario(99L).rol(Rol.ADMIN_POLYGRAPH).build(), HOY, null).equipo()).hasSize(3);
    }

    @Test
    @DisplayName("Un perfil que no es del equipo interno no ve la agenda")
    void soloPerfilesInternos() {
        Usuario cliente = Usuario.builder().idUsuario(50L).rol(Rol.ADMIN_CLIENTE).build();
        assertThatThrownBy(() -> service.equipoHoy(cliente, HOY, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.diaDePersona(cliente, 10L, HOY)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Un día que no se trabaja lo dice: sábado, domingo o festivo")
    void diaNoHabilEnElEquipo() {
        assertThat(service.equipoHoy(programador, LocalDate.of(2026, 10, 31), null).motivoNoHabil()).isEqualTo("Sábado");
        assertThat(service.equipoHoy(programador, LocalDate.of(2026, 11, 1), null).esDiaHabil()).isFalse();
        assertThat(service.equipoHoy(programador, LocalDate.of(2026, 11, 2), null).motivoNoHabil()).isEqualTo("Festivo");
    }

    // ───────────── El día de una persona por bloques ─────────────

    @Test
    @DisplayName("El día por bloques: ocupado (subproceso, evaluado, servicio, duración del catálogo), libre y bloqueado (almuerzo)")
    void diaPorBloques() {
        ServicioSubproceso c = cita(100, poligrafia, pola, programador, HOY.atTime(9, 0), 2L, 40);
        when(repository.findByFechaProgramadaBetween(any(), any())).thenReturn(List.of(c));

        DiaPersona d = service.diaDePersona(programador, 10L, HOY);
        assertThat(d.nombre()).isEqualTo("Pola");
        assertThat(d.jornadaInicio()).isEqualTo(HOY.atTime(7, 0));
        assertThat(d.jornadaFin()).isEqualTo(HOY.atTime(18, 0));
        var tipos = d.bloques().stream().map(b -> b.tipo()).toList();
        assertThat(tipos).containsExactly("LIBRE", "OCUPADO", "LIBRE", "BLOQUEADO", "LIBRE");
        var ocupado = d.bloques().get(1);
        assertThat(ocupado.subproceso()).isEqualTo("Poligrafia Pre Empleo");
        assertThat(ocupado.evaluado()).isEqualTo("Eva Luada");
        assertThat(ocupado.idServicio()).isEqualTo(40);
        assertThat(ocupado.minutos()).isEqualTo(85);                         // la duración del catálogo
        assertThat(ocupado.inicio()).isEqualTo(HOY.atTime(9, 0));
        assertThat(d.bloques().get(3).motivo()).isEqualTo("ALMUERZO");
        assertThat(d.minutosOcupados()).isEqualTo(85);
        assertThat(d.minutosOcupados() + d.minutosLibres() + d.minutosBloqueados()).isEqualTo(11 * 60);
    }

    @Test
    @DisplayName("Se puede elegir a cualquier persona del equipo; una que no es del equipo, no")
    void elegirAPersona() {
        assertThat(service.diaDePersona(programador, 11L, HOY).nombre()).isEqualTo("Vera");        // visitadora sin trabajo: sí aparece
        assertThatThrownBy(() -> service.diaDePersona(programador, 12L, HOY)).isInstanceOf(ApiException.class);   // analista: no es de su equipo
        assertThatThrownBy(() -> service.diaDePersona(programador, 777L, HOY)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("El gestor no ve el evaluado ni el servicio de la cita de OTRO gestor: solo «Ocupado»")
    void privacidadEntreGestores() {
        ServicioSubproceso mia = cita(100, antecedentes, ana, null, HOY.atTime(8, 0), 2L, 40);          // cliente de Gina
        ServicioSubproceso ajena = cita(101, antecedentes, ana, null, HOY.atTime(9, 0), 3L, 41);        // cliente de otro gestor
        when(repository.findByFechaProgramadaBetween(any(), any())).thenReturn(List.of(mia, ajena));

        DiaPersona d = service.diaDePersona(gestor, 12L, HOY);
        var ocupados = d.bloques().stream().filter(b -> b.tipo().equals("OCUPADO")).toList();
        assertThat(ocupados.get(0).evaluado()).isEqualTo("Eva Luada");
        assertThat(ocupados.get(0).idServicio()).isEqualTo(40);
        assertThat(ocupados.get(1).subproceso()).isEqualTo("Ocupado");
        assertThat(ocupados.get(1).evaluado()).isNull();
        assertThat(ocupados.get(1).idServicio()).isNull();
        assertThat(ocupados.get(1).minutos()).isEqualTo(30);                                           // pero su duración sí cuenta
    }

    // ───────────── Atrasado en agenda / Para reprogramar ─────────────

    @Test
    @DisplayName("«Para reprogramar»: lo que estaba programado para un día pasado y no se hizo; no lo de hoy ni lo futuro; el más atrasado primero")
    void paraReprogramar() {
        ServicioSubproceso muyVieja = cita(1, poligrafia, pola, programador, HOY.minusDays(9).atTime(9, 0), 2L, 40);
        ServicioSubproceso ayer = cita(2, poligrafia, pola, programador, HOY.minusDays(1).atTime(15, 0), 2L, 41);
        ServicioSubproceso deHoy = cita(3, poligrafia, pola, programador, HOY.atTime(8, 0), 2L, 42);
        ServicioSubproceso futura = cita(4, poligrafia, pola, programador, HOY.plusDays(2).atTime(8, 0), 2L, 43);
        when(repository.porEstadoYRoles(any(), any())).thenReturn(List.of(ayer, deHoy, futura, muyVieja));
        var llamadas = new java.util.ArrayList<Long>();
        when(asignacion.toResponse(any())).thenAnswer(i -> { llamadas.add(((ServicioSubproceso) i.getArgument(0)).getId()); return null; });

        service.paraReprogramar(programador);
        assertThat(llamadas).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Cada perfil ve solo sus atrasados: el programador las citas que coordina; el gestor, el análisis de sus clientes")
    void alcanceDeLosAtrasados() {
        ServicioSubproceso deOtroProgramador = cita(1, poligrafia, pola, Usuario.builder().idUsuario(77L).rol(Rol.PROGRAMADOR).build(), HOY.minusDays(2).atTime(9, 0), 2L, 40);
        ServicioSubproceso miaCita = cita(2, poligrafia, pola, programador, HOY.minusDays(2).atTime(9, 0), 2L, 41);
        ServicioSubproceso analisisMio = cita(3, antecedentes, ana, null, HOY.minusDays(2).atTime(9, 0), 2L, 42);
        ServicioSubproceso analisisAjeno = cita(4, antecedentes, ana, null, HOY.minusDays(2).atTime(9, 0), 3L, 43);
        when(repository.porEstadoYRoles(any(), any())).thenAnswer(i -> {
            List<Rol> roles = i.getArgument(1);
            return List.of(deOtroProgramador, miaCita, analisisMio, analisisAjeno).stream()
                    .filter(sp -> roles.contains(sp.getTipoProgreso().getRolResponsable())).toList();
        });
        var vistos = new java.util.ArrayList<Long>();
        when(asignacion.toResponse(any())).thenAnswer(i -> { vistos.add(((ServicioSubproceso) i.getArgument(0)).getId()); return null; });

        service.paraReprogramar(programador);
        assertThat(vistos).containsExactly(2L);
        vistos.clear();
        service.paraReprogramar(gestor);
        assertThat(vistos).containsExactly(3L);
    }

    // ───────────── Cronograma: días que no se trabajan ─────────────

    @Test
    @DisplayName("El cronograma marca como no hábiles los sábados, domingos y festivos")
    void diasNoHabiles() {
        var dias = service.diasNoHabiles(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 3));
        assertThat(dias).extracting(d -> d.fecha() + " " + d.motivo()).containsExactly(
                "2026-10-31 Sábado", "2026-11-01 Domingo", "2026-11-02 Festivo");
    }

    @Test
    @DisplayName("Un rango inválido o demasiado largo se rechaza")
    void rangoInvalido() {
        assertThatThrownBy(() -> service.diasNoHabiles(HOY, HOY.minusDays(1))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.diasNoHabiles(HOY, HOY.plusDays(500))).isInstanceOf(ApiException.class);
    }
}
