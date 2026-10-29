package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.CategoriaNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository;
import com.polygraph.erp.modules.evaluados.entity.DocumentoEvaluado;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.catalogo.entity.DocumentoRequeridoProceso;
import com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.springframework.http.HttpStatus;
import com.polygraph.erp.modules.servicios.entity.*;
import com.polygraph.erp.modules.servicios.repository.*;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudDetalleResponse;
import com.polygraph.erp.shared.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Qué ve el cliente en el detalle de su solicitud: solo novedades de tipos visibles (sin la
 *  descripción del ejecutor), si el candidato ya diligenció su información, y nunca el correo
 *  del empleado interno en el historial. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SolicitudService — detalle de la solicitud visto por el cliente")
class SolicitudServiceDetalleClienteTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ServicioSubprocesoRepository servicioSubprocesoRepository;
    @Mock private HistorialEstadoServicioRepository historialRepository;
    @Mock private NovedadSubprocesoRepository novedadSubprocesoRepository;
    @Mock private NovedadSeguimientoRepository novedadSeguimientoRepository;
    @Mock private HojaVidaEvaluadoRepository hojaVidaEvaluadoRepository;
    @Mock private LinkCandidatoRepository linkCandidatoRepository;
    @Mock private DocumentoEvaluadoRepository documentoEvaluadoRepository;
    @Mock private DocumentoRequeridoProcesoRepository documentoRequeridoProcesoRepository;
    @Mock private com.polygraph.erp.modules.pagos.service.RecargoPendienteService recargoPendienteService;
    @Mock private com.polygraph.erp.modules.pagos.repository.MovimientoCreditoPospagoRepository movimientoCreditoPospagoRepository;
    @Mock private com.polygraph.erp.modules.pagos.repository.OrdenCompraItemRepository ordenCompraItemRepository;
    @Mock private com.polygraph.erp.modules.pagos.service.DevolucionCobroService devolucionCobroService;

    @Mock private com.polygraph.erp.shared.service.DiasHabilesService diasHabilesService;
    @Mock private com.polygraph.erp.shared.service.TiempoServicioService tiempoServicioService;

    @Mock private com.polygraph.erp.modules.servicios.service.SemaforoTiempoService semaforoTiempoService;

    @InjectMocks private SolicitudService service;

    private final UserDetails sesionCliente = User.withUsername("cliente@test.com").password("x").roles("ADMIN_CLIENTE").build();
    private final UserDetails sesionGestor = User.withUsername("gestor@test.com").password("x").roles("GESTOR").build();
    private Servicio servicio;

    @BeforeEach
    void setUp() {
        servicio = Servicio.builder().idServicio(40).estado(EstadoServicio.EN_EJECUCION)
                .cliente(Cliente.builder().idCliente(7).idGestor(4L).build())
                .candidato(Candidato.builder().idCandidato(5L).cedula("1000222333").build()).build();
        ServicioSubproceso poligrafia = ServicioSubproceso.builder().id(900L).servicio(servicio).estado(EstadoSubproceso.EN_EJECUCION)
                .tipoProgreso(TipoProgreso.builder().nombreProgreso("Poligrafía").requiereDocumento(true).build()).build();

        CategoriaNovedad asistencia = CategoriaNovedad.builder().nombre("Asistencia del candidato").color("red").build();
        NovedadSubproceso noLlego = NovedadSubproceso.builder().id(1L).servicioSubproceso(poligrafia)
                .tipo(TipoNovedad.builder().nombre("El candidato no llegó").categoria(asistencia).visibleCliente(true).build())
                .descripcion("Esperé hasta las 10:30 y no contestó el celular").estado(EstadoNovedad.RESUELTA)
                .respuesta("Se reprogramó con el candidato").fechaRegistro(LocalDateTime.now().minusDays(1)).build();
        NovedadSubproceso tecnica = NovedadSubproceso.builder().id(2L).servicioSubproceso(poligrafia)
                .tipo(TipoNovedad.builder().nombre("Falla técnica").visibleCliente(false).build())
                .descripcion("El equipo no encendió").estado(EstadoNovedad.ABIERTA).fechaRegistro(LocalDateTime.now()).build();
        LocalDateTime nuevaCita = LocalDateTime.now().plusDays(2);

        when(servicioRepository.findById(40)).thenReturn(Optional.of(servicio));
        when(usuarioRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(
                Usuario.builder().idUsuario(3L).email("cliente@test.com").rol(Rol.ADMIN_CLIENTE).idCliente(7).build()));
        when(usuarioRepository.findByEmail("gestor@test.com")).thenReturn(Optional.of(
                Usuario.builder().idUsuario(4L).email("gestor@test.com").rol(Rol.GESTOR).build()));
        when(servicioSubprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(40)).thenReturn(List.of(poligrafia));
        when(historialRepository.findByServicio_IdServicioOrderByFechaCambioDesc(40)).thenReturn(List.of(
                HistorialEstadoServicio.builder().estadoAnterior(EstadoServicio.PENDIENTE).estadoNuevo(EstadoServicio.EN_EJECUCION)
                        .usuario(Usuario.builder().email("analista.interna@polygraph.com").build())
                        .fechaCambio(LocalDateTime.now()).build()));
        when(novedadSubprocesoRepository.findByServicioSubproceso_IdOrderByFechaRegistroAsc(900L)).thenReturn(List.of(noLlego, tecnica));
        when(novedadSeguimientoRepository.findByNovedad_IdInOrderByFechaAscIdAsc(any())).thenReturn(List.of(
                NovedadSeguimiento.builder().novedad(noLlego).tipo(TipoSeguimientoNovedad.REPROGRAMACION).fechaNueva(nuevaCita).build()));
        when(hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(5L)).thenReturn(Optional.empty());
        when(linkCandidatoRepository.findTopByServicio_IdServicioOrderByFechaCreacionDesc(40)).thenReturn(Optional.of(
                // V197: el link ya no vence por tiempo; "VENCIDO" = el candidato ya no puede entrar.
                LinkCandidato.builder().fechaExpiracion(LocalDateTime.now().minusHours(3)).revocado(true).build()));
    }

    @Test
    @DisplayName("Solo ve las novedades de tipos visibles, sin la descripción del ejecutor, con la nueva fecha")
    void novedadesVisibles() {
        SolicitudDetalleResponse d = service.obtenerDetalle(40, sesionCliente);

        assertThat(d.novedades()).hasSize(1);
        var n = d.novedades().get(0);
        assertThat(n.tipo()).isEqualTo("El candidato no llegó");
        assertThat(n.subproceso()).isEqualTo("Poligrafía");
        assertThat(n.estado()).isEqualTo("RESUELTA");
        assertThat(n.respuesta()).isEqualTo("Se reprogramó con el candidato");
        assertThat(n.reprogramadoPara()).isNotNull();
        assertThat(n.toString()).doesNotContain("Esperé hasta las 10:30").doesNotContain("El equipo no encendió");
    }

    @Test
    @DisplayName("El cliente no ve el correo del empleado interno en el historial; el gestor sí")
    void historialSinCorreoInterno() {
        assertThat(service.obtenerDetalle(40, sesionCliente).historial().get(0).usuario()).isNull();
        assertThat(service.obtenerDetalle(40, sesionGestor).historial().get(0).usuario()).isEqualTo("analista.interna@polygraph.com");
    }

    @Test
    @DisplayName("Documentos: lo que pide el proceso marcado como cargado o no, sin ruta del archivo; y el % del candidato")
    void documentosYAvanceDelCandidato() {
        servicio.setProceso(Proceso.builder().idProceso(3).build());
        HojaVidaEvaluado hv = HojaVidaEvaluado.builder().id(8L).completado(false).progresoPorcentaje(60).pasoActual(4).build();
        when(hojaVidaEvaluadoRepository.findByCandidato_IdCandidato(5L)).thenReturn(Optional.of(hv));
        when(documentoRequeridoProcesoRepository.findByProceso_IdProcesoOrderByOrdenAscNombreAsc(3)).thenReturn(List.of(
                DocumentoRequeridoProceso.builder().clave("cedula").nombre("Cédula").obligatorio(true).build(),
                DocumentoRequeridoProceso.builder().clave("diploma").nombre("Diploma").obligatorio(false).build()));
        when(documentoEvaluadoRepository.findByHojaVidaEvaluado_IdOrderByFechaCargaDesc(8L)).thenReturn(List.of(
                DocumentoEvaluado.builder().tipoDocumento("CEDULA").rutaArchivo("evaluados/5/cedula.pdf").fechaCarga(LocalDateTime.now()).build()));

        SolicitudDetalleResponse d = service.obtenerDetalle(40, sesionCliente);
        assertThat(d.documentosCandidato()).extracting(SolicitudDetalleResponse.DocumentoCandidatoResponse::nombre,
                SolicitudDetalleResponse.DocumentoCandidatoResponse::cargado)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Cédula", true), org.assertj.core.groups.Tuple.tuple("Diploma", false));
        assertThat(d.toString()).doesNotContain("evaluados/5/cedula.pdf");
        assertThat(d.estadoCandidato().porcentaje()).isEqualTo(60);
    }

    @Test
    @DisplayName("Cancelar: el cliente solo puede antes de la ejecución y con motivo")
    void cancelarReglasDelCliente() {
        assertThatThrownBy(() -> service.cambiarEstado(40, new CambioEstadoRequest("CANCELADO", "Ya no lo necesitamos"), sesionCliente))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT)); // EN_EJECUCION

        servicio.setEstado(EstadoServicio.PENDIENTE);
        assertThatThrownBy(() -> service.cambiarEstado(40, new CambioEstadoRequest("CANCELADO", "  "), sesionCliente))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        service.cambiarEstado(40, new CambioEstadoRequest("CANCELADO", "El candidato desistió"), sesionCliente);
        assertThat(servicio.getEstado()).isEqualTo(EstadoServicio.CANCELADO);
    }

    @Test
    @DisplayName("Cobro pospago: valores exactos del cargo de la solicitud y el estado de su factura")
    void cobroPospago() {
        servicio.getCliente().setTipoCliente(TipoCliente.POSPAGO);
        when(movimientoCreditoPospagoRepository.findFirstByServicio_IdServicioOrderByFechaGeneradoDesc(40)).thenReturn(Optional.of(
                com.polygraph.erp.modules.pagos.entity.MovimientoCreditoPospago.builder()
                        .montoBase(new java.math.BigDecimal("180000")).montoRecargoCiudad(new java.math.BigDecimal("25000"))
                        .montoTotal(new java.math.BigDecimal("205000"))
                        .factura(com.polygraph.erp.modules.pagos.entity.FacturaPospago.builder().referencia("FAC-0012")
                                .estado(EstadoFacturaPospago.PENDIENTE).build())
                        .build()));

        var c = service.obtenerDetalle(40, sesionCliente).cobro();
        assertThat(c.modalidad()).isEqualTo("POSPAGO");
        assertThat(c.total()).isEqualByComparingTo("205000");
        assertThat(c.recargoPagado()).isEqualByComparingTo("25000");
        assertThat(c.referencia()).isEqualTo("FAC-0012");
        assertThat(c.estadoPago()).isEqualTo("PENDIENTE");
        assertThat(c.valorReferencia()).isFalse();
    }

    @Test
    @DisplayName("Cobro prepago: valor unitario de la última compra aprobada (con su descuento), marcado como referencia")
    void cobroPrepago() {
        servicio.getCliente().setTipoCliente(TipoCliente.PREPAGO);
        servicio.setProceso(Proceso.builder().idProceso(3).build());
        var orden = com.polygraph.erp.modules.pagos.entity.OrdenCompra.builder().referencia("OC-77").estado(EstadoOrdenCompra.APROBADA).build();
        when(ordenCompraItemRepository.findFirstByOrdenCompra_Cliente_IdClienteAndProceso_IdProcesoAndOrdenCompra_EstadoOrderByOrdenCompra_FechaCreacionDesc(
                7, 3, EstadoOrdenCompra.APROBADA)).thenReturn(Optional.of(
                com.polygraph.erp.modules.pagos.entity.OrdenCompraItem.builder().ordenCompra(orden)
                        .valorOriginal(new java.math.BigDecimal("200000")).valorUnitario(new java.math.BigDecimal("170000"))
                        .nombreDescuentoAplicado("Volumen 15%").build()));

        var c = service.obtenerDetalle(40, sesionCliente).cobro();
        assertThat(c.modalidad()).isEqualTo("PREPAGO");
        assertThat(c.valorServicio()).isEqualByComparingTo("170000");
        assertThat(c.valorSinDescuento()).isEqualByComparingTo("200000");
        assertThat(c.descuento()).isEqualTo("Volumen 15%");
        assertThat(c.valorReferencia()).isTrue();
        assertThat(c.referencia()).isEqualTo("OC-77");
    }

    @Test
    @DisplayName("Si el candidato ya no puede entrar a su link (ej. revocado) sin terminar, el cliente lo ve como VENCIDO")
    void estadoCandidatoVencido() {
        assertThat(service.obtenerDetalle(40, sesionCliente).estadoCandidato().estado()).isEqualTo("VENCIDO");
    }
}
