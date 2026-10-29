package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.SolicitudPausa;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.OrigenPausa;
import com.polygraph.erp.shared.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificacionesTiempoService — un aviso por transición")
class NotificacionesTiempoServiceTest {

    private static final long GESTOR = 7L;
    private static final long ADMIN = 1L;
    private static final long CLIENTE_1 = 21L;
    private static final long CLIENTE_2 = 22L;

    @Mock private NotificacionService notificaciones;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private NotificacionesTiempoService avisos;

    private Servicio s;

    @BeforeEach
    void datos() {
        Cliente cliente = Cliente.builder().idCliente(5).razonSocial("Cliente S.A.S.").idGestor(GESTOR).build();
        Candidato candidato = Candidato.builder().nombres("Ana").apellidos("Pérez").build();
        s = Servicio.builder().idServicio(12).cliente(cliente).candidato(candidato).fechaCompromisoAjustada(LocalDate.of(2026, 9, 23))
                .diasRetrasoHabiles(17).diasRestantesHabiles(1).build();
        when(usuarioRepository.findByRolIn(List.of(Rol.ADMIN_POLYGRAPH))).thenReturn(List.of(usuario(ADMIN, Rol.ADMIN_POLYGRAPH, true)));
        when(usuarioRepository.findByIdCliente(5)).thenReturn(List.of(usuario(CLIENTE_1, Rol.ADMIN_CLIENTE, true), usuario(CLIENTE_2, Rol.ANALISTA_CLIENTE, true),
                usuario(99L, Rol.ANALISTA_CLIENTE, false)));
    }

    private static Usuario usuario(long id, Rol rol, boolean activo) {
        return Usuario.builder().idUsuario(id).rol(rol).activo(activo).build();
    }

    private List<String> destinatarios(String tipo) {
        ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
        verify(notificaciones, org.mockito.Mockito.atLeast(0)).crearParaUsuario(ids.capture(), eq(tipo), anyString(), anyString(), eq("SERVICIO"), eq(12L));
        return ids.getAllValues().stream().map(String::valueOf).toList();
    }

    private String mensajeA(long idUsuario, String tipo) {
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).crearParaUsuario(eq(idUsuario), eq(tipo), anyString(), mensaje.capture(), eq("SERVICIO"), eq(12L));
        return mensaje.getValue();
    }

    @Test
    @DisplayName("Pasa a POR_VENCER: avisa al gestor y a los usuarios activos del cliente, no a los administradores")
    void porVencer() {
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.A_TIEMPO, EstadoTiempo.POR_VENCER);
        assertThat(destinatarios("TIEMPO_POR_VENCER")).containsExactlyInAnyOrder("7", "21", "22");
        String alGestor = mensajeA(GESTOR, "TIEMPO_POR_VENCER");
        assertThat(alGestor).contains("La solicitud #12 de Ana Pérez (Cliente S.A.S.)").contains("23 de septiembre de 2026").doesNotContain("2026-09-23");
        assertThat(mensajeA(CLIENTE_1, "TIEMPO_POR_VENCER")).contains("Ana Pérez").contains("23 de septiembre de 2026");
    }

    @Test
    @DisplayName("Por vencer hoy lo dice con esas palabras")
    void porVencerHoy() {
        s.setDiasRestantesHabiles(0);
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.A_TIEMPO, EstadoTiempo.POR_VENCER);
        assertThat(mensajeA(GESTOR, "TIEMPO_POR_VENCER")).contains("vence hoy");
    }

    @Test
    @DisplayName("Pasa a VENCIDA: avisa al gestor, a los administradores y al cliente, con los días hábiles de retraso y la fecha legible")
    void vencida() {
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.POR_VENCER, EstadoTiempo.VENCIDA);
        assertThat(destinatarios("TIEMPO_VENCIDA")).containsExactlyInAnyOrder("7", "1", "21", "22");
        assertThat(mensajeA(ADMIN, "TIEMPO_VENCIDA")).contains("pasó su fecha de compromiso (23 de septiembre de 2026)").contains("17 días hábiles de retraso");
        assertThat(mensajeA(CLIENTE_2, "TIEMPO_VENCIDA")).contains("23 de septiembre de 2026").contains("17 días hábiles");
    }

    @Test
    @DisplayName("Una sola vez: sin cambio de estado, o en el primer cálculo, no se avisa nada")
    void soloEnLaTransicion() {
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.VENCIDA, EstadoTiempo.VENCIDA);          // el recálculo de la noche
        avisos.alCambiarEstadoTiempo(s, null, EstadoTiempo.VENCIDA);                           // primer cálculo (ej. el histórico)
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.VENCIDA, null);
        verify(notificaciones, never()).crearParaUsuario(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Los demás cambios (a tiempo, en pausa, entregada) no generan aviso de tiempo")
    void otrosCambios() {
        for (EstadoTiempo e : List.of(EstadoTiempo.A_TIEMPO, EstadoTiempo.EN_PAUSA, EstadoTiempo.ENTREGADA_A_TIEMPO, EstadoTiempo.ENTREGADA_TARDE)) {
            avisos.alCambiarEstadoTiempo(s, EstadoTiempo.VENCIDA, e);
        }
        verify(notificaciones, never()).crearParaUsuario(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Reprogramación: solo al cliente, con la fecha anterior, la nueva y el motivo, en fechas legibles")
    void reprogramacion() {
        avisos.alReprogramar(s, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 30), "El laboratorio externo no entregó el reporte");
        assertThat(destinatarios("SOLICITUD_REPROGRAMADA")).containsExactlyInAnyOrder("21", "22");
        String m = mensajeA(CLIENTE_1, "SOLICITUD_REPROGRAMADA");
        assertThat(m).contains("23 de septiembre de 2026").contains("30 de septiembre de 2026").contains("Motivo: El laboratorio externo no entregó el reporte")
                .doesNotContain("2026-09");
        verify(notificaciones, never()).crearParaUsuario(eq(GESTOR), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    private SolicitudPausa pausa(String motivo) {
        return SolicitudPausa.builder().motivo(motivo).origen(OrigenPausa.CLIENTE).inicio(LocalDateTime.of(2026, 9, 28, 9, 0)).build();
    }

    @Test
    @DisplayName("Se abre una pausa: avisa al gestor (solo al gestor)")
    void pausaAbierta() {
        avisos.alAbrirPausa(s, pausa("Novedad #4: Candidato pidió reprogramar"));
        assertThat(destinatarios("TIEMPO_PAUSA_ABIERTA")).containsExactly("7");
        assertThat(mensajeA(GESTOR, "TIEMPO_PAUSA_ABIERTA")).contains("novedad #4: Candidato pidió reprogramar").contains("Ana Pérez");
    }

    @Test
    @DisplayName("Se cierra una pausa: avisa al gestor con cuándo empezó y la fecha de compromiso, en fechas legibles")
    void pausaCerrada() {
        avisos.alCerrarPausa(s, pausa("Novedad #4: Candidato pidió reprogramar"));
        assertThat(destinatarios("TIEMPO_PAUSA_CERRADA")).containsExactly("7");
        assertThat(mensajeA(GESTOR, "TIEMPO_PAUSA_CERRADA")).contains("empezó el 28 de septiembre de 2026").contains("La fecha de compromiso es el 23 de septiembre de 2026");
    }

    @Test
    @DisplayName("La pausa automática de espera de la hoja de vida no genera aviso (el gestor ya recibe los de la solicitud nueva y la hoja completada)")
    void pausaDeHojaDeVidaNoAvisa() {
        avisos.alAbrirPausa(s, pausa(SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA));
        avisos.alCerrarPausa(s, pausa(SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA));
        verify(notificaciones, never()).crearParaUsuario(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Un cliente sin gestor asignado no rompe nada: solo se avisa al cliente")
    void sinGestor() {
        s.getCliente().setIdGestor(null);
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.A_TIEMPO, EstadoTiempo.POR_VENCER);
        assertThat(destinatarios("TIEMPO_POR_VENCER")).containsExactlyInAnyOrder("21", "22");
    }

    @Test
    @DisplayName("Si avisar falla, el cálculo del tiempo no se detiene")
    void fallaAlAvisar() {
        org.mockito.Mockito.doThrow(new IllegalStateException("sin campana")).when(notificaciones)
                .crearParaUsuario(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
        avisos.alCambiarEstadoTiempo(s, EstadoTiempo.A_TIEMPO, EstadoTiempo.VENCIDA);          // no lanza
        avisos.alReprogramar(s, LocalDate.now(), LocalDate.now().plusDays(1), "x");              // no lanza
        verify(notificaciones, org.mockito.Mockito.atLeastOnce()).crearParaUsuario(any(), any(), any(), any(), any(), any());
    }
}
