package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.controller.ListaPospagoAdminController;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.entity.HistorialPrecioPospago;
import com.polygraph.erp.modules.tienda.entity.HistorialPrecioPospago.Accion;
import com.polygraph.erp.modules.tienda.repository.HistorialPrecioPospagoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LP-03: editar la lista pospago: precio manual, volver a automático, ofrecido, restablecer todo, historial, interruptor apagado y permisos. */
@DisplayName("LP-03 — edición de la lista pospago")
class LP03ListaPospagoAdminTest {

    private ParametrosTiendaService parametros;
    private PlanesTiendaService planes;
    private ProcesoRepository procesos;
    private UsuarioRepository usuarios;
    private HistorialPrecioPospagoRepository historialRepo;
    private ListaPospagoAdminService servicio;
    private final List<HistorialPrecioPospago> historial = new ArrayList<>();
    private Proceso pre;
    private Proceso visita;

    @BeforeEach
    void setUp() {
        parametros = mock(ParametrosTiendaService.class);
        planes = mock(PlanesTiendaService.class);
        procesos = mock(ProcesoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        historialRepo = mock(HistorialPrecioPospagoRepository.class);
        var iva = mock(IvaService.class);
        when(parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100)).thenReturn(true);
        when(parametros.decimal("margenMinimoPorcentaje")).thenReturn(new BigDecimal("30"));
        when(iva.porcentaje()).thenReturn(new BigDecimal("19"));
        var admin = Usuario.builder().idUsuario(7L).nombre("Ana").apellido("Admin").email("admin@x.co").build();
        when(usuarios.findByEmail("admin@x.co")).thenReturn(Optional.of(admin));
        when(usuarios.findById(7L)).thenReturn(Optional.of(admin));
        when(usuarios.findAllById(any())).thenReturn(List.of(admin));
        when(historialRepo.save(any())).thenAnswer(i -> { historial.add(i.getArgument(0)); return i.getArgument(0); });
        when(historialRepo.findTop50ByIdProcesoOrderByFechaDescIdDesc(1))
                .thenAnswer(i -> { var l = new ArrayList<>(historial.stream().filter(h -> h.getIdProceso() == 1).toList()); java.util.Collections.reverse(l); return l; });

        pre = proceso(1, "Poligrafía Pre Empleo", 168000);
        visita = proceso(2, "Visita Rutina", 90000);
        when(procesos.findAll()).thenReturn(List.of(pre, visita));
        var precios = new PrecioPospagoService(planes, parametros, iva, procesos, usuarios);
        servicio = new ListaPospagoAdminService(precios, procesos, usuarios, historialRepo, Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC));
    }

    private Proceso proceso(int id, String nombre, int plan100) {
        Proceso p = Proceso.builder().idProceso(id).nombreProceso(nombre).valor(new BigDecimal("200000")).activo(true).aplicaIva(true).build();
        when(planes.planesDe(p)).thenReturn(List.of(plan(1, "200000"), plan(100, String.valueOf(plan100))));
        when(procesos.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private PlanPublicoResponse plan(int unidades, String unitario) {
        return new PlanPublicoResponse(unidades, "Plan " + unidades, false, null, new BigDecimal(unitario), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    @Test
    @DisplayName("Precio manual: queda Manual, devuelve la fila con quién y cuándo, y deja una fila en el historial con el precio antes y después")
    void precioManual() {
        var fila = servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co");
        assertThat(fila.modo()).isEqualTo("MANUAL");
        assertThat(fila.precio()).isEqualByComparingTo("160000");
        assertThat(fila.precioPlan100()).isEqualByComparingTo("168000");
        assertThat(fila.actualizadoPor()).isEqualTo("Ana Admin");
        assertThat(fila.actualizadoEn()).isNotNull();
        assertThat(historial).hasSize(1);
        var h = historial.get(0);
        assertThat(h.getAccion()).isEqualTo(Accion.PRECIO_MANUAL);
        assertThat(h.getPrecioAnterior()).isEqualTo(168000L);
        assertThat(h.getPrecioNuevo()).isEqualTo(160000L);
        assertThat(h.getModoAnterior()).isEqualTo("AUTO");
        assertThat(h.getModoNuevo()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Precio por debajo del costo: guarda y marca margen bajo")
    void debajoDelCosto() {
        pre.setCostoDirecto(new BigDecimal("150000"));
        var fila = servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co");
        assertThat(fila.margenBajo()).isTrue();
        assertThat(fila.precio()).isEqualByComparingTo("160000");
    }

    @Test
    @DisplayName("Precio 0, vacío, negativo o con decimales: 400 «El precio debe ser mayor que 0»; AUTO guarda el manual en null")
    void validaciones() {
        for (BigDecimal malo : new BigDecimal[]{BigDecimal.ZERO, null, new BigDecimal("-5")}) {
            assertThatThrownBy(() -> servicio.actualizar(1, "MANUAL", malo, true, "admin@x.co"))
                    .isInstanceOf(ApiException.class).hasMessage("El precio debe ser mayor que 0");
        }
        assertThatThrownBy(() -> servicio.actualizar(1, "MANUAL", new BigDecimal("1500.5"), true, "admin@x.co")).isInstanceOf(ApiException.class);
        assertThat(historial).isEmpty();
        servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co");
        servicio.actualizar(1, "AUTO", new BigDecimal("999"), true, "admin@x.co");
        assertThat(pre.getPrecioPospagoPropio()).isNull();
    }

    @Test
    @DisplayName("Volver a Automático: el precio vuelve al plan de 100 y queda VOLVER_AUTO")
    void volverAutomatico() {
        servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co");
        var fila = servicio.actualizar(1, "AUTO", null, true, "admin@x.co");
        assertThat(fila.modo()).isEqualTo("AUTO");
        assertThat(fila.precio()).isEqualByComparingTo("168000");
        assertThat(historial.get(1).getAccion()).isEqualTo(Accion.VOLVER_AUTO);
        assertThat(historial.get(1).getPrecioAnterior()).isEqualTo(160000L);
        assertThat(historial.get(1).getPrecioNuevo()).isEqualTo(168000L);
    }

    @Test
    @DisplayName("Sin cambios no se escribe historial; marcar Ofrecido = No lo deja en el historial y el servicio ya no es visible para el cliente")
    void ofrecido() {
        servicio.actualizar(2, "AUTO", null, true, "admin@x.co");
        assertThat(historial).isEmpty();
        var fila = servicio.actualizar(2, "AUTO", null, false, "admin@x.co");
        assertThat(fila.ofrecido()).isFalse();
        assertThat(fila.visibleParaCliente()).isFalse();
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getAccion()).isEqualTo(Accion.OFRECIDO_NO);
        assertThat(historial.get(0).getOfrecidoAnterior()).isTrue();
        assertThat(historial.get(0).getOfrecidoNuevo()).isFalse();
    }

    @Test
    @DisplayName("Restablecer todo: pone en Automático solo los Manuales, no cambia Ofrecido y deja una fila RESTABLECER_TODOS por cada uno")
    void restablecerTodos() {
        servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co");
        servicio.actualizar(2, "AUTO", null, false, "admin@x.co");
        historial.clear();
        var lista = servicio.restablecerTodos("admin@x.co");
        assertThat(lista).allMatch(f -> f.modo().equals("AUTO"));
        assertThat(lista.stream().filter(f -> f.idProceso() == 2).findFirst().orElseThrow().ofrecido()).isFalse();
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getAccion()).isEqualTo(Accion.RESTABLECER_TODOS);
        assertThat(historial.get(0).getPrecioAnterior()).isEqualTo(160000L);
        assertThat(historial.get(0).getPrecioNuevo()).isEqualTo(168000L);
    }

    @Test
    @DisplayName("El historial trae el nombre del usuario, sin motivo, la más reciente primero")
    void historialConUsuario() {
        servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co");
        servicio.actualizar(1, "AUTO", null, true, "admin@x.co");
        var items = servicio.historial(1);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).accion()).isEqualTo("VOLVER_AUTO");
        assertThat(items.get(0).usuario()).isEqualTo("Ana Admin");
    }

    @Test
    @DisplayName("Con el interruptor apagado todo responde 409 con el mensaje de la lista apagada")
    void interruptorApagado() {
        when(parametros.activo(ParametrosTiendaService.POSPAGO_PLAN_100)).thenReturn(false);
        for (Runnable accion : List.<Runnable>of(
                () -> servicio.actualizar(1, "MANUAL", new BigDecimal("160000"), true, "admin@x.co"),
                () -> servicio.restablecerTodos("admin@x.co"),
                () -> servicio.historial(1))) {
            assertThatThrownBy(accion::run).isInstanceOf(ApiException.class)
                    .hasMessage("La lista de precios pospago está apagada en Admin › Catálogo")
                    .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        }
        assertThat(historial).isEmpty();
    }

    @Test
    @DisplayName("Solo el administrador: el controlador exige el rol ADMIN_POLYGRAPH (cualquier otro recibe 403 de Spring Security)")
    void soloAdministrador() {
        PreAuthorize anotacion = ListaPospagoAdminController.class.getAnnotation(PreAuthorize.class);
        assertThat(anotacion).isNotNull();
        assertThat(anotacion.value()).isEqualTo("hasRole('ADMIN_POLYGRAPH')");
    }
}
