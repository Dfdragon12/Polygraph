package com.polygraph.erp.modules.pagos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago;
import com.polygraph.erp.modules.pagos.mora.MoraCliente;
import com.polygraph.erp.modules.pagos.repository.FacturaPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.LotePrepagoRepository;
import com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository;
import com.polygraph.erp.modules.pagos.repository.PagoWompiRepository;
import com.polygraph.erp.modules.pagos.repository.SaldoServicioClienteRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.solicitudes.service.CargaMasivaValidador;
import com.polygraph.erp.modules.solicitudes.service.CargaMasivaValidador.FilaCarga;
import com.polygraph.erp.modules.solicitudes.service.SolicitudExtraService;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.service.IvaService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.modules.tienda.service.PlanesTiendaService;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService;
import com.polygraph.erp.shared.enums.ModoPrecioPospago;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import com.polygraph.erp.shared.service.DiasHabilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LP-05: el cliente pospago usa la lista nueva (precio efectivo), el servidor vuelve a validar «ofrecido» y precio, y el cargo guarda el precio con el
 * que se creó: un cambio de la lista solo aplica a las solicitudes nuevas, y cancelar devuelve al cupo exactamente lo que se descontó.
 * Usa el PrecioPospagoService, el ClientePospagoService y el DevolucionCobroService reales (solo los repositorios están simulados).
 */
@DisplayName("LP-05 — cliente pospago con la lista editable")
class LP05ClientePospagoListaTest {

    private PlanesTiendaService planes;
    private ParametrosTiendaService parametros;
    private ClientePospagoRepository pospagoRepo;
    private MovimientoCreditoPospagoRepository movimientos;
    private PrecioPospagoService precios;
    private ClientePospagoService servicio;
    private DevolucionCobroService devolucion;
    private ClientePospago cupo;
    private final List<MovimientoCreditoPospago> cargos = new java.util.ArrayList<>();
    private final Cliente cliente = Cliente.builder().idCliente(9).tipoCliente(TipoCliente.POSPAGO).build();
    private Proceso pre;
    private Proceso visita;

    @BeforeEach
    void setUp() {
        planes = mock(PlanesTiendaService.class);
        parametros = mock(ParametrosTiendaService.class);
        var iva = mock(IvaService.class);
        var procesos = mock(ProcesoRepository.class);
        when(parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100)).thenReturn(true);
        when(parametros.decimal("margenMinimoPorcentaje")).thenReturn(new BigDecimal("30"));
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        pre = proceso(7, "Poligrafía Pre Empleo", 168000);
        visita = proceso(8, "Visita Rutina", 90000);
        when(procesos.findAll()).thenReturn(List.of(pre, visita));
        precios = new PrecioPospagoService(planes, parametros, iva, procesos, mock(UsuarioRepository.class));

        pospagoRepo = mock(ClientePospagoRepository.class);
        movimientos = mock(MovimientoCreditoPospagoRepository.class);
        var recargos = mock(RecargoPendienteService.class);
        when(recargos.calcular(any(), any())).thenReturn(BigDecimal.ZERO);
        cupo = ClientePospago.builder().idCliente(9).limiteCredito(new BigDecimal("9000000")).creditoDisponible(new BigDecimal("8000000")).build();
        when(pospagoRepo.findByIdCliente(9)).thenReturn(Optional.of(cupo));
        when(pospagoRepo.bloquearPorIdCliente(9)).thenReturn(Optional.of(cupo));
        when(movimientos.save(any())).thenAnswer(i -> { cargos.add(i.getArgument(0)); return i.getArgument(0); });
        servicio = new ClientePospagoService(pospagoRepo, movimientos, mock(FacturaPospagoRepository.class), mock(PagoWompiRepository.class),
                mock(CiudadRepository.class), recargos, mock(UsuarioRepository.class), mock(NotificacionRepository.class), iva, precios);
        devolucion = new DevolucionCobroService(mock(SaldoServicioClienteRepository.class), mock(LotePrepagoRepository.class), movimientos, pospagoRepo,
                mock(SaldoServicioClienteService.class), recargos, parametros);
    }

    private Proceso proceso(int id, String nombre, int plan100) {
        Proceso p = Proceso.builder().idProceso(id).nombreProceso(nombre).valor(new BigDecimal("200000")).activo(true).aplicaIva(true).build();
        when(planes.planesDe(p)).thenReturn(List.of(plan(1, "200000"), plan(100, String.valueOf(plan100))));
        return p;
    }

    private PlanPublicoResponse plan(int unidades, String unitario) {
        return new PlanPublicoResponse(unidades, "Plan " + unidades, false, null, new BigDecimal(unitario), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private Servicio pedir(Proceso p, int idServicio) {
        var s = Servicio.builder().idServicio(idServicio).proceso(p).cliente(cliente).build();
        servicio.validarCreditoDisponible(cliente, p, null);
        servicio.descontarCredito(cliente, s, p, null);
        return s;
    }

    @Test
    @DisplayName("Manual $160.000: Nueva solicitud cobra $160.000 + IVA y el cupo baja $190.400")
    void manualSeCobra() {
        pre.setModoPrecioPospago(ModoPrecioPospago.PROPIO);
        pre.setPrecioPospagoPropio(new BigDecimal("160000"));
        assertThat(precios.listaParaCliente(cliente).stream().filter(f -> f.idProceso() == 7).findFirst().orElseThrow().precioConIva()).isEqualByComparingTo("190400");
        pedir(pre, 101);
        assertThat(cargos.get(0).getMontoBase()).isEqualByComparingTo("160000");
        assertThat(cargos.get(0).getMontoTotal()).isEqualByComparingTo("190400");
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("7809600");
    }

    @Test
    @DisplayName("Volver a Automático: la solicitud ya creada conserva $160.000 y una nueva cobra $168.000; cancelar la primera devuelve exactamente $190.400")
    void precioCongelado() {
        pre.setModoPrecioPospago(ModoPrecioPospago.PROPIO);
        pre.setPrecioPospagoPropio(new BigDecimal("160000"));
        var primera = pedir(pre, 101);
        var cargoPrimera = cargos.get(0);

        pre.setModoPrecioPospago(ModoPrecioPospago.PLAN_100);
        pre.setPrecioPospagoPropio(null);
        pedir(pre, 102);
        assertThat(cargoPrimera.getMontoBase()).isEqualByComparingTo("160000");          // el cargo creado no cambió
        assertThat(cargos.get(1).getMontoBase()).isEqualByComparingTo("168000");         // la nueva sí usa el precio de hoy
        assertThat(cupo.getCreditoDisponible()).isEqualByComparingTo("7609680");   // 8.000.000 − 190.400 − 199.920

        BigDecimal antes = cupo.getCreditoDisponible();
        when(movimientos.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(101)).thenReturn(Optional.of(cargoPrimera));
        assertThat(devolucion.devolver(primera)).isTrue();
        assertThat(cupo.getCreditoDisponible().subtract(antes)).isEqualByComparingTo("190400");   // vuelve lo que se descontó, no el precio de hoy
        assertThat(cargoPrimera.getAnuladoEn()).isNotNull();
    }

    @Test
    @DisplayName("El servidor rechaza un servicio no ofrecido o sin precio con el mensaje del plan pospago, aunque el frontend lo haya mandado")
    void servidorRechaza() {
        visita.setOfrecerPospago(false);
        assertThat(precios.listaParaCliente(cliente)).extracting(f -> f.idProceso()).containsExactly(7);
        for (Runnable intento : List.<Runnable>of(() -> servicio.validarCreditoDisponible(cliente, visita, null),
                () -> servicio.descontarCredito(cliente, Servicio.builder().idServicio(1).build(), visita, null))) {
            assertThatThrownBy(intento::run).isInstanceOf(ApiException.class)
                    .hasMessage("Este servicio no está disponible en tu plan pospago. Escríbele a tu gestor.");
        }
        assertThat(cargos).isEmpty();

        visita.setOfrecerPospago(true);
        when(planes.planesDe(visita)).thenReturn(List.of(plan(1, "100000")));   // sin plan de 100 y sin precio manual
        assertThat(precios.disponibleParaCliente(visita)).isFalse();
        assertThat(precios.listaParaCliente(cliente)).extracting(f -> f.idProceso()).containsExactly(7);
        assertThatThrownBy(() -> servicio.validarCreditoDisponible(cliente, visita, null)).hasMessageContaining("no está disponible en tu plan pospago");
    }

    @Test
    @DisplayName("Carga masiva pospago: una fila con un servicio no ofrecido o sin precio se rechaza con el mensaje, y las demás filas no tienen error")
    void cargaMasivaRechazaNoOfrecidos() {
        visita.setOfrecerPospago(false);
        var usuarios = mock(UsuarioRepository.class);
        var clientes = mock(ClienteRepository.class);
        var ciudades = mock(CiudadRepository.class);
        var servicios = mock(ServicioRepository.class);
        var mora = mock(MoraService.class);
        var dias = mock(DiasHabilesService.class);
        var recargos = mock(RecargoPendienteService.class);
        var validador = new CargaMasivaValidador(usuarios, clientes, ciudades, servicios, mock(BolsaPrepagoService.class), pospagoRepo, recargos, mora,
                mock(SolicitudExtraService.class), dias, precios);
        when(usuarios.findByEmail("c@test.com")).thenReturn(Optional.of(Usuario.builder().idUsuario(1L).idCliente(9).build()));
        when(clientes.findById(9)).thenReturn(Optional.of(cliente));
        var bogota = Ciudad.builder().idCiudad(1).nombreCiudad("Bogotá").departamento("Cundinamarca").pendienteConfirmacion(false).build();
        when(ciudades.findAllByOrderByNombreCiudadAsc()).thenReturn(List.of(bogota));
        when(mora.calcular(anyInt())).thenReturn(MoraCliente.Resultado.SIN_MORA);
        when(dias.hoy()).thenReturn(LocalDate.of(2026, 11, 24));
        when(servicios.existeDuplicado(anyString(), anyInt(), any())).thenReturn(false);
        when(recargos.calcular(any(), any())).thenReturn(BigDecimal.ZERO);

        var filas = List.of(
                new FilaCarga(2, "1000001", "Ana", "Pérez", "3001234567", "ana@correo.com", "Bogotá", "", "Analista", "ESTANDAR", "Poligrafía Pre Empleo"),
                new FilaCarga(3, "1000002", "Luis", "Gómez", "3001234567", "luis@correo.com", "Bogotá", "Calle 1 # 2-3", "Analista", "ESTANDAR", "Visita Rutina"));
        var r = validador.validar(filas, "c@test.com", Map.of("poligrafia pre empleo", pre, "visita rutina", visita));
        assertThat(r.errores()).hasSize(1);
        assertThat(r.errores().get(0).fila()).isEqualTo(3);
        assertThat(r.errores().get(0).error()).contains("Este servicio no está disponible en tu plan pospago. Escríbele a tu gestor.");
    }
}
