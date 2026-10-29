package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.dto.SemaforoResponse;
import com.polygraph.erp.modules.servicios.dto.ServicioSemaforoFila;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Filtros;
import com.polygraph.erp.modules.servicios.service.ConsultaSemaforo.Solicitante;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.service.TiempoServicioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El semáforo de estado contra una base real (H2): los seis servicios de prueba del cliente «QA Semáforo» de la especificación, más un
 * servicio de OTRO cliente para comprobar que cada perfil solo ve lo suyo. Verifica el JPQL, el alcance y los conteos de punta a punta.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Semáforo de estado — cliente QA")
class SemaforoEstadoServiceTest {

    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ServicioSubprocesoRepository subprocesoRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private SemaforoEstadoService service;

    private static final Long GESTOR_QA = 100L;
    private static final Long OTRO_GESTOR = 200L;

    private Cliente clienteQa;
    private Cliente otroCliente;
    private Usuario analista;
    private Usuario poligrafista;
    private Usuario programador;
    private final Map<Integer, Servicio> qa = new TreeMap<>();   // 40..45
    private Servicio ajeno;

    private TipoProgreso antecedentes, validacionDoc, visita, poligrafia, financiero, validacionLab, visitaVirtual;

    @BeforeEach
    void setUp() {
        service = new SemaforoEstadoService(servicioRepository, subprocesoRepository, mock(TiempoServicioService.class),
                mock(com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository.class), java.time.Clock.systemUTC());
        ReflectionTestUtils.setField(service, "em", em);

        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("SEG").nombre("Seguridad").activo(true).build());
        Proceso proceso = persistir(Proceso.builder().nombreProceso("Avanzado").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());

        antecedentes = tipo("Antecedentes", Rol.ANALISTA_INTERNO);
        validacionDoc = tipo("Validación documental", Rol.ANALISTA_INTERNO);
        visita = tipo("Visita", Rol.VISITADOR);
        poligrafia = tipo("Poligrafía", Rol.POLIGRAFISTA);
        financiero = tipo("Análisis financiero", Rol.ANALISTA_INTERNO);
        validacionLab = tipo("Validación laboral", Rol.ANALISTA_INTERNO);
        visitaVirtual = tipo("Visita virtual", Rol.VISITADOR);

        analista = usuario("analista@qa.test", Rol.ANALISTA_INTERNO);
        poligrafista = usuario("poli@qa.test", Rol.POLIGRAFISTA);
        programador = usuario("prog@qa.test", Rol.PROGRAMADOR);

        clienteQa = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("QA Semáforo").emailPrincipal("qa@test.com").idGestor(GESTOR_QA).build());
        otroCliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Otro cliente").emailPrincipal("otro@test.com").idGestor(OTRO_GESTOR).build());

        // #40 Avanzado: Antecedentes Publicado · Validación documental En ejecución · Visita Programando · Poligrafía Programando · Financiero Pendiente
        Servicio s40 = servicio(clienteQa, proceso, "1000000040", 40);
        sp(s40, antecedentes, EstadoSubproceso.PUBLICADO, analista, null);
        sp(s40, validacionDoc, EstadoSubproceso.EN_EJECUCION, analista, null);
        sp(s40, visita, EstadoSubproceso.PROGRAMANDO, null, programador);
        sp(s40, poligrafia, EstadoSubproceso.PROGRAMANDO, null, programador);
        sp(s40, financiero, EstadoSubproceso.PENDIENTE, null, null);
        // #41 Antecedentes: Publicado
        sp(servicio(clienteQa, proceso, "1000000041", 41), antecedentes, EstadoSubproceso.PUBLICADO, analista, null);
        // #42 Básico Virtual: Antecedentes Finalizado · Validación laboral En ejecución · Visita virtual Reprogramado
        Servicio s42 = servicio(clienteQa, proceso, "1000000042", 42);
        sp(s42, antecedentes, EstadoSubproceso.FINALIZADO, analista, null);
        sp(s42, validacionLab, EstadoSubproceso.EN_EJECUCION, analista, null);
        sp(s42, visitaVirtual, EstadoSubproceso.REPROGRAMADO, null, programador);
        // #43 Poligrafía Pre Empleo: Pendiente (esperando link) — vencida
        Servicio s43 = servicio(clienteQa, proceso, "1000000043", 43);
        s43.setEstadoTiempo(EstadoTiempo.VENCIDA);
        sp(s43, poligrafia, EstadoSubproceso.PENDIENTE, null, null);
        // #44 Poligrafía + Antecedentes: Antecedentes Publicado · Poligrafía Reprogramado
        Servicio s44 = servicio(clienteQa, proceso, "1000000044", 44);
        sp(s44, antecedentes, EstadoSubproceso.PUBLICADO, analista, null);
        sp(s44, poligrafia, EstadoSubproceso.REPROGRAMADO, poligrafista, programador);
        // #45 Antecedentes: Cancelado
        sp(servicio(clienteQa, proceso, "1000000045", 45), antecedentes, EstadoSubproceso.CANCELADO, null, null);

        // De otro cliente: no debe aparecer para el cliente QA ni para su gestor
        ajeno = servicio(otroCliente, proceso, "1000000099", 99);
        sp(ajeno, antecedentes, EstadoSubproceso.PENDIENTE, null, null);
        em.flush();
        em.clear();
    }

    private Solicitante cliente() { return new Solicitante(Rol.ADMIN_CLIENTE, 1L, clienteQa.getIdCliente()); }
    private Solicitante gestor() { return new Solicitante(Rol.GESTOR, GESTOR_QA, null); }
    private Solicitante admin() { return new Solicitante(Rol.ADMIN_POLYGRAPH, 1L, null); }

    private static List<Integer> servicios(SemaforoResponse r) { return r.estados().stream().map(SemaforoResponse.EstadoConteo::servicios).toList(); }
    private static List<Integer> subprocesos(SemaforoResponse r) { return r.estados().stream().map(SemaforoResponse.EstadoConteo::subprocesos).toList(); }

    // ───────────── contadores ─────────────

    @Test
    @DisplayName("El cliente QA ve 2 · 1 · 2 · 2 · 1 · 3 · 1 (servicios) y 2 · 2 · 2 · 2 · 1 · 3 · 1 (subprocesos), total 6")
    void contadoresDelCliente() {
        SemaforoResponse r = service.resumen(cliente(), Filtros.ninguno());
        assertThat(r.unidad()).isEqualTo("SERVICIOS");
        assertThat(r.total()).isEqualTo(6);
        assertThat(servicios(r)).containsExactly(2, 1, 2, 2, 1, 3, 1);
        assertThat(subprocesos(r)).containsExactly(2, 2, 2, 2, 1, 3, 1);
        assertThat(r.estados().stream().map(SemaforoResponse.EstadoConteo::codigo).toList())
                .containsExactly("PENDIENTE", "PROGRAMANDO", "EN_EJECUCION", "REPROGRAMADO", "FINALIZADO", "PUBLICADO", "CANCELADO");
        assertThat(r.estados().get(0).nombre()).isEqualTo("Pendiente");
        assertThat(r.estados().get(0).color()).isEqualTo("#F59E0B");
    }

    @Test
    @DisplayName("Cliente, gestor y administrador (filtrado a ese cliente) ven exactamente los mismos contadores")
    void clienteGestorYAdministradorCoinciden() {
        SemaforoResponse cliente = service.resumen(cliente(), Filtros.ninguno());
        SemaforoResponse gestor = service.resumen(gestor(), Filtros.ninguno());
        SemaforoResponse admin = service.resumen(admin(), new Filtros(null, clienteQa.getIdCliente(), null, null, null));
        assertThat(gestor).isEqualTo(cliente);
        assertThat(admin).isEqualTo(cliente);
    }

    @Test
    @DisplayName("Cada perfil solo ve lo suyo: el administrador suma el servicio ajeno, el gestor y el cliente de otra empresa no lo ven")
    void alcancePorPerfil() {
        assertThat(service.resumen(admin(), Filtros.ninguno()).total()).isEqualTo(7);
        assertThat(service.resumen(new Solicitante(Rol.GESTOR, OTRO_GESTOR, null), Filtros.ninguno()).total()).isEqualTo(1);
        assertThat(service.resumen(new Solicitante(Rol.ADMIN_CLIENTE, 2L, otroCliente.getIdCliente()), Filtros.ninguno()).total()).isEqualTo(1);
        assertThat(service.resumen(new Solicitante(Rol.GESTOR, 999L, null), Filtros.ninguno()).total()).isZero();
    }

    @Test
    @DisplayName("El analista cuenta SUS subprocesos (no servicios) y solo en En ejecución, Finalizado y Publicado")
    void contadoresDelAnalista() {
        SemaforoResponse r = service.resumen(new Solicitante(Rol.ANALISTA_INTERNO, analista.getIdUsuario(), null), Filtros.ninguno());
        assertThat(r.unidad()).isEqualTo("SUBPROCESOS");
        assertThat(r.estados().stream().map(SemaforoResponse.EstadoConteo::codigo).toList()).containsExactly("EN_EJECUCION", "FINALIZADO", "PUBLICADO");
        assertThat(subprocesos(r)).containsExactly(2, 1, 3);   // en ejecución: #40 y #42 · finalizado: #42 · publicado: #40, #41, #44
        assertThat(r.total()).isEqualTo(6);
    }

    @Test
    @DisplayName("El poligrafista cuenta su subproceso: la poligrafía reprogramada de la #44")
    void contadoresDelPoligrafista() {
        SemaforoResponse r = service.resumen(new Solicitante(Rol.POLIGRAFISTA, poligrafista.getIdUsuario(), null), Filtros.ninguno());
        assertThat(r.estados().stream().map(SemaforoResponse.EstadoConteo::codigo).toList()).containsExactly("EN_EJECUCION", "REPROGRAMADO", "FINALIZADO", "PUBLICADO");
        assertThat(subprocesos(r)).containsExactly(0, 1, 0, 0);
    }

    @Test
    @DisplayName("El programador cuenta lo que coordina, en Programando, En ejecución, Reprogramado y Finalizado")
    void contadoresDelProgramador() {
        SemaforoResponse r = service.resumen(new Solicitante(Rol.PROGRAMADOR, programador.getIdUsuario(), null), Filtros.ninguno());
        assertThat(r.estados().stream().map(SemaforoResponse.EstadoConteo::codigo).toList()).containsExactly("PROGRAMANDO", "EN_EJECUCION", "REPROGRAMADO", "FINALIZADO");
        assertThat(subprocesos(r)).containsExactly(2, 0, 2, 0);   // programando: visita y poligrafía de la #40 · reprogramado: visita virtual #42 y poligrafía #44
    }

    // ───────────── filtros combinables ─────────────

    @Test
    @DisplayName("Filtros: rol, tipo de subproceso y tiempo se combinan")
    void filtrosCombinados() {
        SemaforoResponse porRol = service.resumen(gestor(), new Filtros(null, null, null, Rol.POLIGRAFISTA, null));
        assertThat(porRol.total()).isEqualTo(3);                       // #40, #43 y #44 tienen poligrafía
        assertThat(servicios(porRol)).containsExactly(1, 1, 0, 1, 0, 0, 0);

        SemaforoResponse porTipo = service.resumen(gestor(), new Filtros(null, null, null, null, financiero.getIdTipoProgreso()));
        assertThat(porTipo.total()).isEqualTo(1);                      // solo la #40 tiene análisis financiero
        assertThat(servicios(porTipo)).containsExactly(1, 0, 0, 0, 0, 0, 0);

        SemaforoResponse vencidas = service.resumen(gestor(), new Filtros(EstadoTiempo.VENCIDA, null, null, null, null));
        assertThat(vencidas.total()).isEqualTo(1);                     // la #43
        SemaforoResponse vencidasDePoligrafia = service.resumen(gestor(), new Filtros(EstadoTiempo.VENCIDA, null, null, Rol.POLIGRAFISTA, poligrafia.getIdTipoProgreso()));
        assertThat(vencidasDePoligrafia.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("Filtro por responsable: solo lo que tiene asignado esa persona")
    void filtroPorResponsable() {
        SemaforoResponse r = service.resumen(admin(), new Filtros(null, null, analista.getIdUsuario(), null, null));
        assertThat(subprocesos(r)).containsExactly(0, 0, 2, 0, 1, 3, 0);
    }

    // ───────────── lista filtrada ─────────────

    @Test
    @DisplayName("Clic en Programando → solo la #40, con Visita y Poligrafía resaltadas")
    void filtroProgramando() {
        var pagina = service.servicios(cliente(), Filtros.ninguno(), EstadoSubproceso.PROGRAMANDO, PageRequest.of(0, 10));
        assertThat(pagina.getTotalElements()).isEqualTo(1);
        ServicioSemaforoFila f = pagina.getContent().get(0);
        assertThat(f.idServicio()).isEqualTo(qa.get(40).getIdServicio());
        assertThat(f.subprocesos()).hasSize(5);
        assertThat(f.subprocesos().stream().filter(ServicioSemaforoFila.SubprocesoEnFila::resaltado).map(ServicioSemaforoFila.SubprocesoEnFila::nombre).toList())
                .containsExactlyInAnyOrder("Visita", "Poligrafía");
        // el resumen sigue mostrando todos los estados del servicio, no solo el filtrado
        assertThat(f.resumen().stream().map(ServicioSemaforoFila.ResumenEstado::codigo).toList())
                .containsExactly("PENDIENTE", "PROGRAMANDO", "EN_EJECUCION", "PUBLICADO");
        assertThat(f.resumen().get(1).cantidad()).isEqualTo(2);
    }

    @Test
    @DisplayName("Clic en Publicado → #40 y #44 «Publicado parcial»; #41 «Estudio completo publicado»")
    void filtroPublicado() {
        var pagina = service.servicios(cliente(), Filtros.ninguno(), EstadoSubproceso.PUBLICADO, PageRequest.of(0, 10));
        Map<Integer, ServicioSemaforoFila> porId = new TreeMap<>();
        pagina.getContent().forEach(f -> porId.put(f.idServicio(), f));
        assertThat(porId.keySet()).containsExactlyInAnyOrder(qa.get(40).getIdServicio(), qa.get(41).getIdServicio(), qa.get(44).getIdServicio());
        assertThat(porId.get(qa.get(40).getIdServicio()).etiquetaPublicacion()).isEqualTo("Publicado parcial");
        assertThat(porId.get(qa.get(44).getIdServicio()).etiquetaPublicacion()).isEqualTo("Publicado parcial");
        assertThat(porId.get(qa.get(41).getIdServicio()).etiquetaPublicacion()).isEqualTo("Estudio completo publicado");
        // avance: publicados sobre no cancelados
        assertThat(porId.get(qa.get(40).getIdServicio()).publicados()).isEqualTo(1);
        assertThat(porId.get(qa.get(40).getIdServicio()).vigentes()).isEqualTo(5);
    }

    @Test
    @DisplayName("La #40 está en varios estados a la vez y la lista de cada uno la incluye")
    void unServicioEnVariosFiltros() {
        for (EstadoSubproceso e : List.of(EstadoSubproceso.PENDIENTE, EstadoSubproceso.PROGRAMANDO, EstadoSubproceso.EN_EJECUCION, EstadoSubproceso.PUBLICADO)) {
            var ids = service.servicios(cliente(), Filtros.ninguno(), e, PageRequest.of(0, 10)).getContent().stream().map(ServicioSemaforoFila::idServicio).toList();
            assertThat(ids).as(e.name()).contains(qa.get(40).getIdServicio());
        }
    }

    @Test
    @DisplayName("Sin estado la lista trae todos los servicios del alcance, paginados; el cliente nunca ve el de otra empresa")
    void listaSinEstado() {
        var pagina = service.servicios(cliente(), Filtros.ninguno(), null, PageRequest.of(0, 4));
        assertThat(pagina.getTotalElements()).isEqualTo(6);
        assertThat(pagina.getContent()).hasSize(4);
        var todos = service.servicios(cliente(), Filtros.ninguno(), null, PageRequest.of(0, 20)).getContent();
        assertThat(todos.stream().map(ServicioSemaforoFila::idServicio)).doesNotContain(ajeno.getIdServicio());
    }

    @Test
    @DisplayName("El analista pide un estado que no es de su perfil → lista vacía")
    void estadoFueraDelPerfil() {
        var r = service.servicios(new Solicitante(Rol.ANALISTA_INTERNO, analista.getIdUsuario(), null), Filtros.ninguno(),
                EstadoSubproceso.PROGRAMANDO, PageRequest.of(0, 10));
        assertThat(r.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("En la fila de quien ejecuta solo salen SUS subprocesos")
    void filaDelAnalistaSoloConLoSuyo() {
        var r = service.servicios(new Solicitante(Rol.ANALISTA_INTERNO, analista.getIdUsuario(), null), Filtros.ninguno(),
                EstadoSubproceso.EN_EJECUCION, PageRequest.of(0, 10));
        ServicioSemaforoFila f40 = r.getContent().stream().filter(f -> f.idServicio().equals(qa.get(40).getIdServicio())).findFirst().orElseThrow();
        assertThat(f40.subprocesos().stream().map(ServicioSemaforoFila.SubprocesoEnFila::nombre)).containsExactlyInAnyOrder("Antecedentes", "Validación documental");
    }

    // ───────────── pertenencia a un estado (SE-09: lo que usan las listas de cliente, gestor y administrador) ─────────────

    @Test
    @DisplayName("conSubprocesoEn: un servicio entra a la lista de cada estado donde tenga un subproceso, una sola vez")
    void pertenenciaPorSubproceso() {
        java.util.function.Function<EstadoSubproceso, java.util.List<Integer>> ids = e -> servicioRepository
                .findAll(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conSubprocesoEn(e)).stream()
                .map(Servicio::getIdServicio).sorted().toList();
        assertThat(ids.apply(EstadoSubproceso.PROGRAMANDO)).hasSize(1);          // solo la #40, aunque tenga dos subprocesos programando
        assertThat(ids.apply(EstadoSubproceso.PUBLICADO)).hasSize(3);            // #40, #41 y #44
        assertThat(ids.apply(EstadoSubproceso.CANCELADO)).hasSize(1);            // la #45
        assertThat(servicioRepository.findAll(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.conSubprocesoEn(null))).hasSize((int) servicioRepository.count());
    }

    @Test
    @DisplayName("estadoDeFiltro: acepta el código sin importar mayúsculas y rechaza uno desconocido con 400")
    void estadoDeFiltro() {
        assertThat(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.estadoDeFiltro(" en_ejecucion ")).isEqualTo(EstadoSubproceso.EN_EJECUCION);
        assertThat(com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.estadoDeFiltro(null)).isNull();
        org.junit.jupiter.api.Assertions.assertThrows(com.polygraph.erp.shared.exceptions.ApiException.class,
                () -> com.polygraph.erp.modules.servicios.repository.ServicioSpecifications.estadoDeFiltro("INVENTADO"));
    }

    // ───────────── reporte de productividad (SE-11): sale del registro de cambios, no del estado actual ─────────────

    @Test
    @DisplayName("Productividad: el estado del día sale del registro; sin registro todo es PENDIENTE, aunque hoy tenga otros estados guardados")
    void productividadDesdeElRegistro() {
        var registro = mock(com.polygraph.erp.modules.servicios.repository.RegistroCambioSubprocesoRepository.class);
        var usuarios = mock(com.polygraph.erp.modules.auth.repository.UsuarioRepository.class);
        var hoy = java.time.LocalDate.now(RegistroCambiosService.ZONA);
        Long idSub41 = subprocesoRepository.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(qa.get(41).getIdServicio()).get(0).getId();
        var evento = com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso.builder()
                .idServicio(qa.get(41).getIdServicio()).idServicioSubproceso(idSub41).estadoAnterior("FINALIZADO").estadoNuevo("PUBLICADO")
                .idResponsable(analista.getIdUsuario()).fechaHora(java.time.OffsetDateTime.now()).fechaBogota(hoy.atTime(10, 0)).build();
        org.mockito.Mockito.when(registro.findByIdServicioSubprocesoInAndFechaBogotaBeforeOrderByFechaBogotaAscIdAsc(org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(evento));
        org.mockito.Mockito.when(usuarios.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(java.util.List.of(analista));
        var productividad = new ProductividadService(em, registro, usuarios, java.time.Clock.systemUTC());

        var quien = mock(Usuario.class);
        org.mockito.Mockito.when(quien.getRol()).thenReturn(Rol.ADMIN_POLYGRAPH);
        var r = productividad.reporte(quien, new ProductividadService.Filtros(hoy, hoy, null, null, null, clienteQa.getIdCliente()));

        assertThat(r.corte()).isEqualTo("11:59 p. m.");
        assertThat(r.zona()).isEqualTo("America/Bogota");
        var fila = r.personas().stream().filter(p -> p.idPersona().equals(analista.getIdUsuario())).findFirst().orElseThrow();
        assertThat(fila.publicados()).isEqualTo(1);
        var gestor = r.gestores().get(0);
        assertThat(gestor.totalServicios()).isEqualTo(6);
        assertThat(gestor.serviciosPorEstado().stream().filter(e -> e.codigo().equals("PUBLICADO")).findFirst().orElseThrow().servicios()).isEqualTo(1);
        assertThat(gestor.estudiosCompletosPublicados()).isEqualTo(1);             // la #41 quedó completa hoy
        assertThat(gestor.serviciosPorEstado().stream().filter(e -> e.codigo().equals("PENDIENTE")).findFirst().orElseThrow().servicios()).isEqualTo(5);
        assertThat(ProductividadExcel.generar(r)).isNotEmpty();
    }

    @Test
    @DisplayName("Productividad: el rango no puede pasar de 93 días ni terminar antes de empezar")
    void productividadRangoInvalido() {
        var productividad = new ProductividadService(em, mock(com.polygraph.erp.modules.servicios.repository.RegistroCambioSubprocesoRepository.class),
                mock(com.polygraph.erp.modules.auth.repository.UsuarioRepository.class), java.time.Clock.systemUTC());
        var quien = mock(Usuario.class);
        org.mockito.Mockito.when(quien.getRol()).thenReturn(Rol.ADMIN_POLYGRAPH);
        var hoy = java.time.LocalDate.now();
        org.junit.jupiter.api.Assertions.assertThrows(com.polygraph.erp.shared.exceptions.ApiException.class,
                () -> productividad.reporte(quien, new ProductividadService.Filtros(hoy.minusDays(200), hoy, null, null, null, null)));
        org.junit.jupiter.api.Assertions.assertThrows(com.polygraph.erp.shared.exceptions.ApiException.class,
                () -> productividad.reporte(quien, new ProductividadService.Filtros(hoy, hoy.minusDays(1), null, null, null, null)));
    }

    // ───────────── alertas aparte ─────────────

    @Test
    @DisplayName("Alertas del gestor sobre el cliente QA: 4 sin asignar (financiero, visita, poligrafía de la #40 y la poligrafía de la #43) y 1 vencida (la #43)")
    void alertasDelGestor() {
        var a = service.alertas(gestor(), Filtros.ninguno());
        assertThat(a.sinAsignar()).isEqualTo(4);
        assertThat(a.vencidas()).isEqualTo(1);
        assertThat(a.paraHoy()).isZero();
        assertThat(a.citasDeHoy()).isZero();
        assertThat(a.porConfirmar()).isZero();
        assertThat(a.novedadesAbiertas()).isZero();
    }

    @Test
    @DisplayName("El cliente solo recibe las alertas de entrega (vencidas y para hoy); las demás no le aplican")
    void alertasDelCliente() {
        var a = service.alertas(cliente(), Filtros.ninguno());
        assertThat(a.vencidas()).isEqualTo(1);
        assertThat(a.paraHoy()).isZero();
        assertThat(a.sinAsignar()).isNull();
        assertThat(a.porConfirmar()).isNull();
        assertThat(a.citasDeHoy()).isNull();
        assertThat(a.novedadesAbiertas()).isNull();
    }

    @Test
    @DisplayName("El analista solo ve alertas de lo suyo: sin la de «sin asignar»")
    void alertasDelAnalista() {
        var a = service.alertas(new Solicitante(Rol.ANALISTA_INTERNO, analista.getIdUsuario(), null), Filtros.ninguno());
        assertThat(a.sinAsignar()).isNull();
        assertThat(a.novedadesAbiertas()).isNotNull();
        assertThat(a.vencidas()).isZero();            // la #43 (vencida) no es suya
    }

    // ───────────── armado de datos ─────────────

    private TipoProgreso tipo(String nombre, Rol rol) {
        return persistir(TipoProgreso.builder().nombreProgreso(nombre).rolResponsable(rol).requiereDocumento(false).build());
    }

    private Usuario usuario(String email, Rol rol) {
        return persistir(Usuario.builder().nombre(email).email(email).password("x").rol(rol).activo(true).emailVerificado(true).build());
    }

    private Servicio servicio(Cliente cliente, Proceso proceso, String cedula, int numero) {
        Candidato c = persistir(Candidato.builder().cedula(cedula).tipoDocumento("CC").nombres("Eval" + numero).apellidos("QA").build());
        Usuario solicita = usuario("solicita" + numero + "@qa.test", Rol.GESTOR);
        Servicio s = persistir(Servicio.builder().cliente(cliente).candidato(c).proceso(proceso).usuarioSolicita(solicita)
                .fechaSolicitud(LocalDate.now()).estado(EstadoServicio.EN_EJECUCION).build());
        if (cliente == clienteQa) qa.put(numero, s);
        return s;
    }

    private void sp(Servicio s, TipoProgreso tipo, EstadoSubproceso estado, Usuario responsable, Usuario programadorResponsable) {
        persistir(ServicioSubproceso.builder().servicio(s).tipoProgreso(tipo).estado(estado)
                .usuarioAsignado(responsable).programadorResponsable(programadorResponsable).build());
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
