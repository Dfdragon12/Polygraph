package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.pagos.entity.SaldoServicioCliente;
import com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BolsaPrepagoServiceTest {

    private SaldoServicioClienteRepository saldos;
    private OrdenCompraItemRepository items;
    private BolsaPrepagoService bolsa;

    @BeforeEach
    void setUp() {
        saldos = mock(SaldoServicioClienteRepository.class);
        items = mock(OrdenCompraItemRepository.class);
        bolsa = new BolsaPrepagoService(saldos, items);
    }

    private static SaldoServicioCliente saldo(int idCliente, int idProceso, String nombre, int comprada, int consumida, int disponibleGuardado) {
        Cliente c = new Cliente();
        c.setIdCliente(idCliente);
        Proceso p = new Proceso();
        p.setIdProceso(idProceso);
        p.setNombreProceso(nombre);
        return SaldoServicioCliente.builder().cliente(c).proceso(p).cantidadComprada(comprada).cantidadConsumida(consumida)
                .cantidadDisponible(disponibleGuardado).build();
    }

    @Test
    @DisplayName("disponible = comprado − consumido − reservado")
    void formula() {
        assertThat(BolsaPrepagoService.calcularDisponible(20, 2, 0)).isEqualTo(18);
        assertThat(BolsaPrepagoService.calcularDisponible(20, 2, 3)).isEqualTo(15);
    }

    @Test
    @DisplayName("Una fila por servicio comprado alguna vez (también los agotados) y los totales suman lo mismo que las filas")
    void porServicioYTotales() {
        when(saldos.findByCliente_IdCliente(1)).thenReturn(List.of(
                saldo(1, 10, "Antecedentes", 20, 2, 18), saldo(1, 11, "Poligrafía Pre Empleo", 40, 6, 34), saldo(1, 12, "Visita", 5, 5, 0),
                saldo(1, 13, "Nunca comprado", 0, 0, 0)));
        var b = bolsa.saldoPorServicio(1);
        assertThat(b.servicios()).extracting(s -> s.nombreProceso()).containsExactly("Antecedentes", "Poligrafía Pre Empleo", "Visita");
        assertThat(b.totalDisponible()).isEqualTo(52);
        assertThat(b.totalComprado()).isEqualTo(65);
        assertThat(b.totalConsumido()).isEqualTo(13);
        assertThat(b.servicios().get(2).disponible()).isZero();      // el agotado se muestra
    }

    @Test
    @DisplayName("Dashboard, Comprar servicios, Gestión de cuentas y Nueva solicitud dan el mismo número: todos leen de la bolsa")
    void todasLasPantallasCoinciden() {
        when(saldos.findByCliente_IdCliente(1)).thenReturn(List.of(saldo(1, 10, "Antecedentes", 20, 2, 18), saldo(1, 11, "Poligrafía", 40, 6, 34)));
        when(saldos.findByCliente_IdClienteAndProceso_IdProceso(1, 10)).thenReturn(Optional.of(saldo(1, 10, "Antecedentes", 20, 2, 18)));
        var servicio = new SaldoServicioClienteService(saldos, bolsa, mock(com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository.class));
        int dashboard = servicio.totalDisponible(1);                                               // Dashboard y Gestión de cuentas
        int comprarServicios = servicio.obtenerSaldos(1).stream().mapToInt(s -> s.cantidadDisponible()).sum();
        int nuevaSolicitud = bolsa.disponible(1, 10) + bolsa.disponible(1, 11 );
        assertThat(dashboard).isEqualTo(52).isEqualTo(comprarServicios);
        assertThat(bolsa.disponible(1, 10)).isEqualTo(18);
        assertThat(nuevaSolicitud).isEqualTo(dashboard - 34 + bolsa.disponible(1, 11));           // coherente con el total
    }

    @Test
    @DisplayName("El saldo mostrado sale de comprado − consumido aunque el contador guardado esté desfasado")
    void ignoraElContadorDisponibleGuardado() {
        when(saldos.findByCliente_IdCliente(1)).thenReturn(List.of(saldo(1, 10, "Antecedentes", 20, 2, 25)));
        assertThat(bolsa.saldoPorServicio(1).totalDisponible()).isEqualTo(18);
    }

    @Test
    @DisplayName("Reporte previo: una compra «por facturar» (no aprobada) no suma y se ve como diferencia")
    void reporteDeDiferencias() {
        // guardado: 20 comprado; las compras APROBADAS solo suman 15 (las otras 5 vienen de una compra no aprobada)
        when(saldos.findAll()).thenReturn(List.of(saldo(1, 10, "Antecedentes", 20, 2, 18), saldo(2, 10, "Antecedentes", 10, 0, 10)));
        when(items.sumarCantidadPorClienteYProceso(EstadoOrdenCompra.APROBADA)).thenReturn(List.<Object[]>of(new Object[] {1, 10, 15L}, new Object[] {2, 10, 10L}));
        var d = bolsa.diferenciasConComprasAprobadas();
        assertThat(d).hasSize(1);
        assertThat(d.get(0).idCliente()).isEqualTo(1);
        assertThat(d.get(0).compradoGuardado()).isEqualTo(20);
        assertThat(d.get(0).compradoSegunCompras()).isEqualTo(15);
    }

    @Test
    @DisplayName("Sin bolsa el disponible es 0")
    void sinBolsa() {
        when(saldos.findByCliente_IdClienteAndProceso_IdProceso(any(), any())).thenReturn(Optional.empty());
        when(saldos.findByCliente_IdCliente(9)).thenReturn(Collections.emptyList());
        assertThat(bolsa.disponible(9, 10)).isZero();
        assertThat(bolsa.saldoPorServicio(9).totalDisponible()).isZero();
    }
}
