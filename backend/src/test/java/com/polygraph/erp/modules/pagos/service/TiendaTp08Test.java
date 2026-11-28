package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.entity.LotePrepago;
import com.polygraph.erp.modules.pagos.entity.OrdenCompraItem;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.RecargoPendienteRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.solicitudes.service.ResumenCobroService;
import com.polygraph.erp.modules.tienda.service.CalculadoraCobertura;
import com.polygraph.erp.modules.tienda.service.IvaService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-08: cobertura por sede o nacional: recargo por ciudad, precio nacional, bolsas separadas, devolución y aviso del paso 2. */
class TiendaTp08Test {

    private static final Map<NivelCiudad, BigDecimal> PRE_EMPLEO = new EnumMap<>(Map.of(
            NivelCiudad.INTERMEDIA, new BigDecimal("15000"), NivelCiudad.MUNICIPIO_SECUNDARIO, new BigDecimal("25000")));

    // ───────────── reglas puras ─────────────

    @Test
    @DisplayName("Pre Empleo: sede (Bogotá) sin recargo; Tunja (intermedia) $15.000; Cali (principal que no es sede) recargo de intermedia; municipio $25.000")
    void recargoPorCiudad() {
        assertThat(CalculadoraCobertura.recargo(true, true, NivelCiudad.PRINCIPAL, "LOCAL", PRE_EMPLEO)).isEqualByComparingTo("0");
        assertThat(CalculadoraCobertura.recargo(true, false, NivelCiudad.INTERMEDIA, "LOCAL", PRE_EMPLEO)).isEqualByComparingTo("15000");   // Tunja
        assertThat(CalculadoraCobertura.recargo(true, false, NivelCiudad.PRINCIPAL, "LOCAL", PRE_EMPLEO)).isEqualByComparingTo("15000");    // Cali
        assertThat(CalculadoraCobertura.recargo(true, false, NivelCiudad.MUNICIPIO_SECUNDARIO, "LOCAL", PRE_EMPLEO)).isEqualByComparingTo("25000");
        assertThat(CalculadoraCobertura.recargo(true, false, null, "LOCAL", PRE_EMPLEO)).isEqualByComparingTo("25000");   // sin clasificar = nunca el más barato
    }

    @Test
    @DisplayName("Una unidad nacional nunca genera recargo, ni siquiera en un municipio; si aplicaPrecioCiudad es false, tampoco hay recargo aunque estén guardados")
    void nacionalYSinPrecioCiudad() {
        assertThat(CalculadoraCobertura.recargo(true, false, NivelCiudad.MUNICIPIO_SECUNDARIO, "NACIONAL", PRE_EMPLEO)).isEqualByComparingTo("0");
        assertThat(CalculadoraCobertura.recargo(false, false, NivelCiudad.INTERMEDIA, "LOCAL", PRE_EMPLEO)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Plan Medio nacional: $180.000 + 80 % × $15.000 = $192.000 por unidad; se redondea a $1.000")
    void precioNacional() {
        assertThat(CalculadoraCobertura.precioNacional(new BigDecimal("180000"), new BigDecimal("15000"), new BigDecimal("80"))).isEqualByComparingTo("192000");
        assertThat(CalculadoraCobertura.precioNacional(new BigDecimal("180000"), new BigDecimal("14000"), new BigDecimal("80"))).isEqualByComparingTo("191000");   // 191.200
        assertThat(CalculadoraCobertura.precioNacional(new BigDecimal("180500"), new BigDecimal("15000"), new BigDecimal("80"))).isEqualByComparingTo("193000");   // 192.500 → sube a 193.000
    }

    @Test
    @DisplayName("Antecedentes (sin recargo de ciudad) no ofrece paquete nacional; Pre Empleo sí")
    void soloConRecargoOfreceNacional() {
        assertThat(CalculadoraCobertura.admiteNacional(false, BigDecimal.ZERO)).isFalse();
        assertThat(CalculadoraCobertura.admiteNacional(true, BigDecimal.ZERO)).isFalse();
        assertThat(CalculadoraCobertura.admiteNacional(true, new BigDecimal("15000"))).isTrue();
    }

    @Test
    void bolsaPreferida() {
        assertThat(CalculadoraCobertura.bolsaPreferida(true)).isEqualTo("NACIONAL");
        assertThat(CalculadoraCobertura.bolsaPreferida(false)).isEqualTo("LOCAL");
    }

    // ───────────── recargo con el interruptor ─────────────

    private final Proceso preEmpleo = Proceso.builder().idProceso(10).nombreProceso("Poligrafía Pre Empleo").valor(new BigDecimal("200000"))
            .aplicaPrecioCiudad(true).build();
    private final Cliente cliente = Cliente.builder().idCliente(1).tipoCliente(TipoCliente.PREPAGO).build();
    private SaldoServicioClienteRepository saldos;
    private LotePrepagoRepository lotes;
    private ParametrosTiendaService parametros;
    private PrecioCiudadProcesoRepository precios;

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        lotes = mock(LotePrepagoRepository.class);
        parametros = mock(ParametrosTiendaService.class);
        precios = mock(PrecioCiudadProcesoRepository.class);
        when(parametros.activo(ParametrosTiendaService.COBERTURA_POR_SEDE)).thenReturn(true);
        when(parametros.decimal("factorPrecioNacional")).thenReturn(new BigDecimal("80"));
        when(precios.findByProceso_IdProcesoOrderByNivelCiudadAsc(10)).thenReturn(List.of(
                PrecioCiudadProceso.builder().proceso(preEmpleo).nivelCiudad(NivelCiudad.INTERMEDIA).valor(new BigDecimal("15000")).build(),
                PrecioCiudadProceso.builder().proceso(preEmpleo).nivelCiudad(NivelCiudad.MUNICIPIO_SECUNDARIO).valor(new BigDecimal("25000")).build()));
    }

    private RecargoPendienteService recargos() {
        return new RecargoPendienteService(mock(RecargoPendienteRepository.class), precios, mock(CiudadRepository.class), parametros);
    }

    private static Ciudad ciudad(String nombre, NivelCiudad nivel, boolean sede) {
        return Ciudad.builder().idCiudad(1).nombreCiudad(nombre).nivelCiudad(nivel).esSede(sede).build();
    }

    @Test
    @DisplayName("RecargoPendienteService con el interruptor encendido: Tunja $15.000, Bogotá 0, Cali intermedia; nacional 0")
    void recargoServicioEncendido() {
        var r = recargos();
        assertThat(r.calcular(preEmpleo, ciudad("Tunja", NivelCiudad.INTERMEDIA, false))).isEqualByComparingTo("15000");
        assertThat(r.calcular(preEmpleo, ciudad("Bogotá", NivelCiudad.PRINCIPAL, true))).isEqualByComparingTo("0");
        assertThat(r.calcular(preEmpleo, ciudad("Cali", NivelCiudad.PRINCIPAL, false))).isEqualByComparingTo("15000");
        assertThat(r.calcular(preEmpleo, ciudad("Un municipio", NivelCiudad.MUNICIPIO_SECUNDARIO, false), "NACIONAL")).isEqualByComparingTo("0");
        assertThat(r.recargoIntermedio(preEmpleo)).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("Con el interruptor apagado el recargo es el de siempre: la fila del nivel de la ciudad (Bogotá PRINCIPAL sin fila = 0)")
    void recargoServicioApagado() {
        when(parametros.activo(ParametrosTiendaService.COBERTURA_POR_SEDE)).thenReturn(false);
        when(precios.findByProceso_IdProcesoAndNivelCiudad(10, NivelCiudad.INTERMEDIA)).thenReturn(Optional.of(
                PrecioCiudadProceso.builder().nivelCiudad(NivelCiudad.INTERMEDIA).valor(new BigDecimal("15000")).build()));
        var r = recargos();
        assertThat(r.calcular(preEmpleo, ciudad("Tunja", NivelCiudad.INTERMEDIA, false))).isEqualByComparingTo("15000");
        assertThat(r.calcular(preEmpleo, ciudad("Cali", NivelCiudad.PRINCIPAL, false))).isEqualByComparingTo("0");   // sin la regla nueva
    }

    // ───────────── bolsas separadas ─────────────

    private SaldoServicioClienteService saldoService() {
        var reloj = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
        return new SaldoServicioClienteService(saldos, new BolsaPrepagoService(saldos, mock(OrdenCompraItemRepository.class)), lotes, reloj, parametros);
    }

    private LotePrepago lote(long id, String cobertura, int comprada, int consumida, int dia) {
        return LotePrepago.builder().idLote(id).cliente(cliente).proceso(preEmpleo).cobertura(cobertura).cantidadComprada(comprada)
                .cantidadConsumida(consumida).valorUnitario(new BigDecimal("180000")).fechaCompra(LocalDateTime.of(2026, 10, dia, 9, 0)).build();
    }

    @Test
    @DisplayName("Ciudad con recargo: usa primero unidades nacionales; ciudad sede: primero las de sede")
    void bolsaIdeal() {
        var local = lote(1, "LOCAL", 5, 0, 1);
        var nacional = lote(2, "NACIONAL", 5, 0, 2);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(local, nacional));
        when(saldos.descontarUna(eq(1), eq(10), any())).thenReturn(1);
        var s = saldoService();

        var enTunja = s.descontar(1, preEmpleo, "NACIONAL");
        assertThat(enTunja.cobertura()).isEqualTo("NACIONAL");
        assertThat(enTunja.idLote()).isEqualTo(2L);
        verify(saldos, never()).descontarUna(anyInt(), anyInt(), any());   // la bolsa nacional no tiene contador

        var enBogota = s.descontar(1, preEmpleo, "LOCAL");
        assertThat(enBogota.cobertura()).isEqualTo("LOCAL");
        assertThat(enBogota.idLote()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sin unidades del tipo ideal usa el otro: ciudad con recargo y solo unidades de sede → usa la de sede (y paga recargo); sede y solo nacionales → nacional")
    void usaLaOtraBolsa() {
        var nacional = lote(2, "NACIONAL", 5, 0, 2);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(nacional));
        when(saldos.descontarUna(eq(1), eq(10), any())).thenReturn(0);   // sin unidades por sede
        assertThat(saldoService().descontar(1, preEmpleo, "LOCAL").cobertura()).isEqualTo("NACIONAL");

        var local = lote(1, "LOCAL", 5, 0, 1);
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(local));
        when(saldos.descontarUna(eq(1), eq(10), any())).thenReturn(1);
        assertThat(saldoService().descontar(1, preEmpleo, "NACIONAL").cobertura()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("Sin unidades en ninguna bolsa: «Sin unidades disponibles»")
    void sinUnidades() {
        when(lotes.bloquearPorClienteYProceso(1, 10)).thenReturn(List.of(lote(2, "NACIONAL", 5, 5, 2)));
        when(saldos.descontarUna(eq(1), eq(10), any())).thenReturn(0);
        assertThatThrownBy(() -> saldoService().descontar(1, preEmpleo, "NACIONAL")).isInstanceOf(ApiException.class)
                .hasMessageStartingWith("Sin unidades disponibles");
    }

    @Test
    @DisplayName("El paquete nacional se acredita solo en su lote (cobertura NACIONAL), sin tocar el contador por sede")
    void acreditarNacional() {
        var item = OrdenCompraItem.builder().idItem(5L).cantidad(20).valorUnitario(new BigDecimal("192000")).valorOriginal(new BigDecimal("192000"))
                .cobertura("NACIONAL").build();
        when(lotes.existsByItem_IdItem(5L)).thenReturn(false);
        saldoService().acreditar(cliente, preEmpleo, 20, item, "T-1");
        verify(saldos, never()).save(any());
        verify(lotes).save(org.mockito.ArgumentMatchers.argThat((LotePrepago l) -> "NACIONAL".equals(l.getCobertura()) && l.getCantidadComprada() == 20));
    }

    @Test
    @DisplayName("Las unidades nacionales cuentan en lo disponible del cliente, con su propio número")
    void disponibleIncluyeNacionales() {
        var nacional = lote(2, "NACIONAL", 20, 4, 2);   // 16
        when(lotes.findByCliente_IdClienteAndCobertura(1, "NACIONAL")).thenReturn(List.of(nacional));
        when(lotes.findByCliente_IdClienteAndProceso_IdProcesoOrderByFechaCompraAscIdLoteAsc(1, 10)).thenReturn(List.of(nacional));
        when(saldos.findByCliente_IdCliente(1)).thenReturn(List.of());
        when(saldos.findByCliente_IdClienteAndProceso_IdProceso(1, 10)).thenReturn(Optional.empty());
        var s = saldoService();
        var filas = s.obtenerSaldos(1);
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).cantidadDisponible()).isEqualTo(16);
        assertThat(filas.get(0).disponibleNacional()).isEqualTo(16);
        assertThat(s.totalDisponible(1)).isEqualTo(16);
        s.validarDisponibilidad(1, preEmpleo);   // no lanza: hay nacionales
    }

    // ───────────── cancelar devuelve a la bolsa de donde salió ─────────────

    @Test
    @DisplayName("Cancelar una solicitud que usó una unidad nacional la devuelve al lote nacional, sin tocar el contador por sede")
    void devolverNacional() {
        var nacional = lote(2, "NACIONAL", 5, 3, 2);
        when(lotes.findById(2L)).thenReturn(Optional.of(nacional));
        var devolucion = new DevolucionCobroService(saldos, lotes, mock(MovimientoCreditoPospagoRepository.class), mock(ClientePospagoRepository.class),
                saldoService(), mock(RecargoPendienteService.class), parametros);
        var servicio = Servicio.builder().idServicio(9).cliente(cliente).proceso(preEmpleo).idLotePrepago(2L).coberturaConsumo("NACIONAL").build();

        assertThat(devolucion.devolver(servicio)).isTrue();

        assertThat(nacional.getCantidadConsumida()).isEqualTo(2);
        verify(saldos, never()).devolverUna(anyInt(), anyInt(), any());
        assertThat(servicio.getCobroDevueltoEn()).isNotNull();
    }

    // ───────────── aviso del paso 2 ─────────────

    private ResumenCobroService resumen(Ciudad ciudad, int disponibleLocal, int disponibleNacional) {
        var usuarios = mock(UsuarioRepository.class);
        var usuario = mock(Usuario.class);
        when(usuario.getIdCliente()).thenReturn(1);
        when(usuarios.findByEmail("c@x.co")).thenReturn(Optional.of(usuario));
        var clientes = mock(ClienteRepository.class);
        when(clientes.findById(1)).thenReturn(Optional.of(cliente));
        var procesos = mock(ProcesoRepository.class);
        when(procesos.findById(10)).thenReturn(Optional.of(preEmpleo));
        var ciudades = mock(CiudadRepository.class);
        when(ciudades.findById(1)).thenReturn(Optional.of(ciudad));
        var bolsa = mock(BolsaPrepagoService.class);
        when(bolsa.disponible(1, 10)).thenReturn(disponibleLocal);
        var saldo = mock(SaldoServicioClienteService.class);
        when(saldo.disponibleNacional(1, 10)).thenReturn(disponibleNacional);
        var iva = mock(IvaService.class);
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        return new ResumenCobroService(usuarios, clientes, procesos, ciudades, bolsa, mock(ClientePospagoRepository.class), recargos(), iva, saldo, parametros,
                mock(com.polygraph.erp.modules.tienda.service.PrecioPospagoService.class));
    }

    @Test
    @DisplayName("Paso 2: con unidades por sede en Tunja, el aviso dice el recargo con IVA y la ciudad")
    void avisoEnTunja() {
        var r = resumen(ciudad("Tunja", NivelCiudad.INTERMEDIA, false), 20, 0).avisoRecargo("c@x.co", 10, 1);
        assertThat(r.aplica()).isTrue();
        assertThat(r.recargo()).isEqualByComparingTo("15000");
        assertThat(r.iva()).isEqualByComparingTo("2850");
        assertThat(r.cobertura()).isEqualTo("LOCAL");
        assertThat(r.mensaje()).isEqualTo("Esta prueba tiene un recargo de $15.000 + IVA por realizarse en Tunja.");
    }

    @Test
    @DisplayName("Paso 2: en Bogotá no hay aviso; en Tunja con unidades nacionales tampoco hay recargo")
    void avisoSinRecargo() {
        assertThat(resumen(ciudad("Bogotá", NivelCiudad.PRINCIPAL, true), 20, 0).avisoRecargo("c@x.co", 10, 1).aplica()).isFalse();
        var r = resumen(ciudad("Tunja", NivelCiudad.INTERMEDIA, false), 20, 5).avisoRecargo("c@x.co", 10, 1);
        assertThat(r.aplica()).isFalse();
        assertThat(r.cobertura()).isEqualTo("NACIONAL");
        assertThat(r.mensaje()).contains("paquete nacional");
    }

    @Test
    @DisplayName("Con el interruptor apagado no hay aviso")
    void avisoApagado() {
        when(parametros.activo(ParametrosTiendaService.COBERTURA_POR_SEDE)).thenReturn(false);
        assertThat(resumen(ciudad("Tunja", NivelCiudad.INTERMEDIA, false), 20, 0).avisoRecargo("c@x.co", 10, 1).aplica()).isFalse();
    }
}
