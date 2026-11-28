package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.SolicitudPausa;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudPausaRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudReprogramacionRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.OrigenPausa;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.service.CalendarioHabilService;
import com.polygraph.erp.shared.service.FestivosColombiaGenerator;
import com.polygraph.erp.shared.service.ParametrosTiempo;
import com.polygraph.erp.shared.service.ParametrosTiempoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El semáforo y los avisos juntos (con el servicio de avisos de verdad): una notificación por transición, nunca una por cada recálculo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SemaforoTiempoService + avisos — una sola vez por transición")
class SemaforoTiempoAvisosTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private SolicitudPausaRepository pausaRepository;
    @Mock private SolicitudReprogramacionRepository reprogramacionRepository;
    @Mock private PlatformTransactionManager transacciones;
    @Mock private NotificacionService notificaciones;
    @Mock private UsuarioRepository usuarioRepository;

    /** Hoy = lunes 19-oct-2026. */
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("UTC"));
    private SemaforoTiempoService service;
    private final List<SolicitudPausa> pausas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var calendario = new CalendarioHabilService(anio -> FestivosColombiaGenerator.generar(anio).keySet(), () -> ParametrosTiempo.POR_DEFECTO, reloj);
        service = new SemaforoTiempoService(servicioRepository, pausaRepository, reprogramacionRepository, calendario,
                new ParametrosTiempoService(ParametrosTiempo.POR_DEFECTO), transacciones, new NotificacionesTiempoService(notificaciones, usuarioRepository), mock(com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository.class));
        when(pausaRepository.findByServicio_IdServicioOrderByInicio(any())).thenAnswer(inv -> new ArrayList<>(pausas));
        when(pausaRepository.saveAndFlush(any(SolicitudPausa.class))).thenAnswer(inv -> {
            SolicitudPausa p = inv.getArgument(0);
            pausas.add(p);
            return p;
        });
        when(pausaRepository.save(any(SolicitudPausa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reprogramacionRepository.findByServicio_IdServicioOrderByFechaDesc(any())).thenReturn(List.of());
        when(usuarioRepository.findByRolIn(any())).thenReturn(List.of(Usuario.builder().idUsuario(1L).rol(Rol.ADMIN_POLYGRAPH).activo(true).build()));
        when(usuarioRepository.findByIdCliente(5)).thenReturn(List.of(Usuario.builder().idUsuario(21L).rol(Rol.ADMIN_CLIENTE).activo(true).build()));
    }

    private Servicio solicitud(LocalDate compromiso, EstadoTiempo previo) {
        Cliente cliente = Cliente.builder().idCliente(5).razonSocial("Cliente S.A.S.").idGestor(7L).build();
        return Servicio.builder().idServicio(12).cliente(cliente).candidato(Candidato.builder().nombres("Ana").apellidos("Pérez").build())
                .estado(EstadoServicio.EN_EJECUCION).fechaEntregaEstimada(compromiso).fechaCompromisoOriginal(compromiso).diasPausaHabiles(0).estadoTiempo(previo).build();
    }

    private void avisosDeTipo(String tipo, int veces) {
        verify(notificaciones, times(veces)).crearParaUsuario(anyLong(), eq(tipo), anyString(), anyString(), eq("SERVICIO"), eq(12L));
    }

    @Test
    @DisplayName("Pasa a VENCIDA: se avisa al gestor, al administrador y al cliente; recalcular cada noche NO repite el aviso")
    void vencidaUnaSolaVez() {
        Servicio s = solicitud(LocalDate.of(2026, 10, 16), EstadoTiempo.POR_VENCER);      // el viernes pasado: hoy lunes ya lleva 1 día hábil
        service.recalcular(s);
        avisosDeTipo("TIEMPO_VENCIDA", 3);
        service.recalcular(s);                                                             // la noche siguiente: sigue vencida
        service.recalcular(s);
        avisosDeTipo("TIEMPO_VENCIDA", 3);                                                 // sigue siendo 3: no se repitió
    }

    @Test
    @DisplayName("Pasa a POR_VENCER: se avisa al gestor y al cliente una sola vez")
    void porVencerUnaSolaVez() {
        Servicio s = solicitud(LocalDate.of(2026, 10, 19), EstadoTiempo.A_TIEMPO);        // vence hoy
        service.recalcular(s);
        avisosDeTipo("TIEMPO_POR_VENCER", 2);
        service.recalcular(s);
        avisosDeTipo("TIEMPO_POR_VENCER", 2);
        avisosDeTipo("TIEMPO_VENCIDA", 0);
    }

    @Test
    @DisplayName("El primer cálculo de una solicitud (o del histórico) no avisa nada, aunque ya esté vencida")
    void primerCalculoNoAvisa() {
        Servicio s = solicitud(LocalDate.of(2026, 9, 23), null);
        service.recalcular(s);
        verify(notificaciones, never()).crearParaUsuario(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Abrir y cerrar una pausa avisa al gestor, cada una una vez; la de espera de hoja de vida no")
    void pausas() {
        Servicio s = solicitud(LocalDate.of(2026, 10, 30), EstadoTiempo.A_TIEMPO);
        service.abrirPausa(s, OrigenPausa.CANDIDATO, "Novedad #4: Candidato pidió reprogramar", "poligrafista@polygraph.co", LocalDateTime.of(2026, 10, 14, 9, 0));
        service.abrirPausa(s, OrigenPausa.CANDIDATO, "Novedad #4: Candidato pidió reprogramar", "poligrafista@polygraph.co", null);   // ya abierta: nada nuevo
        avisosDeTipo("TIEMPO_PAUSA_ABIERTA", 1);
        service.cerrarPausas(s, "Novedad #4: Candidato pidió reprogramar");
        service.cerrarPausas(s, "Novedad #4: Candidato pidió reprogramar");                // ya cerrada: nada nuevo
        avisosDeTipo("TIEMPO_PAUSA_CERRADA", 1);

        service.abrirPausa(s, OrigenPausa.CANDIDATO, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA, "sistema", null);
        service.cerrarPausas(s, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA);
        avisosDeTipo("TIEMPO_PAUSA_ABIERTA", 1);
        avisosDeTipo("TIEMPO_PAUSA_CERRADA", 1);
        verify(notificaciones, atLeastOnce()).crearParaUsuario(eq(7L), anyString(), anyString(), anyString(), anyString(), anyLong());
    }
}
