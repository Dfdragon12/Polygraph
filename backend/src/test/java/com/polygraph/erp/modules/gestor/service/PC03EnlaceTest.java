package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.auth.service.EmailService;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.service.EvaluadoService;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoEnlace;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PC-03: vigencia de cada envío desde AHORA, historial por envío sin pisar el primero, mensaje con el nombre del servicio y estado único para el cliente. */
@DisplayName("PC-03 — envío del enlace: vigencia, historial y mensaje")
class PC03EnlaceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);          // martes
    private static final LocalDate VIGENCIA = LocalDate.of(2026, 11, 26);     // jueves: 2 días hábiles desde hoy

    private EnvioEnlaceService servicio;
    private JdbcTemplate jdbc;
    private EvaluadoService evaluado;
    private LinkCandidato link;
    private Servicio s;
    private final Usuario gestor = Usuario.builder().idUsuario(5L).email("g@x.co").nombre("Andrea").rol(Rol.ADMIN_POLYGRAPH).build();

    @BeforeEach
    void setUp() {
        LinkCandidatoRepository links = mock(LinkCandidatoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        evaluado = mock(EvaluadoService.class);
        CalendarioHabilService calendario = mock(CalendarioHabilService.class);
        jdbc = mock(JdbcTemplate.class);
        servicio = new EnvioEnlaceService(links, usuarios, mock(CandidatoRepository.class), mock(HojaVidaEvaluadoRepository.class), evaluado, calendario, mock(EmailService.class), jdbc);
        ReflectionTestUtils.setField(servicio, "frontendUrl", "https://app.polygraph.test");
        Candidato candidato = Candidato.builder().nombres("Carolina").apellidos("Ruiz").celular("+57 300 000 0047").emailPrincipal("caro@x.co").build();
        s = Servicio.builder().idServicio(47).cargo("Conductor").estado(EstadoServicio.PENDIENTE).candidato(candidato)
                .proceso(Proceso.builder().nombreProceso("Poligrafia Pre Empleo").build())
                .cliente(Cliente.builder().idCliente(1).nombreComercial("Carlos Palacios").build()).build();
        // Reenvío: el primer envío fue el 3/11 y su vigencia (5/11) ya pasó.
        link = LinkCandidato.builder().id(9L).servicio(s).token("TOKEN47").usado(false).revocado(false).intentosFallidos((short) 0)
                .enviadoEn(LocalDateTime.of(2026, 11, 3, 9, 0)).fechaExpiracion(LocalDateTime.of(2026, 11, 5, 23, 59, 59)).build();
        when(links.findById(9L)).thenReturn(Optional.of(link));
        when(usuarios.findByEmail("g@x.co")).thenReturn(Optional.of(gestor));
        when(evaluado.tipoDeEnlace(any())).thenReturn(TipoEnlace.COMPLETO);
        when(calendario.hoy()).thenReturn(HOY);
        when(calendario.sumarDiasHabiles(HOY, 2)).thenReturn(VIGENCIA);
    }

    @Test
    @DisplayName("c) Reenviar hoy: la ventana y el mensaje traen la vigencia de 2 días hábiles desde HOY (jueves 26/11/2026), no la fecha vieja del 5/11")
    void laVentanaUsaLaVigenciaNueva() {
        var ctx = servicio.contexto(9L, "g@x.co");
        assertThat(ctx.venceReferencia()).isEqualTo(VIGENCIA);
        assertThat(ctx.mensaje()).contains("jueves 26/11/2026").doesNotContain("5/11/2026");
    }

    @Test
    @DisplayName("c) Al reenviar se recalcula la vigencia, se anota en SU fila y el primer envío NO se sobrescribe")
    void reenviarRecalculaYNoPisaElPrimero() {
        servicio.enviar(9L, new EnvioEnlaceService.EnviarRequest(null, null, null, Set.of("CORREO", "WHATSAPP")), "g@x.co");

        assertThat(link.getFechaExpiracion().toLocalDate()).isEqualTo(VIGENCIA);
        assertThat(link.getEnviadoEn()).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 0));   // el primer envío sigue siendo el primero

        ArgumentCaptor<Object> id = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> vigencia = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, atLeastOnce()).update(contains("INSERT INTO envios_enlace"), eq(9L), any(), any(), any(), eq(5L), eq(false), any(), id.capture(), vigencia.capture());
        assertThat(id.getAllValues()).hasSize(2).doesNotContainNull();
        assertThat(id.getAllValues().get(0)).isEqualTo(id.getAllValues().get(1));                       // correo y WhatsApp = UN envío
        assertThat(vigencia.getAllValues()).allSatisfy(v -> assertThat(v.toString()).isEqualTo("2026-11-26"));
    }

    @Test
    @DisplayName("e) Una Poligrafía Pre Empleo no dice «estudio de seguridad»: dice «tu prueba de poligrafía pre empleo»")
    void mensajePorServicio() {
        var ctx = servicio.contexto(9L, "g@x.co");
        assertThat(ctx.mensaje()).contains("Carlos Palacios nos pidió tu prueba de poligrafía pre empleo para el cargo de Conductor").doesNotContain("estudio de seguridad");

        s.setProceso(Proceso.builder().nombreProceso("Antecedentes").build());
        assertThat(servicio.contexto(9L, "g@x.co").mensaje()).contains("nos pidió tu consulta de antecedentes");
        s.setProceso(Proceso.builder().nombreProceso("Estudio de seguridad").build());
        assertThat(servicio.contexto(9L, "g@x.co").mensaje()).contains("nos pidió tu estudio de seguridad");   // solo si el servicio se llama así
    }

    @Test
    @DisplayName("La frase del servicio usa su nombre, con tildes, sin inventar «estudio de seguridad»")
    void fraseDelServicio() {
        assertThat(FraseServicio.de("Poligrafia Pre Empleo")).isEqualTo("tu prueba de poligrafía pre empleo");
        assertThat(FraseServicio.de("Poligrafia de Rutina")).isEqualTo("tu prueba de poligrafía de rutina");
        assertThat(FraseServicio.de("Antecedentes")).isEqualTo("tu consulta de antecedentes");
        assertThat(FraseServicio.de("Visita Domiciliaria  presencial")).isEqualTo("tu visita domiciliaria presencial");
        assertThat(FraseServicio.de("Validacion Laboral")).isEqualTo("tu validación laboral");
        assertThat(FraseServicio.de("Analisis Financiero")).isEqualTo("tu análisis financiero");
        assertThat(FraseServicio.de(null)).isEqualTo("tu estudio");
        assertThat(FraseServicio.de(" ")).isEqualTo("tu estudio");
    }

    @Test
    @DisplayName("a) #37: el cliente ve «El enlace venció el 23 de oct. Tu gestor debe reenviarlo.», no «aún no ha enviado… lo abrió con su cédula»")
    void elClienteVeUnSoloTexto() {
        link.setEnviadoEn(null);                                              // sin primer envío registrado
        link.setFechaPrimerIngreso(LocalDateTime.of(2026, 10, 21, 9, 0));     // pero lo abrió
        link.setFechaExpiracion(LocalDateTime.of(2026, 10, 23, 23, 59, 59));
        var sg = servicio.seguimientoParaCliente(link);
        assertThat(sg.estado()).isEqualTo("VENCIDO");
        assertThat(sg.texto()).isEqualTo("El enlace venció el 23 de oct. Tu gestor debe reenviarlo.");
    }

    @Test
    @DisplayName("El estado que ve el cliente sale del servidor para cada caso: no enviado, enviado y abierto")
    void estadosParaElCliente() {
        link.setEnviadoEn(null);
        link.setFechaExpiracion(LocalDateTime.of(2026, 10, 23, 23, 59, 59));
        assertThat(servicio.seguimientoParaCliente(link).estado()).isEqualTo("NO_ENVIADO");

        link.setEnviadoEn(LocalDateTime.of(2026, 11, 24, 10, 0));
        link.setFechaExpiracion(VIGENCIA.atTime(23, 59, 59));
        var enviado = servicio.seguimientoParaCliente(link);
        assertThat(enviado.estado()).isEqualTo("ENVIADO");
        assertThat(enviado.texto()).isEqualTo("Polygraph le envió el enlace el 24 de nov. Todavía no lo ha abierto. Vence el 26 de nov.");

        link.setFechaPrimerIngreso(LocalDateTime.of(2026, 11, 24, 15, 0));
        assertThat(servicio.seguimientoParaCliente(link).estado()).isEqualTo("ABIERTO");
    }

    // ───────────── historial de envíos ─────────────

    private static HistorialEnvios.Fila fila(long id, String idEnvio, String canal, LocalDateTime fecha, LocalDate vigencia, String usuario) {
        return new HistorialEnvios.Fila(id, idEnvio, canal, fecha, vigencia, usuario);
    }

    @Test
    @DisplayName("A) El historial trae UN renglón por envío o reenvío (canales juntos, quién y vigencia), del más reciente al más antiguo; el primero sigue ahí")
    void historialPorEnvio() {
        var filas = List.of(
                fila(1, null, "ANTERIOR", LocalDateTime.of(2026, 10, 21, 8, 0), null, "Andrea Gómez"),
                fila(2, "e1", "CORREO", LocalDateTime.of(2026, 11, 3, 9, 0), LocalDate.of(2026, 11, 5), "Andrea Gómez"),
                fila(3, "e1", "WHATSAPP", LocalDateTime.of(2026, 11, 3, 9, 0, 1), LocalDate.of(2026, 11, 5), "Andrea Gómez"),
                fila(4, null, "CAMBIO_CONTACTO", LocalDateTime.of(2026, 11, 24, 9, 0), null, "Andrea Gómez"),
                fila(5, null, "RECORDATORIO", LocalDateTime.of(2026, 11, 4, 9, 0), null, null),
                fila(6, "e2", "CORREO", LocalDateTime.of(2026, 11, 24, 10, 0), LocalDate.of(2026, 11, 26), "Andrea Gómez"));

        var envios = HistorialEnvios.agrupar(filas);

        assertThat(envios).hasSize(3);                                            // el cambio de contacto y el recordatorio no son envíos
        assertThat(envios.get(0).fecha()).isEqualTo(LocalDateTime.of(2026, 11, 24, 10, 0));
        assertThat(envios.get(0).canales()).containsExactly("CORREO");
        assertThat(envios.get(0).vigenciaHasta()).isEqualTo(LocalDate.of(2026, 11, 26));   // la vigencia NUEVA, en su propio renglón
        assertThat(envios.get(1).canales()).containsExactly("CORREO", "WHATSAPP");
        assertThat(envios.get(1).vigenciaHasta()).isEqualTo(LocalDate.of(2026, 11, 5));    // la del envío anterior no se pisó
        assertThat(envios.get(2).sistemaAnterior()).isTrue();                              // el primero (sistema anterior) sigue en el historial
        assertThat(envios.get(2).fecha()).isEqualTo(LocalDateTime.of(2026, 10, 21, 8, 0));
        assertThat(envios.get(2).usuario()).isEqualTo("Andrea Gómez");
    }
}
