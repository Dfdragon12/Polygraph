package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.mora.MoraCliente;
import com.polygraph.erp.modules.pagos.service.BolsaPrepagoService;
import com.polygraph.erp.modules.pagos.service.MoraService;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.solicitudes.service.CargaMasivaValidador.Costo;
import com.polygraph.erp.modules.solicitudes.service.CargaMasivaValidador.FilaCarga;
import com.polygraph.erp.modules.solicitudes.service.CargaMasivaValidador.Pedido;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.service.DiasHabilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** NS-12: la carga masiva se revisa completa antes de crear nada. */
class CargaMasivaValidadorTest {

    private UsuarioRepository usuarios;
    private ClienteRepository clientes;
    private CiudadRepository ciudades;
    private ServicioRepository servicios;
    private BolsaPrepagoService bolsa;
    private ClientePospagoRepository pospago;
    private RecargoPendienteService recargos;
    private MoraService mora;
    private SolicitudExtraService extras;
    private DiasHabilesService dias;
    private CargaMasivaValidador validador;

    private final Proceso antecedentes = Proceso.builder().idProceso(10).nombreProceso("Antecedentes").activo(true).valor(new BigDecimal("40000")).build();
    private final Proceso visita = Proceso.builder().idProceso(11).nombreProceso("Visita Domiciliaria").activo(true).requiereDireccion(true).valor(new BigDecimal("90000")).build();
    private final Map<String, Proceso> catalogo = Map.of("antecedentes", antecedentes, "visita domiciliaria", visita);
    private final Ciudad bogota = Ciudad.builder().idCiudad(1).nombreCiudad("Bogotá").departamento("Cundinamarca").pendienteConfirmacion(false).build();

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        clientes = mock(ClienteRepository.class);
        ciudades = mock(CiudadRepository.class);
        servicios = mock(ServicioRepository.class);
        bolsa = mock(BolsaPrepagoService.class);
        pospago = mock(ClientePospagoRepository.class);
        recargos = mock(RecargoPendienteService.class);
        mora = mock(MoraService.class);
        extras = mock(SolicitudExtraService.class);
        dias = mock(DiasHabilesService.class);
        validador = new CargaMasivaValidador(usuarios, clientes, ciudades, servicios, bolsa, pospago, recargos, mora, extras, dias);

        when(usuarios.findByEmail("c@test.com")).thenReturn(Optional.of(Usuario.builder().idUsuario(1L).idCliente(7).build()));
        Cliente cliente = new Cliente();
        cliente.setIdCliente(7);
        cliente.setTipoCliente(TipoCliente.PREPAGO);
        when(clientes.findById(7)).thenReturn(Optional.of(cliente));
        when(ciudades.findAllByOrderByNombreCiudadAsc()).thenReturn(List.of(bogota));
        when(mora.calcular(anyInt())).thenReturn(MoraCliente.Resultado.SIN_MORA);
        when(dias.hoy()).thenReturn(LocalDate.of(2026, 10, 28));
        when(servicios.existeDuplicado(anyString(), anyInt(), any())).thenReturn(false);
        when(recargos.calcular(any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private static FilaCarga fila(int numero, String cedula, String servicio) {
        return new FilaCarga(numero, cedula, "Ana", "Pérez", "3001234567", "ana@correo.com", "Bogotá", "", "Analista", "ESTANDAR", servicio);
    }

    private List<FilaCarga> archivo(int n) {
        List<FilaCarga> filas = new ArrayList<>();
        for (int i = 0; i < n; i++) filas.add(fila(i + 2, String.valueOf(1000000 + i), "Antecedentes"));
        return filas;
    }

    @Test
    void un_archivo_con_mas_filas_que_unidades_se_rechaza_con_el_detalle_y_no_trae_solicitudes() {
        when(bolsa.disponible(7, 10)).thenReturn(3);
        var r = validador.validar(archivo(5), "c@test.com", catalogo);
        assertThat(r.valido()).isFalse();
        assertThat(r.solicitudes()).isEmpty();                       // no se crea nada
        assertThat(r.errores()).extracting(e -> e.fila()).containsExactly(5, 6);   // las que pasan de 3
        assertThat(r.errores().get(0).error()).contains("Sin saldo de 'Antecedentes'").contains("el archivo pide 5").contains("tienes 3 disponibles");
    }

    @Test
    void un_archivo_valido_trae_una_solicitud_por_fila() {
        when(bolsa.disponible(7, 10)).thenReturn(5);
        var r = validador.validar(archivo(5), "c@test.com", catalogo);
        assertThat(r.valido()).isTrue();
        assertThat(r.solicitudes()).hasSize(5);
        assertThat(r.solicitudes()).allSatisfy(s -> {
            assertThat(s.procesosIds()).containsExactly(10);
            assertThat(s.idCiudad()).isEqualTo(1);
            assertThat(s.criticidadCargo()).isEqualTo("ESTANDAR");
        });
    }

    @Test
    void cada_fila_se_revisa_con_las_reglas_del_formulario_y_el_reporte_trae_todas() {
        when(bolsa.disponible(7, 10)).thenReturn(10);
        var filas = List.of(
                new FilaCarga(2, "ABC", "Ana", "Pérez", "3001234567", "ana@correo.com", "Bogotá", "", "Analista", "ESTANDAR", "Antecedentes"),
                new FilaCarga(3, "2000001", "Luis", "Gómez", "2001234567", "sin-arroba", "Bogotá", "", "", "", "Antecedentes"),
                new FilaCarga(4, "2000002", "Eva", "Ríos", "3001234567", "eva@correo.com", "Atlantis", "", "Analista", "ESTANDAR", "Antecedentes"),
                new FilaCarga(5, "2000003", "Rut", "Mora", "3001234567", "rut@correo.com", "Bogotá", "", "Analista", "ESTANDAR", "Servicio inexistente"),
                new FilaCarga(6, "2000004", "Uno", "Dos", "3001234567", "uno@correo.com", "Bogotá", "", "Analista", "ESTANDAR", "Visita Domiciliaria"));
        var r = validador.validar(filas, "c@test.com", catalogo);
        assertThat(r.errores()).extracting(e -> e.fila()).containsExactly(2, 3, 4, 5, 6);
        assertThat(r.errores().get(0).error()).contains("cédula debe ser numérica");
        assertThat(r.errores().get(1).error()).contains("celular debe tener 10 dígitos").contains("correo no tiene un formato válido").contains("cargo es obligatorio")
                .contains("criticidad del cargo es obligatoria");
        assertThat(r.errores().get(2).error()).contains("'Atlantis' no está en la lista de ciudades confirmadas");
        assertThat(r.errores().get(3).error()).contains("Tipo de servicio no reconocido");
        assertThat(r.errores().get(4).error()).contains("dirección de la visita es obligatoria");   // el servicio incluye una visita
    }

    @Test
    void la_misma_cedula_y_servicio_dos_veces_o_un_duplicado_reciente_se_rechazan() {
        when(bolsa.disponible(7, 10)).thenReturn(10);
        when(servicios.existeDuplicado("1000001", 10, LocalDate.of(2026, 7, 28))).thenReturn(true);
        var filas = List.of(fila(2, "1000000", "Antecedentes"), fila(3, "1000000", "Antecedentes"), fila(4, "1000001", "Antecedentes"));
        var r = validador.validar(filas, "c@test.com", catalogo);
        assertThat(r.errores()).extracting(e -> e.fila()).containsExactly(3, 4);
        assertThat(r.errores().get(0).error()).contains("repetida en el archivo");
        assertThat(r.errores().get(1).error()).contains("últimos 3 meses");
    }

    @Test
    void con_mora_el_archivo_completo_se_rechaza_con_el_mensaje_de_la_factura() {
        when(bolsa.disponible(7, 10)).thenReturn(10);
        when(mora.calcular(7)).thenReturn(new MoraCliente.Resultado(true, 1, 30, "F-1", new BigDecimal("500000"), "Tu cuenta está en mora: tienes 1 factura vencida."));
        var r = validador.validar(archivo(2), "c@test.com", catalogo);
        assertThat(r.valido()).isFalse();
        assertThat(r.errores().get(0).fila()).isZero();
        assertThat(r.errores().get(0).error()).contains("Tu cuenta está en mora");
        assertThat(r.solicitudes()).isEmpty();
    }

    // ───────────── reglas del conjunto ─────────────

    @Test
    void saldo_prepago_se_cuenta_por_servicio_sumando_todas_las_filas() {
        var pedidos = List.of(new Pedido(2, "1", 10, "Antecedentes"), new Pedido(3, "2", 11, "Visita"), new Pedido(4, "3", 10, "Antecedentes"),
                new Pedido(5, "4", 10, "Antecedentes"));
        var errores = CargaMasivaValidador.filasSinSaldo(pedidos, Map.of(10, 2, 11, 1));
        assertThat(errores).extracting(e -> e.fila()).containsExactly(5);
        assertThat(errores.get(0).error()).contains("el archivo pide 3").contains("tienes 2 disponibles").contains("n.º 3");
    }

    @Test
    void cupo_pospago_se_gasta_en_el_orden_del_archivo() {
        var costos = List.of(new Costo(2, "1", new BigDecimal("40000")), new Costo(3, "2", new BigDecimal("40000")), new Costo(4, "3", new BigDecimal("40000")));
        var errores = CargaMasivaValidador.filasSinCupo(costos, new BigDecimal("100000"));
        assertThat(errores).extracting(e -> e.fila()).containsExactly(4);
        assertThat(errores.get(0).error()).contains("$120.000").contains("$100.000").contains("faltan $20.000");
        assertThat(CargaMasivaValidador.filasSinCupo(costos, new BigDecimal("120000"))).isEmpty();
    }

    @Test
    void la_ciudad_se_reconoce_sin_tildes_ni_mayusculas_y_pide_departamento_si_se_repite() {
        Ciudad sanVicente1 = Ciudad.builder().idCiudad(5).nombreCiudad("San Vicente").departamento("Antioquia").pendienteConfirmacion(false).build();
        Ciudad sanVicente2 = Ciudad.builder().idCiudad(6).nombreCiudad("San Vicente").departamento("Santander").pendienteConfirmacion(false).build();
        var lista = List.of(bogota, sanVicente1, sanVicente2);
        assertThat(CargaMasivaValidador.resolverCiudad("BOGOTA", lista).ciudad()).isSameAs(bogota);
        assertThat(CargaMasivaValidador.resolverCiudad("Bogotá - Cundinamarca", lista).ciudad()).isSameAs(bogota);
        assertThat(CargaMasivaValidador.resolverCiudad("San Vicente", lista).error()).contains("varios departamentos");
        assertThat(CargaMasivaValidador.resolverCiudad("san vicente, santander", lista).ciudad()).isSameAs(sanVicente2);
        assertThat(CargaMasivaValidador.resolverCiudad("Nueva", lista).error()).contains("no está en la lista");
    }
}
