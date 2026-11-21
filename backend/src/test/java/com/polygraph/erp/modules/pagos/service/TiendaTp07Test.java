package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.tienda.service.CalculadoraVigencia;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-07: vigencia y lotes de compra: vencimiento, un lote por pago, consumo por vencimiento, concurrencia y vencimiento de unidades. */
class TiendaTp07Test {

    private final Proceso proceso = Proceso.builder().idProceso(10).nombreProceso("Antecedentes").build();
    private final Cliente cliente = Cliente.builder().idCliente(1).build();
    private SaldoServicioClienteRepository saldos;
    private LotePrepagoRepository lotes;
    private ParametrosTiendaService parametros;
    private NotificacionService notificaciones;

    /** Compra aprobada «ahora» (UTC); en Bogotá es 5 horas antes. */
    private SaldoServicioClienteService servicio(String instanteUtc) {
        Clock reloj = Clock.fixed(Instant.parse(instanteUtc), ZoneOffset.UTC);
        return new SaldoServicioClienteService(saldos, new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class)), lotes, reloj, parametros);
    }

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        lotes = mock(LotePrepagoRepository.class);
        parametros = mock(ParametrosTiendaService.class);
        notificaciones = mock(NotificacionService.class);
        when(parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)).thenReturn(true);
        when(parametros.entero("diasAlertaVencimiento")).thenReturn(30);
    }

    // ───────────── vencimiento (pura) ─────────────

    @Test
    @DisplayName("Compra del 1 de noviembre: 3 meses → 1 de febrero; 6 → 1 de mayo; 12 → 1 de noviembre del año siguiente; sin vigencia → no vence")
    void vencimientosDelPlan() {
        LocalDate compra = LocalDate.of(2026, 11, 1);
        assertThat(CalculadoraVigencia.vencimiento(compra, 3)).isEqualTo(LocalDateTime.of(2027, 2, 1, 23, 59, 59));
        assertThat(CalculadoraVigencia.vencimiento(compra, 6)).isEqualTo(LocalDateTime.of(2027, 5, 1, 23, 59, 59));
        assertThat(CalculadoraVigencia.vencimiento(compra, 12)).isEqualTo(LocalDateTime.of(2027, 11, 1, 23, 59, 59));
        assertThat(CalculadoraVigencia.vencimiento(compra, null)).isNull();
    }

    @Test
    @DisplayName("Compra del 31 de enero + 3 meses = 30 de abril (el mes de destino no tiene día 31)")
    void finDeMes() {
        assertThat(CalculadoraVigencia.vencimiento(LocalDate.of(2027, 1, 31), 3)).isEqualTo(LocalDateTime.of(2027, 4, 30, 23, 59, 59));
        assertThat(CalculadoraVigencia.vencimiento(LocalDate.of(2027, 11, 30), 3)).isEqualTo(LocalDateTime.of(2028, 2, 29, 23, 59, 59));   // 2028 bisiesto
    }

    @Test
    @DisplayName("Vence al final del día: el último día todavía se puede usar y un segundo después ya no")
    void finDelDia() {
        LocalDateTime v = LocalDateTime.of(2027, 2, 1, 23, 59, 59);
        assertThat(CalculadoraVigencia.vencido(v, LocalDateTime.of(2027, 2, 1, 23, 59, 59))).isFalse();
        assertThat(CalculadoraVigencia.vencido(v, LocalDateTime.of(2027, 2, 2, 0, 0, 0))).isTrue();
        assertThat(CalculadoraVigencia.vencido(null, LocalDateTime.of(2099, 1, 1, 0, 0))).isFalse();
    }

    // ───────────── creación del lote al aprobarse el pago ─────────────

    private OrdenCompraItem item(long id, int cantidad, Integer meses) {
        return OrdenCompraItem.builder().idItem(id).cantidad(cantidad).valorUnitario(new BigDecimal("35000")).valorOriginal(new BigDecimal("40000"))
                .cobertura("LOCAL").vigenciaMeses(meses).build();
    }

    private void saldoVacio() {
        when(saldos.findByCliente_IdClienteAndProceso_IdProceso(1, 10)).thenReturn(Optional.empty());
    }

    private LotePrepago acreditarYCapturar(SaldoServicioClienteService s, OrdenCompraItem it, String transaccion) {
        saldoVacio();
        when(lotes.existsByItem_IdItem(it.getIdItem())).thenReturn(false);
        s.acreditar(cliente, proceso, it.getCantidad(), it, transaccion);
        ArgumentCaptor<LotePrepago> c = ArgumentCaptor.forClass(LotePrepago.class);
        verify(lotes).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("El lote nace al aprobarse: fecha de compra = aprobación (hora de Bogotá), vencimiento = +vigencia a las 23:59:59, con precio y transacción")
    void loteConVencimiento() {
        // 1 nov 2026 22:00 en Bogotá = 2 nov 03:00 UTC: la compra cuenta como 1 de noviembre
        LotePrepago l = acreditarYCapturar(servicio("2026-11-02T03:00:00Z"), item(7, 20, 6), "WOMPI-1");
        assertThat(l.getFechaCompra()).isEqualTo(LocalDateTime.of(2026, 11, 1, 22, 0));
        assertThat(l.getFechaVencimiento()).isEqualTo(LocalDateTime.of(2027, 5, 1, 23, 59, 59));
        assertThat(l.getCantidadComprada()).isEqualTo(20);
        assertThat(l.getValorUnitario()).isEqualByComparingTo("35000");
        assertThat(l.getIdTransaccionPago()).isEqualTo("WOMPI-1");
        assertThat(l.getCobertura()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("Plan sin vigencia (200 unidades): el lote no tiene fecha de vencimiento y nunca vence")
    void loteSinVencimiento() {
        LotePrepago l = acreditarYCapturar(servicio("2026-11-01T15:00:00Z"), item(8, 200, null), "WOMPI-2");
        assertThat(l.getFechaVencimiento()).isNull();
    }

    @Test
    @DisplayName("El mismo aviso de pago dos veces crea el lote una sola vez y no vuelve a sumar al saldo")
    void avisoRepetidoNoDuplica() {
        var s = servicio("2026-11-01T15:00:00Z");
        var it = item(9, 5, 3);
        saldoVacio();
        when(lotes.existsByItem_IdItem(9L)).thenReturn(false);
        s.acreditar(cliente, proceso, 5, it, "WOMPI-3");
        verify(lotes, times(1)).save(any(LotePrepago.class));
        verify(saldos, times(1)).save(any(SaldoServicioCliente.class));

        // llega otra vez el mismo aviso (el lote ya existe para esa transacción e ítem)
        when(lotes.existsByItem_IdItem(9L)).thenReturn(true);
        s.acreditar(cliente, proceso, 5, it, "WOMPI-3");
        verify(lotes, times(1)).save(any(LotePrepago.class));
        verify(saldos, times(1)).save(any(SaldoServicioCliente.class));
    }

    @Test
    @DisplayName("Misma transacción e ítem sin pasar por el ítem (otra vía): la restricción por (transacción, ítem) también lo frena")
    void unicoPorTransaccionEItem() {
        var s = servicio("2026-11-01T15:00:00Z");
        when(lotes.existsByItem_IdItem(11L)).thenReturn(false);
        when(lotes.existsByIdTransaccionPagoAndItem_IdItem("WOMPI-4", 11L)).thenReturn(true);
        s.acreditar(cliente, proceso, 5, item(11, 5, 3), "WOMPI-4");
        verify(lotes, never()).save(any(LotePrepago.class));
        verify(saldos, never()).save(any(SaldoServicioCliente.class));
    }

    // ───────────── consumo: el que vence primero, los que no vencen al final ─────────────

    private LotePrepago lote(long id, int comprada, int consumida, LocalDateTime vence, int diaCompra) {
        return LotePrepago.builder().idLote(id).cliente(cliente).proceso(proceso).cantidadComprada(comprada).cantidadConsumida(consumida)
                .valorUnitario(new BigDecimal(1000 + id)).fechaCompra(LocalDateTime.of(2026, 10, diaCompra, 9, 0)).fechaVencimiento(vence).build();
    }

    private void saldoConDescuentoAtomico(int disponibles) {
        AtomicInteger quedan = new AtomicInteger(disponibles);
        when(saldos.descontarUna(eq(1), eq(10), any())).thenAnswer(i -> quedan.getAndUpdate(n -> n > 0 ? n - 1 : n) > 0 ? 1 : 0);
    }

    @Test
    @DisplayName("Tres lotes (vence en enero, vence en junio, sin vencimiento): consume primero el de enero, luego el de junio y al final el que no vence")
    void ordenPorVencimiento() {
        var sinVencer = lote(1, 1, 0, null, 1);            // el más antiguo, pero no vence: va al final
        var junio = lote(2, 1, 0, LocalDateTime.of(2027, 6, 30, 23, 59, 59), 5);
        var enero = lote(3, 1, 0, LocalDateTime.of(2027, 1, 31, 23, 59, 59), 20);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(sinVencer, junio, enero));
        saldoConDescuentoAtomico(3);
        var s = servicio("2026-10-30T15:00:00Z");

        assertThat(s.descontar(1, proceso).idLote()).isEqualTo(3L);   // enero
        assertThat(s.descontar(1, proceso).idLote()).isEqualTo(2L);   // junio
        assertThat(s.descontar(1, proceso).idLote()).isEqualTo(1L);   // el que no vence
    }

    @Test
    @DisplayName("Entre lotes que vencen el mismo día, el más antiguo primero (FIFO)")
    void empatePorAntiguedad() {
        var v = LocalDateTime.of(2027, 3, 1, 23, 59, 59);
        var nuevo = lote(1, 1, 0, v, 20);
        var viejo = lote(2, 1, 0, v, 2);
        assertThat(AsignadorLotes.siguiente(List.of(nuevo, viejo), LocalDateTime.of(2026, 10, 30, 10, 0)).orElseThrow().getIdLote()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Un lote ya vencido no se usa aunque el job no haya corrido: sus unidades se pasan a vencidas y se usa el siguiente")
    void loteVencidoNoSeUsa() {
        var vencido = lote(1, 3, 1, LocalDateTime.of(2026, 10, 15, 23, 59, 59), 1);   // 2 sin usar, ya vencido
        var vigente = lote(2, 5, 0, LocalDateTime.of(2027, 5, 1, 23, 59, 59), 10);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(vencido, vigente));
        saldoConDescuentoAtomico(5);
        when(saldos.vencerUnidades(eq(1), eq(10), anyInt(), any())).thenReturn(1);

        var u = servicio("2026-10-30T15:00:00Z").descontar(1, proceso);

        assertThat(u.idLote()).isEqualTo(2L);
        assertThat(vencido.getCantidadVencida()).isEqualTo(2);
        assertThat(vencido.getFechaVencida()).isNotNull();
        verify(saldos).vencerUnidades(eq(1), eq(10), eq(2), any());
    }

    @Test
    @DisplayName("Con el interruptor apagado no se vence nada al descontar (los lotes sin vencimiento siguen el FIFO de siempre)")
    void interruptorApagado() {
        when(parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)).thenReturn(false);
        var vencido = lote(1, 3, 0, LocalDateTime.of(2026, 10, 15, 23, 59, 59), 1);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(vencido));
        saldoConDescuentoAtomico(3);
        servicio("2026-10-30T15:00:00Z").descontar(1, proceso);
        verify(saldos, never()).vencerUnidades(anyInt(), anyInt(), anyInt(), any());
    }

    // ───────────── concurrencia ─────────────

    @Test
    @DisplayName("Dos solicitudes simultáneas con un lote de 1 unidad: solo una se crea, la otra recibe «sin unidades disponibles»")
    void dosSolicitudesUnaUnidad() throws Exception {
        var unico = lote(1, 1, 0, LocalDateTime.of(2027, 1, 31, 23, 59, 59), 1);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(unico));
        saldoConDescuentoAtomico(1);   // la sentencia atómica de la base deja pasar a una sola
        var s = servicio("2026-10-30T15:00:00Z");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                try {
                    s.descontar(1, proceso);
                    return "OK";
                } catch (ApiException e) {
                    return e.getMessage();
                }
            }));
        }
        salida.countDown();
        List<String> r = new ArrayList<>();
        for (Future<String> f : resultados) r.add(f.get());
        pool.shutdown();

        assertThat(r.stream().filter("OK"::equals)).hasSize(1);
        assertThat(r.stream().filter(m -> m.startsWith("Sin unidades disponibles"))).hasSize(1);
        assertThat(unico.getCantidadConsumida()).isEqualTo(1);
    }

    // ───────────── vencimiento de unidades, alerta y reporte ─────────────

    private VencimientoLotesService vencimientos(String instanteUtc) {
        var saldoService = servicio(instanteUtc);
        return new VencimientoLotesService(lotes, saldoService, parametros, notificaciones, Clock.fixed(Instant.parse(instanteUtc), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("El job vence las unidades no usadas del lote cuyo día ya pasó: salen del disponible y quedan reportadas")
    void jobVenceUnidades() {
        // 2 de febrero de 2027, 10:00 en Bogotá: el lote del 1 de febrero ya venció
        var l = lote(1, 20, 12, LocalDateTime.of(2027, 2, 1, 23, 59, 59), 1);   // 8 sin usar
        when(lotes.pendientesDeVencer(any())).thenReturn(List.of(l));
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(l));
        when(lotes.porAvisar(any(), any())).thenReturn(List.of());
        when(saldos.vencerUnidades(eq(1), eq(10), eq(8), any())).thenReturn(1);

        assertThat(vencimientos("2027-02-02T15:00:00Z").ejecutar()).isEqualTo(8);

        assertThat(l.getCantidadVencida()).isEqualTo(8);
        assertThat(l.restante()).isZero();
        assertThat(l.getCantidadConsumida()).isEqualTo(12);   // lo usado no cambia
        verify(saldos).vencerUnidades(eq(1), eq(10), eq(8), any());
    }

    @Test
    @DisplayName("Un lote vencido dos veces no vence dos veces las mismas unidades")
    void noVenceDosVeces() {
        var l = lote(1, 20, 12, LocalDateTime.of(2027, 2, 1, 23, 59, 59), 1);
        when(lotes.pendientesDeVencer(any())).thenReturn(List.of(l));
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(l));
        when(lotes.porAvisar(any(), any())).thenReturn(List.of());
        when(saldos.vencerUnidades(anyInt(), anyInt(), anyInt(), any())).thenReturn(1);
        var v = vencimientos("2027-02-02T15:00:00Z");
        v.ejecutar();
        assertThat(v.ejecutar()).isZero();
        verify(saldos, times(1)).vencerUnidades(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("Con el interruptor apagado el job no hace nada")
    void jobApagado() {
        when(parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)).thenReturn(false);
        assertThat(vencimientos("2027-02-02T15:00:00Z").ejecutar()).isZero();
        verify(lotes, never()).pendientesDeVencer(any());
    }

    @Test
    @DisplayName("Alerta: el lote que vence dentro de diasAlertaVencimiento aparece en el Dashboard con los días que quedan; la notificación sale una sola vez")
    void alertaPorVencer() {
        var l = lote(1, 20, 12, LocalDateTime.of(2027, 2, 10, 23, 59, 59), 1);
        when(lotes.porVencerDelCliente(eq(1), any(), any())).thenReturn(List.of(l));
        when(lotes.porAvisar(any(), any())).thenReturn(List.of(l));
        var v = vencimientos("2027-02-01T15:00:00Z");

        var alertas = v.lotesPorVencer(1);
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).unidades()).isEqualTo(8);
        assertThat(alertas.get(0).diasRestantes()).isEqualTo(9);
        assertThat(alertas.get(0).fechaVencimiento()).isEqualTo(LocalDate.of(2027, 2, 10));

        assertThat(v.avisar(LocalDateTime.of(2027, 2, 1, 10, 0))).isEqualTo(1);
        verify(notificaciones).crearParaCliente(eq(1), eq("LOTE_POR_VENCER"), anyString(), anyString());
        assertThat(l.getAvisoVencimientoEn()).isNotNull();
    }

    @Test
    @DisplayName("Con el interruptor apagado no hay alertas")
    void sinAlertasApagado() {
        when(parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)).thenReturn(false);
        assertThat(vencimientos("2027-02-01T15:00:00Z").lotesPorVencer(1)).isEmpty();
    }

    @Test
    @DisplayName("Reporte de ingreso por vencimiento: unidades vencidas × precio unitario del lote")
    void reporteIngreso() {
        var l = lote(1, 20, 12, LocalDateTime.of(2027, 2, 1, 23, 59, 59), 1);
        l.setCantidadVencida(8);
        l.setFechaVencida(LocalDateTime.of(2027, 2, 2, 10, 10));
        when(lotes.vencidosEntre(any(), any())).thenReturn(List.of(l));
        var filas = vencimientos("2027-02-05T15:00:00Z").reporte(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28));
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).unidadesVencidas()).isEqualTo(8);
        assertThat(filas.get(0).montoVencido()).isEqualByComparingTo("8008");   // 8 × 1001
    }

    @Test
    @DisplayName("El saldo disponible descuenta las unidades vencidas (comprado − consumido − vencido)")
    void disponibleSinVencidas() {
        var s = SaldoServicioCliente.builder().cliente(cliente).proceso(proceso).cantidadComprada(20).cantidadConsumida(12)
                .cantidadVencida(8).cantidadDisponible(0).build();
        assertThat(BolsaPrepagoService.fila(s).disponible()).isZero();
    }
}
