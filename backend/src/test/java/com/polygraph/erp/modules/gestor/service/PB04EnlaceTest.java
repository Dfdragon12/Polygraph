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
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoEnlace;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PB-04: tipo de enlace (corto/completo), envío por canales con registro, y el recordatorio automático (uno solo por enlace). */
class PB04EnlaceTest {

    // ───────────── tipo de enlace ─────────────

    @Test
    @DisplayName("Solo Antecedentes → enlace CORTO; cualquier otro subproceso (o ninguno) → COMPLETO")
    void tipoDeEnlace() {
        assertThat(TipoEnlace.de(List.of("Antecedentes"))).isEqualTo(TipoEnlace.CORTO);
        assertThat(TipoEnlace.de(List.of("Antecedentes", "ANTECEDENTES legales"))).isEqualTo(TipoEnlace.CORTO);
        assertThat(TipoEnlace.de(List.of("Antecedentes", "Poligrafia Pre Empleo"))).isEqualTo(TipoEnlace.COMPLETO);
        assertThat(TipoEnlace.de(List.of("Validacion Laboral"))).isEqualTo(TipoEnlace.COMPLETO);
        assertThat(TipoEnlace.de(List.of())).isEqualTo(TipoEnlace.COMPLETO);
        assertThat(TipoEnlace.de(null)).isEqualTo(TipoEnlace.COMPLETO);
    }

    @Test
    @DisplayName("La etiqueta y las secciones del enlace corto son las del diseño: datos personales, 3 declaraciones judiciales y autorización")
    void seccionesDelCorto() {
        assertThat(EnvioEnlaceService.etiqueta(TipoEnlace.CORTO)).isEqualTo("Enlace corto · Antecedentes");
        assertThat(EnvioEnlaceService.etiqueta(TipoEnlace.COMPLETO)).isEqualTo("Enlace completo");
        assertThat(EnvioEnlaceService.secciones(TipoEnlace.CORTO)).extracting(EnvioEnlaceService.Seccion::titulo)
                .containsExactly("Datos personales", "Declaraciones judiciales", "Autorización");
    }

    // ───────────── envío ─────────────

    private EnvioEnlaceService servicio;
    private JdbcTemplate jdbc;
    private EmailService email;
    private CandidatoRepository candidatos;
    private LinkCandidato link;
    private Candidato candidato;
    private final Usuario gestor = Usuario.builder().idUsuario(5L).email("g@x.co").nombre("Andrea").rol(Rol.ADMIN_POLYGRAPH).build();

    @BeforeEach
    void setUp() {
        LinkCandidatoRepository links = mock(LinkCandidatoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        candidatos = mock(CandidatoRepository.class);
        EvaluadoService evaluado = mock(EvaluadoService.class);
        CalendarioHabilService calendario = mock(CalendarioHabilService.class);
        email = mock(EmailService.class);
        jdbc = mock(JdbcTemplate.class);
        servicio = new EnvioEnlaceService(links, usuarios, candidatos, mock(HojaVidaEvaluadoRepository.class), evaluado, calendario, email, jdbc);
        ReflectionTestUtils.setField(servicio, "frontendUrl", "https://app.polygraph.test");
        candidato = Candidato.builder().nombres("Diana Marcela").apellidos("Rueda").celular("+57 300 000 0054").emailPrincipal("diana@x.co").build();
        Servicio s = Servicio.builder().idServicio(54).cargo("Auxiliar contable").estado(EstadoServicio.PENDIENTE).candidato(candidato)
                .cliente(Cliente.builder().idCliente(1).nombreComercial("Demo Comercial S.A.S.").build()).build();
        link = LinkCandidato.builder().id(9L).servicio(s).token("TOKEN123").usado(false).revocado(false).intentosFallidos((short) 0)
                .fechaExpiracion(LocalDateTime.of(2026, 11, 12, 23, 59)).build();
        when(links.findById(9L)).thenReturn(Optional.of(link));
        when(usuarios.findByEmail("g@x.co")).thenReturn(Optional.of(gestor));
        when(evaluado.tipoDeEnlace(any())).thenReturn(TipoEnlace.COMPLETO);
        when(calendario.hoy()).thenReturn(LocalDate.of(2026, 11, 10));
        when(calendario.sumarDiasHabiles(LocalDate.of(2026, 11, 10), 2)).thenReturn(LocalDate.of(2026, 11, 12));
    }

    @Test
    @DisplayName("WhatsApp y correo: el correo sale, WhatsApp devuelve el enlace wa.me con el mensaje, y ambos quedan registrados con la fecha de referencia a 2 días hábiles")
    void enviarPorWhatsAppYCorreo() {
        var r = servicio.enviar(9L, new EnvioEnlaceService.EnviarRequest(null, null, null, Set.of("WHATSAPP", "CORREO")), "g@x.co");

        assertThat(r.enviados()).containsExactlyInAnyOrder("CORREO", "WHATSAPP");
        assertThat(r.whatsappUrl()).startsWith("https://wa.me/573000000054?text=").contains("Diana+Marcela").contains("TOKEN123");
        assertThat(r.venceReferencia()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(link.getFechaExpiracion().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(link.getEnviadoEn()).isNotNull();
        verify(email).enviarLinkEvaluado(eq("diana@x.co"), eq("Diana Marcela Rueda"), eq("TOKEN123"), any());
        verify(jdbc).update(contains("INSERT INTO envios_enlace"), eq(9L), eq("CORREO"), any(), any(), eq(5L), eq(false), any());
        verify(jdbc).update(contains("INSERT INTO envios_enlace"), eq(9L), eq("WHATSAPP"), any(), any(), eq(5L), eq(false), any());
    }

    @Test
    @DisplayName("Si el gestor corrige el celular o el correo, el cambio queda en el candidato y en el historial")
    void cambiarContactoQuedaRegistrado() {
        servicio.enviar(9L, new EnvioEnlaceService.EnviarRequest("+57 311 111 2222", "nuevo@x.co", null, Set.of("CORREO")), "g@x.co");
        assertThat(candidato.getCelular()).isEqualTo("+57 311 111 2222");
        assertThat(candidato.getEmailPrincipal()).isEqualTo("nuevo@x.co");
        verify(jdbc, times(2)).update(contains("INSERT INTO envios_enlace"), eq(9L), eq("CAMBIO_CONTACTO"), anyString(), anyString(), eq(5L), eq(false), any());
        verify(candidatos).save(candidato);
        verify(email).enviarLinkEvaluado(eq("nuevo@x.co"), any(), any(), any());
    }

    @Test
    @DisplayName("Sin canal elegido no se envía; un enlace revocado tampoco")
    void validaciones() {
        assertThatThrownBy(() -> servicio.enviar(9L, new EnvioEnlaceService.EnviarRequest(null, null, null, Set.of()), "g@x.co"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Elige por dónde");
        link.setRevocado(true);
        assertThatThrownBy(() -> servicio.enviar(9L, new EnvioEnlaceService.EnviarRequest(null, null, null, Set.of("CORREO")), "g@x.co"))
                .isInstanceOf(ApiException.class).hasMessageContaining("activo");
        verify(email, never()).enviarLinkEvaluado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Copiar el enlace deja registro de quién lo copió")
    void copiar() {
        servicio.copiado(9L, "g@x.co");
        verify(jdbc).update(contains("INSERT INTO envios_enlace"), eq(9L), eq("COPIA"), any(), any(), eq(5L), eq(false), any());
    }

    // ───────────── recordatorio ─────────────

    @Test
    @DisplayName("A las 24 h sin terminar sale UN recordatorio por correo y queda en el seguimiento; si otro proceso ya lo reclamó, no sale otro")
    void recordatorioUnico() {
        LinkCandidatoRepository links = mock(LinkCandidatoRepository.class);
        EnvioEnlaceService envio = mock(EnvioEnlaceService.class);
        EmailService correo = mock(EmailService.class);
        var job = new RecordatorioEnlaceJob(links, envio, correo, Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC));
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 11, 10, 0);
        when(links.pendientesDeRecordatorio(eq(ahora.minusHours(24)), anyInt(), any())).thenReturn(List.of(link));

        when(links.reclamarRecordatorio(9L, ahora)).thenReturn(1);
        assertThat(job.recordar(ahora)).isEqualTo(1);
        verify(correo).enviarRecordatorioLinkEvaluado(eq("diana@x.co"), eq("Diana Marcela Rueda"), eq("TOKEN123"), any());
        verify(envio).registrar(eq(9L), eq("RECORDATORIO"), eq("diana@x.co"), anyString(), any(), eq(true));

        when(links.reclamarRecordatorio(9L, ahora)).thenReturn(0);   // la siguiente corrida: ya reclamado
        assertThat(job.recordar(ahora)).isZero();
        verify(correo, times(1)).enviarRecordatorioLinkEvaluado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin correo del candidato no se envía nada, pero queda anotado y no se reintenta")
    void recordatorioSinCorreo() {
        candidato.setEmailPrincipal(" ");
        LinkCandidatoRepository links = mock(LinkCandidatoRepository.class);
        EnvioEnlaceService envio = mock(EnvioEnlaceService.class);
        EmailService correo = mock(EmailService.class);
        var job = new RecordatorioEnlaceJob(links, envio, correo, Clock.systemUTC());
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 11, 10, 0);
        when(links.pendientesDeRecordatorio(any(), anyInt(), any())).thenReturn(List.of(link));
        when(links.reclamarRecordatorio(9L, ahora)).thenReturn(1);
        assertThat(job.recordar(ahora)).isZero();
        verify(correo, never()).enviarRecordatorioLinkEvaluado(any(), any(), any(), any());
        verify(envio).registrar(eq(9L), eq("RECORDATORIO"), any(), contains("no tiene correo"), any(), eq(true));
    }

}
