package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.AprobacionPospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.service.ConsumoMinimoService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.pagos.entity.FacturaPospago;
import com.polygraph.erp.modules.pagos.mora.MoraCliente;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.enums.EstadoFacturaPospago;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.service.DiasHabilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-14: mora automática (días de gracia, cambio inmediato al pagar, avisos, suspensión manual) y consumo mínimo del pospago. */
class TiendaTp14Test {

    private static final LocalDate HOY = LocalDate.of(2026, 12, 1);

    private FacturaPospagoRepository facturas;
    private ClientePospagoRepository pospagos;
    private ClienteRepository clientes;
    private ParametrosTiendaService parametros;
    private NotificacionService notificaciones;
    private MoraService mora;
    private ClientePospago cupo;
    private FacturaPospago factura;

    @BeforeEach
    void setUp() {
        facturas = mock(FacturaPospagoRepository.class);
        pospagos = mock(ClientePospagoRepository.class);
        clientes = mock(ClienteRepository.class);
        parametros = mock(ParametrosTiendaService.class);
        notificaciones = mock(NotificacionService.class);
        var dias = mock(DiasHabilesService.class);
        when(dias.hoy()).thenReturn(HOY);
        when(parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)).thenReturn(true);
        when(parametros.entero("diasGraciaMora")).thenReturn(0);
        mora = new MoraService(facturas, pospagos, dias, parametros, notificaciones, clientes);

        cupo = ClientePospago.builder().idCliente(1).limiteCredito(new BigDecimal("9000000")).creditoDisponible(new BigDecimal("8000000")).estadoMora(EstadoMora.NORMAL).build();
        when(pospagos.findByIdCliente(1)).thenReturn(Optional.of(cupo));
        when(pospagos.findAll()).thenReturn(List.of(cupo));
        when(clientes.findById(1)).thenReturn(Optional.of(Cliente.builder().idCliente(1).tipoCliente(TipoCliente.POSPAGO).razonSocial("Acme").idGestor(5L).build()));
        factura = FacturaPospago.builder().referencia("F-1").fechaVencimiento(HOY.minusDays(1)).montoTotal(new BigDecimal("1000000")).estado(EstadoFacturaPospago.VENCIDA).build();
        when(facturas.findByCliente_IdClienteOrderByFechaGeneracionDesc(1)).thenReturn(List.of(factura));
    }

    // ───────────── días de gracia ─────────────

    private static MoraCliente.Factura f(int diasVencida) {
        return new MoraCliente.Factura("F", HOY.minusDays(diasVencida), BigDecimal.TEN, false);
    }

    @Test
    @DisplayName("En mora = vencida hace MÁS de diasGraciaMora días; con 0 de gracia, vencida ayer ya es mora y la que vence hoy no")
    void graciaEnLaRegla() {
        assertThat(MoraCliente.calcular(List.of(f(1)), HOY, 0).enMora()).isTrue();
        assertThat(MoraCliente.calcular(List.of(f(0)), HOY, 0).enMora()).isFalse();
        assertThat(MoraCliente.calcular(List.of(f(5)), HOY, 5).enMora()).isFalse();   // exactamente 5 días: todavía en gracia
        assertThat(MoraCliente.calcular(List.of(f(6)), HOY, 5).enMora()).isTrue();
        assertThat(MoraCliente.calcular(List.of(f(1)), HOY).enMora()).isTrue();      // la regla de siempre no cambia
    }

    @Test
    @DisplayName("Con la mora automática apagada los días de gracia no se usan")
    void interruptorApagadoSinGracia() {
        when(parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)).thenReturn(false);
        when(parametros.entero("diasGraciaMora")).thenReturn(10);
        assertThat(mora.calcular(1).enMora()).isTrue();   // vencida ayer: mora como hoy
    }

    // ───────────── proceso diario ─────────────

    @Test
    @DisplayName("Factura con vencimiento ayer y sin pago: el proceso de hoy pasa al cliente a «En mora» y avisa al cliente y al gestor; Nueva solicitud ve la mora")
    void procesoDiarioPasaAMora() {
        assertThat(mora.sincronizarTodos()).isEqualTo(1);

        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.EN_MORA);
        assertThat(cupo.getDiasMora()).isEqualTo(1);
        verify(notificaciones).crearParaCliente(eq(1), eq("MORA"), eq("Tu cuenta pasó a mora"), anyString());
        verify(notificaciones).crearParaUsuario(eq(5L), eq("MORA"), eq("Cliente en mora"), anyString());
        assertThat(mora.calcular(1).enMora()).isTrue();   // es el mismo cálculo con el que Nueva solicitud rechaza el envío
    }

    @Test
    @DisplayName("Con días de gracia el cliente entra en mora cuando se acaba la gracia, aunque ninguna factura haya cambiado")
    void graciaYProcesoDiario() {
        when(parametros.entero("diasGraciaMora")).thenReturn(3);
        mora.sincronizarTodos();
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.NORMAL);   // vencida hace 1 día, 3 de gracia
        verify(notificaciones, never()).crearParaCliente(any(), any(), any(), any());

        factura.setFechaVencimiento(HOY.minusDays(4));                    // pasan los días
        mora.sincronizarTodos();
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.EN_MORA);
    }

    @Test
    @DisplayName("Se registra el pago de la última factura vencida: el cliente queda «Al día» en el momento y se le avisa")
    void pagoLevantaEnElMomento() {
        mora.sincronizarTodos();
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.EN_MORA);

        factura.setEstado(EstadoFacturaPospago.PAGADA);   // lo que hace marcarFacturaPagada, ahora ANTES de recalcular
        mora.sincronizar(1);                              // lo que llama levantarRestriccionPorDeuda al instante, sin esperar al proceso diario

        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.NORMAL);
        assertThat(cupo.getDiasMora()).isZero();
        verify(notificaciones).crearParaCliente(eq(1), eq("MORA"), eq("Tu cuenta está al día"), anyString());
        verify(notificaciones).crearParaUsuario(eq(5L), eq("MORA"), eq("Cliente al día"), anyString());
    }

    @Test
    @DisplayName("Pagar una de dos facturas vencidas no lo saca de la mora")
    void unaDeDosNoLevanta() {
        var otra = FacturaPospago.builder().referencia("F-2").fechaVencimiento(HOY.minusDays(10)).montoTotal(BigDecimal.TEN).estado(EstadoFacturaPospago.VENCIDA).build();
        when(facturas.findByCliente_IdClienteOrderByFechaGeneracionDesc(1)).thenReturn(List.of(factura, otra));
        mora.sincronizar(1);
        factura.setEstado(EstadoFacturaPospago.PAGADA);
        mora.sincronizar(1);
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.EN_MORA);
    }

    @Test
    @DisplayName("La plataforma nunca suspende sola ni levanta una suspensión: un cliente suspendido no se toca")
    void nuncaSuspendeNiLevantaSola() {
        cupo.setEstadoMora(EstadoMora.SUSPENDIDO);
        mora.sincronizarTodos();
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.SUSPENDIDO);

        factura.setEstado(EstadoFacturaPospago.PAGADA);
        mora.sincronizar(1);
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.SUSPENDIDO);   // pagar tampoco levanta la suspensión
        verify(notificaciones, never()).crearParaCliente(any(), any(), any(), any());

        // y un cliente al día jamás pasa a SUSPENDIDO por el proceso
        cupo.setEstadoMora(EstadoMora.NORMAL);
        factura.setEstado(EstadoFacturaPospago.VENCIDA);
        factura.setFechaVencimiento(HOY.minusDays(400));
        mora.sincronizarTodos();
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.EN_MORA);
    }

    @Test
    @DisplayName("Con la mora automática apagada no se avisa a nadie por los cambios de mora")
    void apagadoSinAvisos() {
        when(parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)).thenReturn(false);
        mora.sincronizar(1);
        assertThat(cupo.getEstadoMora()).isEqualTo(EstadoMora.EN_MORA);
        verify(notificaciones, never()).crearParaCliente(any(), any(), any(), any());
    }

    // ───────────── consumo mínimo ─────────────

    private ConsumoMinimoService consumo(Long... porMes) {
        var servicios = mock(ServicioRepository.class);
        // 3 meses completos antes del 1 de diciembre: septiembre, octubre y noviembre (del más antiguo al más reciente)
        when(servicios.contarNoCanceladosEntre(eq(1), any(), any())).thenReturn(porMes[0], porMes[1], porMes[2]);
        var aprobaciones = mock(AprobacionPospagoRepository.class);
        when(aprobaciones.findFirstByIdClienteOrderByAprobadoEnDesc(1)).thenReturn(Optional.empty());
        when(parametros.entero("pospagoProcesosMinimosMes")).thenReturn(20);
        when(parametros.entero("pospagoMesesConsumoBajo")).thenReturn(3);
        when(clientes.findAll()).thenReturn(List.of(Cliente.builder().idCliente(1).tipoCliente(TipoCliente.POSPAGO).razonSocial("Acme").idGestor(5L).build()));
        return new ConsumoMinimoService(clientes, servicios, aprobaciones, parametros, notificaciones, Clock.fixed(Instant.parse("2026-12-01T13:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("12, 15 y 18 solicitudes en tres meses seguidos: alerta al gestor y al administrador; la plataforma no cambia nada")
    void consumoBajoAlerta() {
        var alertas = consumo(12L, 15L, 18L).ejecutar();

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).solicitudesPorMes()).containsExactly(12L, 15L, 18L);
        verify(notificaciones).crearParaUsuario(eq(5L), eq("CONSUMO_MINIMO"), any(), anyString());
        verify(notificaciones).crearParaAdmins(eq("CONSUMO_MINIMO"), any(), anyString());
        verify(clientes, never()).save(any());          // no pasa a prepago ni cambia precio solo
        verify(pospagos, never()).save(any());
    }

    @Test
    @DisplayName("12, 25 y 18: un mes llegó al mínimo, no hay alerta")
    void unMesBuenoNoAlerta() {
        assertThat(consumo(12L, 25L, 18L).ejecutar()).isEmpty();
        verify(notificaciones, never()).crearParaUsuario(any(), any(), any(), any());
        verify(notificaciones, never()).crearParaAdmins(any(), any(), any());
    }

    @Test
    @DisplayName("«Menos de» es estricto: exactamente 20 en un mes no es consumo bajo")
    void exactamenteElMinimo() {
        assertThat(ConsumoMinimoService.consumoBajo(List.of(12L, 20L, 18L), 20, 3)).isFalse();
        assertThat(ConsumoMinimoService.consumoBajo(List.of(0L, 0L, 19L), 20, 3)).isTrue();
        assertThat(ConsumoMinimoService.consumoBajo(List.of(1L, 2L), 20, 3)).isFalse();   // faltan meses
    }

    @Test
    @DisplayName("Con el interruptor apagado el proceso mensual no hace nada")
    void consumoApagado() {
        var s = consumo(1L, 1L, 1L);
        when(parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)).thenReturn(false);
        assertThat(s.ejecutar()).isEmpty();
        verify(notificaciones, never()).crearParaAdmins(any(), any(), any());
    }
}
