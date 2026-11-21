package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.RecargoPendiente;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.RecargoPendienteRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.enums.EstadoRecargoPendiente;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.enums.TipoCliente;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-09: cancelar en Pendiente devuelve la unidad a su lote (o a un lote de devolución si ya venció) y reversa el recargo; bolsa con vencimiento. */
class TiendaTp09Test {

    private final Proceso proceso = Proceso.builder().idProceso(10).nombreProceso("Poligrafía Pre Empleo").aplicaPrecioCiudad(true).build();
    private final Cliente cliente = Cliente.builder().idCliente(1).tipoCliente(TipoCliente.PREPAGO).build();
    private final Candidato candidato = Candidato.builder().idCandidato(7L).build();
    private SaldoServicioClienteRepository saldos;
    private LotePrepagoRepository lotes;
    private RecargoPendienteService recargos;
    private ParametrosTiendaService parametros;
    private DevolucionCobroService devolucion;

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        lotes = mock(LotePrepagoRepository.class);
        recargos = mock(RecargoPendienteService.class);
        parametros = mock(ParametrosTiendaService.class);
        when(parametros.entero("diasExtraDevolucionVencida")).thenReturn(30);
        when(parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)).thenReturn(true);
        when(saldos.devolverUna(anyInt(), anyInt(), any())).thenReturn(1);
        when(saldos.devolverUnaEnLoteNuevo(anyInt(), anyInt(), any())).thenReturn(1);
        var reloj = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
        var saldoService = new SaldoServicioClienteService(saldos, new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class)), lotes, reloj, parametros);
        devolucion = new DevolucionCobroService(saldos, lotes, mock(MovimientoCreditoPospagoRepository.class), mock(ClientePospagoRepository.class),
                saldoService, recargos, parametros);
    }

    private LotePrepago lote(LocalDateTime vence, String cobertura) {
        return LotePrepago.builder().idLote(5L).cliente(cliente).proceso(proceso).cobertura(cobertura).cantidadComprada(20).cantidadConsumida(6)
                .valorUnitario(new BigDecimal("180000")).valorOriginal(new BigDecimal("200000")).fechaCompra(LocalDateTime.now().minusMonths(3))
                .fechaVencimiento(vence).build();
    }

    private Servicio servicio(String cobertura) {
        return Servicio.builder().idServicio(9).cliente(cliente).candidato(candidato).proceso(proceso).idLotePrepago(5L).coberturaConsumo(cobertura).build();
    }

    @Test
    @DisplayName("Cancelar con el lote vigente: la unidad vuelve a SU mismo lote (vencimiento original) y se reversa el recargo")
    void vuelveASuLote() {
        var l = lote(LocalDateTime.now().plusMonths(2), "LOCAL");
        when(lotes.findById(5L)).thenReturn(Optional.of(l));
        var s = servicio("LOCAL");

        assertThat(devolucion.devolver(s)).isTrue();

        assertThat(l.getCantidadConsumida()).isEqualTo(5);
        verify(saldos).devolverUna(eq(1), eq(10), any());
        verify(lotes, never()).save(org.mockito.ArgumentMatchers.argThat((LotePrepago x) -> "DEVOLUCION".equals(x.getOrigen())));
        verify(recargos).reversarPorCancelacion(cliente, candidato, proceso);
    }

    @Test
    @DisplayName("Cancelar con el lote vencido ayer: aparece 1 unidad disponible, mismo precio, que vence en diasExtraDevolucionVencida (30) días")
    void loteVencidoAyer() {
        var l = lote(LocalDateTime.now().minusDays(1).withHour(23).withMinute(59).withSecond(59).withNano(0), "LOCAL");
        when(lotes.findById(5L)).thenReturn(Optional.of(l));

        assertThat(devolucion.devolver(servicio("LOCAL"))).isTrue();

        ArgumentCaptor<LotePrepago> c = ArgumentCaptor.forClass(LotePrepago.class);
        verify(lotes).save(c.capture());
        LotePrepago nuevo = c.getValue();
        assertThat(nuevo.getOrigen()).isEqualTo("DEVOLUCION");
        assertThat(nuevo.getCantidadComprada()).isEqualTo(1);
        assertThat(nuevo.getCantidadConsumida()).isZero();
        assertThat(nuevo.restante()).isEqualTo(1);
        assertThat(nuevo.getValorUnitario()).isEqualByComparingTo("180000");
        assertThat(nuevo.getFechaVencimiento().toLocalDate()).isEqualTo(LocalDate.now(ParametrosTiendaService.ZONA).plusDays(30));
        assertThat(nuevo.getFechaVencimiento().toLocalTime()).isEqualTo(java.time.LocalTime.of(23, 59, 59));
        assertThat(l.getCantidadConsumida()).isEqualTo(6);                 // el lote vencido no se toca: esa unidad sí se usó
        verify(saldos).devolverUnaEnLoteNuevo(eq(1), eq(10), any());       // y vuelve a estar disponible en la bolsa
        verify(saldos, never()).devolverUna(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("La devolución de un lote nacional vencido también es nacional y no toca el contador por sede")
    void devolucionNacionalVencida() {
        var l = lote(LocalDateTime.now().minusDays(1), "NACIONAL");
        when(lotes.findById(5L)).thenReturn(Optional.of(l));

        assertThat(devolucion.devolver(servicio("NACIONAL"))).isTrue();

        ArgumentCaptor<LotePrepago> c = ArgumentCaptor.forClass(LotePrepago.class);
        verify(lotes).save(c.capture());
        assertThat(c.getValue().getCobertura()).isEqualTo("NACIONAL");
        verify(saldos, never()).devolverUnaEnLoteNuevo(anyInt(), anyInt(), any());
        verify(saldos, never()).devolverUna(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("Cancelar dos veces no devuelve dos veces")
    void unaSolaVez() {
        var l = lote(LocalDateTime.now().plusMonths(2), "LOCAL");
        when(lotes.findById(5L)).thenReturn(Optional.of(l));
        var s = servicio("LOCAL");
        devolucion.devolver(s);
        assertThat(devolucion.devolver(s)).isFalse();
        assertThat(l.getCantidadConsumida()).isEqualTo(5);
    }

    // ───────────── reverso del recargo ─────────────

    private RecargoPendienteRepository repoRecargos;

    private RecargoPendienteService servicioRecargos() {
        repoRecargos = mock(RecargoPendienteRepository.class);
        return new RecargoPendienteService(repoRecargos, mock(PrecioCiudadProcesoRepository.class), mock(CiudadRepository.class), parametros);
    }

    private RecargoPendiente fila(String monto, EstadoRecargoPendiente estado) {
        return RecargoPendiente.builder().cliente(cliente).candidato(candidato).proceso(proceso)
                .ciudad(Ciudad.builder().idCiudad(3).nombreCiudad("Tunja").nivelCiudad(NivelCiudad.INTERMEDIA).build())
                .monto(new BigDecimal(monto)).estado(estado).fechaGenerado(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("Recargo ya pagado: se reversa como crédito pendiente (negativo) que baja la próxima compra")
    void reversaRecargoPagado() {
        var r = servicioRecargos();
        when(repoRecargos.findByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoAsc(7L, 10))
                .thenReturn(List.of(fila("15000", EstadoRecargoPendiente.PAGADO)));

        r.reversarPorCancelacion(cliente, candidato, proceso);

        ArgumentCaptor<RecargoPendiente> c = ArgumentCaptor.forClass(RecargoPendiente.class);
        verify(repoRecargos).save(c.capture());
        assertThat(c.getValue().getMonto()).isEqualByComparingTo("-15000");
        assertThat(c.getValue().getEstado()).isEqualTo(EstadoRecargoPendiente.PENDIENTE);
    }

    @Test
    @DisplayName("Recargo que seguía pendiente (nunca se cobró): se descarta, sin crédito")
    void descartaRecargoPendiente() {
        var r = servicioRecargos();
        var pendiente = fila("15000", EstadoRecargoPendiente.PENDIENTE);
        when(repoRecargos.findByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoAsc(7L, 10)).thenReturn(List.of(pendiente));

        r.reversarPorCancelacion(cliente, candidato, proceso);

        verify(repoRecargos).deleteAll(List.of(pendiente));
        verify(repoRecargos, never()).save(any());
    }

    @Test
    @DisplayName("Si el crédito ya está pendiente no se duplica")
    void noDuplicaElCredito() {
        var r = servicioRecargos();
        when(repoRecargos.findByCandidato_IdCandidatoAndProceso_IdProcesoOrderByFechaGeneradoAsc(7L, 10))
                .thenReturn(List.of(fila("15000", EstadoRecargoPendiente.PAGADO), fila("-15000", EstadoRecargoPendiente.PENDIENTE)));
        r.reversarPorCancelacion(cliente, candidato, proceso);
        verify(repoRecargos, never()).save(any());
    }

    // ───────────── bolsa con vencimiento ─────────────

    @Test
    @DisplayName("Mi bolsa: cada servicio trae el lote que vence primero («vencen {fecha}») cuando la vigencia está encendida")
    void bolsaConVencimiento() {
        var pronto = lote(LocalDateTime.of(2026, 12, 1, 23, 59, 59), "LOCAL");
        var tarde = LotePrepago.builder().idLote(6L).cliente(cliente).proceso(proceso).cobertura("LOCAL").cantidadComprada(5).cantidadConsumida(0)
                .valorUnitario(BigDecimal.ONE).fechaCompra(LocalDateTime.now()).fechaVencimiento(LocalDateTime.of(2027, 5, 1, 23, 59, 59)).build();
        when(lotes.findByCliente_IdCliente(1)).thenReturn(List.of(tarde, pronto));
        var saldo = com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente.builder().cliente(cliente).proceso(proceso)
                .cantidadComprada(25).cantidadConsumida(6).cantidadDisponible(19).build();
        when(saldos.findByCliente_IdCliente(1)).thenReturn(List.of(saldo));
        var reloj = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
        var s = new SaldoServicioClienteService(saldos, new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class)), lotes, reloj, parametros);

        var fila = s.obtenerSaldos(1).get(0);

        assertThat(fila.cantidadDisponible()).isEqualTo(19);
        assertThat(fila.proximoVencimiento()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(fila.unidadesQueVencen()).isEqualTo(14);
    }

    @Test
    @DisplayName("Con la vigencia apagada la bolsa no trae fecha de vencimiento")
    void bolsaSinVigencia() {
        when(parametros.activo(ParametrosTiendaService.VIGENCIA_POR_PLAN)).thenReturn(false);
        var saldo = com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente.builder().cliente(cliente).proceso(proceso)
                .cantidadComprada(25).cantidadConsumida(6).cantidadDisponible(19).build();
        when(saldos.findByCliente_IdCliente(1)).thenReturn(List.of(saldo));
        var reloj = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
        var s = new SaldoServicioClienteService(saldos, new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class)), lotes, reloj, parametros);
        assertThat(s.obtenerSaldos(1).get(0).proximoVencimiento()).isNull();
    }
}
