package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.SolicitudPausa;
import com.polygraph.erp.modules.servicios.entity.SolicitudReprogramacion;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudPausaRepository;
import com.polygraph.erp.modules.servicios.repository.SolicitudReprogramacionRepository;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.ImputableA;
import com.polygraph.erp.shared.enums.OrigenPausa;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SemaforoTiempoService — calcula y guarda el estado de tiempo")
class SemaforoTiempoServiceTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private SolicitudPausaRepository pausaRepository;
    @Mock private SolicitudReprogramacionRepository reprogramacionRepository;
    @Mock private PlatformTransactionManager transacciones;
    @Mock private NotificacionesTiempoService avisos;
    @Mock private com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository subprocesoRepository;

    /** Hoy = lunes 19-oct-2026, 10:00 en Bogotá. */
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("UTC"));
    private SemaforoTiempoService service;
    private final List<SolicitudPausa> pausas = new ArrayList<>();
    private final List<SolicitudReprogramacion> reprogramaciones = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var calendario = new CalendarioHabilService(anio -> FestivosColombiaGenerator.generar(anio).keySet(), () -> ParametrosTiempo.POR_DEFECTO, reloj);
        service = new SemaforoTiempoService(servicioRepository, pausaRepository, reprogramacionRepository, calendario,
                new ParametrosTiempoService(ParametrosTiempo.POR_DEFECTO), transacciones, avisos, subprocesoRepository);
        when(pausaRepository.findByServicio_IdServicioOrderByInicio(any())).thenAnswer(inv -> new ArrayList<>(pausas));
        when(pausaRepository.saveAndFlush(any(SolicitudPausa.class))).thenAnswer(inv -> {
            SolicitudPausa p = inv.getArgument(0);
            pausas.add(p);
            return p;
        });
        when(pausaRepository.save(any(SolicitudPausa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reprogramacionRepository.findByServicio_IdServicioOrderByFechaDesc(any())).thenAnswer(inv -> new ArrayList<>(reprogramaciones));
    }

    private static Servicio solicitud(EstadoServicio etapa, LocalDate compromiso) {
        return Servicio.builder().idServicio(1).estado(etapa).fechaEntregaEstimada(compromiso).fechaCompromisoOriginal(compromiso).diasPausaHabiles(0).build();
    }

    @Test
    @DisplayName("Guarda estado, días de retraso, fecha de compromiso ajustada y cuándo se calculó")
    void guardaElResultado() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 9, 23));
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(s.getDiasRetrasoHabiles()).isEqualTo(17);
        assertThat(s.getFechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(s.getDiasPausaHabiles()).isZero();
        assertThat(s.getEstadoTiempoActualizadoEn()).isEqualTo(LocalDateTime.of(2026, 10, 19, 10, 0));
    }

    @Test
    @DisplayName("Abrir y cerrar una pausa recalcula: la solicitud queda EN_PAUSA y al cerrarse la fecha de compromiso se corre")
    void pausa() {
        Servicio s = solicitud(EstadoServicio.PROGRAMANDO, LocalDate.of(2026, 10, 21));
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.A_TIEMPO);

        service.abrirPausa(s, OrigenPausa.CANDIDATO, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA, "sistema", LocalDateTime.of(2026, 10, 14, 9, 0));
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.EN_PAUSA);
        assertThat(pausas).hasSize(1);

        // otra vez con el mismo motivo: no abre una segunda
        service.abrirPausa(s, OrigenPausa.CANDIDATO, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA, "sistema", null);
        assertThat(pausas).hasSize(1);

        assertThat(service.cerrarPausas(s, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA)).isEqualTo(1);
        assertThat(pausas.get(0).getFin()).isEqualTo(LocalDateTime.of(2026, 10, 19, 10, 0));
        assertThat(s.getEstadoTiempo()).isNotEqualTo(EstadoTiempo.EN_PAUSA);
        assertThat(s.getDiasPausaHabiles()).isEqualTo(3);                                        // del 14 al 19 (excluido): 14, 15 y 16
        assertThat(s.getFechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 10, 26));       // 21-oct + 3 días hábiles: 22, 23 y 26
        assertThat(service.cerrarPausas(s, SemaforoTiempoService.MOTIVO_HOJA_DE_VIDA)).isZero();
    }

    @Test
    @DisplayName("Al publicar congela la entrega real y el cumplimiento, y no se recalculan aunque pase el tiempo")
    void publicarCongela() {
        Servicio s = solicitud(EstadoServicio.FINALIZADO, LocalDate.of(2026, 10, 16));
        s.setEstado(EstadoServicio.PUBLICADO);
        service.recalcular(s);                                           // se publica "hoy": lunes 19-oct, 1 día hábil después del compromiso
        assertThat(s.getFechaEntregaReal()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(s.getCumplimiento()).isEqualTo(Cumplimiento.TARDE);
        assertThat(s.getDiasRetrasoHabiles()).isEqualTo(1);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.ENTREGADA_TARDE);

        LocalDateTime calculado = s.getEstadoTiempoActualizadoEn();
        s.setFechaEntregaEstimada(LocalDate.of(2026, 12, 1));            // aunque cambien los datos, lo entregado no se recalcula
        service.recalcular(s);
        assertThat(s.getCumplimiento()).isEqualTo(Cumplimiento.TARDE);
        assertThat(s.getDiasRetrasoHabiles()).isEqualTo(1);
        assertThat(s.getEstadoTiempoActualizadoEn()).isEqualTo(calculado);
    }

    @Test
    @DisplayName("Publicada a tiempo")
    void publicadaATiempo() {
        Servicio s = solicitud(EstadoServicio.PUBLICADO, LocalDate.of(2026, 10, 19));
        service.recalcular(s);
        assertThat(s.getCumplimiento()).isEqualTo(Cumplimiento.A_TIEMPO);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.ENTREGADA_A_TIEMPO);
        assertThat(s.getDiasRetrasoHabiles()).isZero();
    }

    @Test
    @DisplayName("Si la solicitud sale de PUBLICADO (reversión) se borra la entrega y vuelve a calcularse como abierta")
    void reversion() {
        Servicio s = solicitud(EstadoServicio.PUBLICADO, LocalDate.of(2026, 10, 16));
        service.recalcular(s);
        assertThat(s.getFechaEntregaReal()).isNotNull();
        s.setEstado(EstadoServicio.FINALIZADO);
        service.recalcular(s);
        assertThat(s.getFechaEntregaReal()).isNull();
        assertThat(s.getCumplimiento()).isNull();
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
    }

    @Test
    @DisplayName("Reprogramación: por causa de Polygraph se mide contra la original; por causa del cliente, contra la nueva")
    void reprogramacion() {
        Servicio s = solicitud(EstadoServicio.REPROGRAMADO, LocalDate.of(2026, 10, 16));
        s.setFechaCompromisoOriginal(LocalDate.of(2026, 10, 9));
        reprogramaciones.add(SolicitudReprogramacion.builder().responsable(ImputableA.POLYGRAPH).build());
        service.recalcular(s);
        assertThat(s.getFechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);

        reprogramaciones.clear();
        reprogramaciones.add(SolicitudReprogramacion.builder().responsable(ImputableA.CLIENTE).build());
        service.recalcular(s);
        assertThat(s.getFechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(s.getDiasRetrasoHabiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancelada: sin estado de tiempo")
    void cancelada() {
        Servicio s = solicitud(EstadoServicio.CANCELADO, LocalDate.of(2026, 9, 1));
        s.setEstadoTiempo(EstadoTiempo.VENCIDA);
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isNull();
        assertThat(s.getDiasRetrasoHabiles()).isNull();
    }

    @Test
    @DisplayName("El recálculo diario no se detiene por una solicitud que falla: sigue con las demás y reporta cuál falló")
    void recalculoDiarioConErrores() {
        Servicio uno = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 9, 23));
        when(servicioRepository.idsAbiertos(anyList())).thenReturn(List.of(1, 2));
        when(servicioRepository.findAllById(List.of(1, 2))).thenThrow(new IllegalStateException("lote roto"));
        when(servicioRepository.findAllById(List.of(1))).thenReturn(List.of(uno));
        when(servicioRepository.findAllById(List.of(2))).thenThrow(new IllegalStateException("solicitud mala"));

        var r = service.recalcularEnCurso();
        assertThat(r.procesadas()).isEqualTo(2);
        assertThat(r.cambiadas()).isEqualTo(1);
        assertThat(r.errores()).hasSize(1);
        assertThat(r.errores().get(0)).contains("Solicitud 2").contains("solicitud mala");
        assertThat(uno.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
    }

    @Test
    @DisplayName("Idempotente: repetir el recálculo no cambia nada")
    void idempotente() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 9, 23));
        when(servicioRepository.idsAbiertos(anyList())).thenReturn(List.of(1));
        when(servicioRepository.findAllById(List.of(1))).thenReturn(List.of(s));
        assertThat(service.recalcularEnCurso().cambiadas()).isEqualTo(1);
        assertThat(service.recalcularEnCurso().cambiadas()).isZero();
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
    }

    // ───────────────────────── SE-05 ─────────────────────────

    private void subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso... estados) {
        List<com.polygraph.erp.modules.servicios.entity.ServicioSubproceso> lista = new ArrayList<>();
        long id = 1;
        for (var e : estados) {
            lista.add(com.polygraph.erp.modules.servicios.entity.ServicioSubproceso.builder().id(id++).estado(e)
                    .fechaPublicado(e == com.polygraph.erp.shared.enums.EstadoSubproceso.PUBLICADO ? LocalDateTime.of(2026, 10, 15, 16, 0) : null).build());
        }
        when(subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(any())).thenReturn(lista);
    }

    @Test
    @DisplayName("Un subproceso en REPROGRAMADO pausa el reloj del servicio; al dejar de estarlo la fecha se corre los días hábiles que duró")
    void pausaPorReprogramacion() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 10, 21));
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.EN_EJECUCION, com.polygraph.erp.shared.enums.EstadoSubproceso.REPROGRAMADO);
        pausas.add(SolicitudPausa.builder().servicio(s).inicio(LocalDateTime.of(2026, 10, 14, 9, 0)).motivo(SemaforoTiempoService.MOTIVO_REPROGRAMACION)
                .origen(OrigenPausa.REPROGRAMACION).build());
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.EN_PAUSA);
        assertThat(s.getPausaOrigen()).isEqualTo(OrigenPausa.REPROGRAMACION);

        // sincronizar abre la pausa si no hay una (idempotente) y no abre una segunda
        pausas.clear();
        service.sincronizarPausaPorReprogramacion(s);
        service.sincronizarPausaPorReprogramacion(s);
        assertThat(pausas).hasSize(1);
        assertThat(pausas.get(0).getOrigen()).isEqualTo(OrigenPausa.REPROGRAMACION);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.EN_PAUSA);
    }

    @Test
    @DisplayName("Cuando ya no hay subprocesos reprogramados la pausa se cierra y la fecha de compromiso se corre 3 días hábiles")
    void pausaPorReprogramacionSeCierra() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 10, 21));
        pausas.add(SolicitudPausa.builder().servicio(s).inicio(LocalDateTime.of(2026, 10, 14, 9, 0)).motivo(SemaforoTiempoService.MOTIVO_REPROGRAMACION)
                .origen(OrigenPausa.REPROGRAMACION).build());
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.EN_EJECUCION, com.polygraph.erp.shared.enums.EstadoSubproceso.EN_EJECUCION);
        service.sincronizarPausaPorReprogramacion(s);
        assertThat(pausas.get(0).getFin()).isEqualTo(LocalDateTime.of(2026, 10, 19, 10, 0));
        assertThat(s.getDiasPausaHabiles()).isEqualTo(3);                                     // 14, 15 y 16
        assertThat(s.getFechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 10, 26));    // 21-oct + 3 días hábiles
    }

    @Test
    @DisplayName("Una pausa que empieza y termina el mismo día no corre la fecha")
    void pausaDeUnDiaNoCorre() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 10, 21));
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.REPROGRAMADO);
        service.sincronizarPausaPorReprogramacion(s);
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.EN_EJECUCION);
        service.sincronizarPausaPorReprogramacion(s);
        assertThat(s.getDiasPausaHabiles()).isZero();
        assertThat(s.getFechaCompromisoAjustada()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("El reloj sigue mientras haya subprocesos en FINALIZADO: no está entregada hasta que TODOS estén publicados")
    void finalizadoNoEntrega() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 10, 16));
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.PUBLICADO, com.polygraph.erp.shared.enums.EstadoSubproceso.FINALIZADO);
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
        assertThat(s.getFechaEntregaReal()).isNull();
        assertThat(s.getCumplimiento()).isNull();
    }

    @Test
    @DisplayName("Estudio completo publicado: se congela como entregado con la fecha en que se publicó el último subproceso (aunque el servicio no se haya publicado)")
    void estudioCompletoPublicadoEntrega() {
        // compromiso 16-oct, el último subproceso se publicó el 15-oct (antes del compromiso): a tiempo, aunque hoy sea 19-oct
        Servicio s = solicitud(EstadoServicio.FINALIZADO, LocalDate.of(2026, 10, 16));
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.PUBLICADO, com.polygraph.erp.shared.enums.EstadoSubproceso.PUBLICADO,
                com.polygraph.erp.shared.enums.EstadoSubproceso.CANCELADO);
        service.recalcular(s);
        assertThat(s.getFechaEntregaReal()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.ENTREGADA_A_TIEMPO);
        assertThat(s.getCumplimiento()).isEqualTo(Cumplimiento.A_TIEMPO);
        // congelado: no cambia aunque pase el tiempo
        LocalDateTime calculado = s.getEstadoTiempoActualizadoEn();
        service.recalcular(s);
        assertThat(s.getEstadoTiempoActualizadoEn()).isEqualTo(calculado);
    }

    @Test
    @DisplayName("Entregada tarde: la publicación del último subproceso fue después del compromiso")
    void estudioCompletoPublicadoTarde() {
        Servicio s = solicitud(EstadoServicio.FINALIZADO, LocalDate.of(2026, 10, 9));      // compromiso viernes 9-oct; publicado el jueves 15-oct
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.PUBLICADO);
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.ENTREGADA_TARDE);
        assertThat(s.getCumplimiento()).isEqualTo(Cumplimiento.TARDE);
        assertThat(s.getDiasRetrasoHabiles()).isEqualTo(3);                                // 13, 14 y 15 (el lunes 12-oct es festivo)
    }

    @Test
    @DisplayName("Sin subprocesos no hay «estudio completo publicado» y un cancelado nunca se entrega")
    void sinSubprocesosNiCancelados() {
        Servicio s = solicitud(EstadoServicio.EN_EJECUCION, LocalDate.of(2026, 10, 16));
        subprocesos();
        service.recalcular(s);
        assertThat(s.getEstadoTiempo()).isEqualTo(EstadoTiempo.VENCIDA);
        Servicio cancelado = solicitud(EstadoServicio.CANCELADO, LocalDate.of(2026, 10, 16));
        subprocesos(com.polygraph.erp.shared.enums.EstadoSubproceso.PUBLICADO);
        service.recalcular(cancelado);
        assertThat(cancelado.getEstadoTiempo()).isNull();
    }
}
