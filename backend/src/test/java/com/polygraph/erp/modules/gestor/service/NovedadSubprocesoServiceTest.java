package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.gestor.dto.NovedadesSubprocesoResponse;
import com.polygraph.erp.modules.gestor.dto.RegistrarNovedadRequest;
import com.polygraph.erp.modules.gestor.dto.ResolverNovedadRequest;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.NovedadSubproceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.entity.NovedadSeguimiento;
import com.polygraph.erp.modules.servicios.repository.NovedadSeguimientoRepository;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
import com.polygraph.erp.modules.gestor.dto.AgregarSeguimientoRequest;
import com.polygraph.erp.shared.enums.TipoSeguimientoNovedad;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoNovedad;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.modules.catalogo.entity.CategoriaNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import com.polygraph.erp.modules.catalogo.repository.TipoNovedadRepository;
import java.util.HashMap;
import java.util.Map;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NovedadSubprocesoService — quién reporta, quién resuelve y a quién se avisa")
class NovedadSubprocesoServiceTest {

    @Mock private NovedadSubprocesoRepository novedadRepository;
    @Mock private ServicioSubprocesoRepository subprocesoRepository;
    @Mock private NotificacionService notificacionService;
    @Mock private NovedadSeguimientoRepository seguimientoRepository;
    @Mock private AsignacionService asignacionService;
    @Mock private TipoNovedadRepository tipoNovedadRepository;

    @InjectMocks
    private NovedadSubprocesoService service;

    private Usuario poligrafista;
    private Usuario otroPoligrafista;
    private Usuario gestor;
    private Usuario programador;
    private ServicioSubproceso subproceso;
    /** Catálogo de prueba (antes era el enum TipoNovedadSubproceso), por clave. */
    private final Map<String, TipoNovedad> tipos = new HashMap<>();

    @BeforeEach
    void setUp() {
        poligrafista = usuario(10L, Rol.POLIGRAFISTA);
        otroPoligrafista = usuario(11L, Rol.POLIGRAFISTA);
        gestor = usuario(20L, Rol.GESTOR);
        programador = usuario(30L, Rol.PROGRAMADOR);

        Cliente cliente = Cliente.builder().idCliente(1).idGestor(gestor.getIdUsuario()).build();
        Servicio servicio = Servicio.builder().idServicio(100).cliente(cliente).build();
        TipoProgreso tipo = TipoProgreso.builder().idTipoProgreso(5).nombreProgreso("Poligrafía").rolResponsable(Rol.POLIGRAFISTA).build();
        subproceso = ServicioSubproceso.builder()
                .id(500L).servicio(servicio).tipoProgreso(tipo)
                .usuarioAsignado(poligrafista).programadorResponsable(programador)
                .estado(EstadoAsignacion.ASIGNADO).build();

        CategoriaNovedad asistencia = CategoriaNovedad.builder().idCategoriaNovedad(1).clave("ASISTENCIA").nombre("Asistencia").color("red").build();
        CategoriaNovedad logistica = CategoriaNovedad.builder().idCategoriaNovedad(2).clave("LOGISTICA").nombre("Logística").color("orange").build();
        CategoriaNovedad otras = CategoriaNovedad.builder().idCategoriaNovedad(3).clave("OTRA").nombre("Otras").color("gray").build();
        registrarTipo(1, "CANDIDATO_NO_ASISTIO", asistencia, true, Rol.POLIGRAFISTA, Rol.VISITADOR);
        registrarTipo(2, "CANDIDATO_LLEGO_TARDE", asistencia, true, Rol.POLIGRAFISTA, Rol.VISITADOR);
        registrarTipo(3, "CANDIDATO_NO_APTO", asistencia, true, Rol.POLIGRAFISTA);
        registrarTipo(4, "DIRECCION_ERRADA", logistica, true, Rol.VISITADOR);
        registrarTipo(5, "FUENTE_NO_DISPONIBLE", otras, true, Rol.ANALISTA_INTERNO);
        registrarTipo(6, "OTRA", otras, true, Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);
        registrarTipo(7, "DESACTIVADO", asistencia, false, Rol.POLIGRAFISTA);
        lenient().when(tipoNovedadRepository.findAllByOrderByCategoria_OrdenAscOrdenAscNombreAsc())
                .thenReturn(tipos.values().stream().sorted(java.util.Comparator.comparing(TipoNovedad::getIdTipoNovedad)).toList());

        lenient().when(subprocesoRepository.findById(500L)).thenReturn(Optional.of(subproceso));
        lenient().when(novedadRepository.save(any(NovedadSubproceso.class))).thenAnswer(inv -> {
            NovedadSubproceso n = inv.getArgument(0);
            n.setId(900L);
            return n;
        });
    }

    @Test
    @DisplayName("El poligrafista asignado registra 'candidato no asistió' y se avisa a gestor y programador")
    void registrar_avisaAGestorYProgramador() {
        var r = service.registrar(500L, new RegistrarNovedadRequest(tipo("CANDIDATO_NO_ASISTIO").getIdTipoNovedad(), "Esperé 40 min, no contestó."), poligrafista);

        assertThat(r.tipo()).isEqualTo("CANDIDATO_NO_ASISTIO");
        assertThat(r.estado()).isEqualTo("ABIERTA");
        verify(notificacionService).crearParaUsuario(eq(20L), eq("NOVEDAD_REGISTRADA"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(500L));
        verify(notificacionService).crearParaUsuario(eq(30L), eq("NOVEDAD_REGISTRADA"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(500L));
    }

    @Test
    @DisplayName("Un poligrafista que no tiene asignado el subproceso no puede reportar")
    void registrar_noAsignado_403() {
        assertThatThrownBy(() -> service.registrar(500L, new RegistrarNovedadRequest(tipo("CANDIDATO_NO_ASISTIO").getIdTipoNovedad(), "x"), otroPoligrafista))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(novedadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un tipo que no aplica al rol del subproceso (dirección errada en poligrafía) se rechaza")
    void registrar_tipoNoAplica_400() {
        assertThatThrownBy(() -> service.registrar(500L, new RegistrarNovedadRequest(tipo("DIRECCION_ERRADA").getIdTipoNovedad(), "x"), poligrafista))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Un tipo desactivado en el catálogo ya no se puede usar")
    void registrar_tipoDesactivado_400() {
        assertThatThrownBy(() -> service.registrar(500L, new RegistrarNovedadRequest(tipo("DESACTIVADO").getIdTipoNovedad(), "x"), poligrafista))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Un subproceso publicado ya no admite novedades")
    void registrar_publicado_409() {
        subproceso.setEstado(EstadoAsignacion.PUBLICADO);
        assertThatThrownBy(() -> service.registrar(500L, new RegistrarNovedadRequest(tipo("OTRA").getIdTipoNovedad(), "x"), poligrafista))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Listar muestra solo los tipos que aplican a poligrafía y los permisos del actor")
    void listar_tiposYPermisos() {
        when(novedadRepository.findByServicioSubproceso_IdOrderByFechaRegistroAsc(500L)).thenReturn(List.of());

        NovedadesSubprocesoResponse r = service.listar(500L, poligrafista);

        // etiqueta = nombre del tipo (en el catálogo de prueba, igual a la clave)
        assertThat(r.tipos()).extracting(NovedadesSubprocesoResponse.TipoNovedadResponse::etiqueta)
                .contains("CANDIDATO_NO_ASISTIO", "CANDIDATO_NO_APTO", "OTRA")
                .doesNotContain("DIRECCION_ERRADA", "FUENTE_NO_DISPONIBLE", "DESACTIVADO");
        assertThat(r.puedeRegistrar()).isTrue();
        assertThat(r.puedeResolver()).isFalse();
    }

    @Test
    @DisplayName("El ejecutor solo ve las novedades que él registró; el gestor ve todas, la más reciente primero")
    void listar_ejecutorSoloVeLasPropias() {
        NovedadSubproceso propia = NovedadSubproceso.builder().id(1L).servicioSubproceso(subproceso)
                .tipo(tipo("OTRA")).descripcion("mía").registradoPor(poligrafista)
                .fechaRegistro(java.time.LocalDateTime.of(2026, 9, 1, 9, 0)).build();
        NovedadSubproceso ajena = NovedadSubproceso.builder().id(2L).servicioSubproceso(subproceso)
                .tipo(tipo("OTRA")).descripcion("del anterior").registradoPor(otroPoligrafista)
                .fechaRegistro(java.time.LocalDateTime.of(2026, 9, 2, 9, 0)).build();
        when(novedadRepository.findByServicioSubproceso_IdOrderByFechaRegistroAsc(500L)).thenReturn(List.of(propia, ajena));

        assertThat(service.listar(500L, poligrafista).novedades())
                .extracting(r -> r.id()).containsExactly(1L);
        assertThat(service.listar(500L, gestor).novedades())
                .extracting(r -> r.id()).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("Un gestor que no es el del cliente no puede resolver")
    void resolver_gestorAjeno_403() {
        Usuario otroGestor = usuario(21L, Rol.GESTOR);
        assertThatThrownBy(() -> service.resolver(500L, 900L, new ResolverNovedadRequest("ok"), otroGestor))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("El programador responsable resuelve y se le avisa a quien la reportó")
    void resolver_programador_avisaAlQueReporto() {
        NovedadSubproceso abierta = NovedadSubproceso.builder()
                .id(900L).servicioSubproceso(subproceso).tipo(tipo("CANDIDATO_NO_ASISTIO"))
                .descripcion("No llegó").registradoPor(poligrafista).build();
        when(novedadRepository.findById(900L)).thenReturn(Optional.of(abierta));

        var r = service.resolver(500L, 900L, new ResolverNovedadRequest("Reprogramado para el lunes 8am"), programador);

        assertThat(r.estado()).isEqualTo(EstadoNovedad.RESUELTA.name());
        assertThat(r.respuesta()).isEqualTo("Reprogramado para el lunes 8am");
        verify(notificacionService).crearParaUsuario(eq(10L), eq("NOVEDAD_RESUELTA"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(500L));
    }

    @Test
    @DisplayName("Una novedad de otro subproceso no se puede resolver por este")
    void resolver_novedadDeOtroSubproceso_404() {
        ServicioSubproceso otro = ServicioSubproceso.builder().id(501L).build();
        NovedadSubproceso ajena = NovedadSubproceso.builder().id(900L).servicioSubproceso(otro)
                .tipo(tipo("OTRA")).descripcion("x").registradoPor(poligrafista).build();
        when(novedadRepository.findById(anyLong())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.resolver(500L, 900L, new ResolverNovedadRequest("ok"), gestor))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Resolver con nueva fecha reprograma el subproceso y deja la reprogramación en el seguimiento")
    void resolver_conReprogramacion() {
        NovedadSubproceso abierta = NovedadSubproceso.builder()
                .id(900L).servicioSubproceso(subproceso).tipo(tipo("CANDIDATO_NO_ASISTIO"))
                .descripcion("No llegó").registradoPor(poligrafista).build();
        when(novedadRepository.findById(900L)).thenReturn(Optional.of(abierta));
        LocalDateTime anterior = LocalDateTime.of(2026, 9, 24, 8, 0);
        LocalDateTime nueva = LocalDateTime.of(2026, 9, 25, 10, 0);
        when(asignacionService.reprogramar(eq(subproceso), eq(nueva), eq(programador), anyString())).thenReturn(anterior);

        service.resolver(500L, 900L, new ResolverNovedadRequest("Se corre para mañana", nueva), programador);

        ArgumentCaptor<NovedadSeguimiento> captor = ArgumentCaptor.forClass(NovedadSeguimiento.class);
        verify(seguimientoRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoSeguimientoNovedad.REPROGRAMACION);
        assertThat(captor.getValue().getFechaAnterior()).isEqualTo(anterior);
        assertThat(captor.getValue().getFechaNueva()).isEqualTo(nueva);
        assertThat(abierta.getEstado()).isEqualTo(EstadoNovedad.RESUELTA);
    }

    @Test
    @DisplayName("Quien reportó agrega una observación y se avisa a gestor y programador")
    void seguimiento_delEjecutor_avisaCoordinadores() {
        NovedadSubproceso abierta = NovedadSubproceso.builder()
                .id(900L).servicioSubproceso(subproceso).tipo(tipo("CANDIDATO_LLEGO_TARDE"))
                .descripcion("Tarde").registradoPor(poligrafista).build();
        when(novedadRepository.findById(900L)).thenReturn(Optional.of(abierta));

        service.agregarSeguimiento(500L, 900L, new AgregarSeguimientoRequest("Llamó, llega en 20 min"), poligrafista);

        verify(seguimientoRepository).save(any(NovedadSeguimiento.class));
        verify(notificacionService).crearParaUsuario(eq(20L), eq("NOVEDAD_REGISTRADA"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(500L));
        verify(notificacionService).crearParaUsuario(eq(30L), eq("NOVEDAD_REGISTRADA"), anyString(), anyString(), eq("SERVICIO_SUBPROCESO"), eq(500L));
    }

    @Test
    @DisplayName("No se agregan observaciones a una novedad ya resuelta")
    void seguimiento_novedadResuelta_409() {
        NovedadSubproceso resuelta = NovedadSubproceso.builder()
                .id(900L).servicioSubproceso(subproceso).tipo(tipo("OTRA"))
                .descripcion("x").registradoPor(poligrafista).estado(EstadoNovedad.RESUELTA).build();
        when(novedadRepository.findById(900L)).thenReturn(Optional.of(resuelta));

        assertThatThrownBy(() -> service.agregarSeguimiento(500L, 900L, new AgregarSeguimientoRequest("más"), poligrafista))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Un poligrafista que no la reportó no puede agregar observaciones")
    void seguimiento_ajeno_403() {
        NovedadSubproceso abierta = NovedadSubproceso.builder()
                .id(900L).servicioSubproceso(subproceso).tipo(tipo("OTRA"))
                .descripcion("x").registradoPor(poligrafista).build();
        when(novedadRepository.findById(900L)).thenReturn(Optional.of(abierta));

        assertThatThrownBy(() -> service.agregarSeguimiento(500L, 900L, new AgregarSeguimientoRequest("más"), otroPoligrafista))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void registrarTipo(int id, String clave, CategoriaNovedad categoria, boolean activo, Rol... roles) {
        TipoNovedad t = TipoNovedad.builder().idTipoNovedad(id).clave(clave).nombre(clave).categoria(categoria)
                .activo(activo).roles(new java.util.HashSet<>(List.of(roles))).build();
        tipos.put(clave, t);
        lenient().when(tipoNovedadRepository.findById(id)).thenReturn(Optional.of(t));
    }

    private TipoNovedad tipo(String clave) {
        return tipos.get(clave);
    }

    private static Usuario usuario(Long id, Rol rol) {
        return Usuario.builder().idUsuario(id).rol(rol).nombre("U" + id).activo(true).build();
    }
}
