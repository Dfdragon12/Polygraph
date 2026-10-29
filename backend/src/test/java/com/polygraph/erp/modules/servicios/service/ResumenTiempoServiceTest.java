package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.dashboard.dto.DashboardAdminResponse;
import com.polygraph.erp.modules.dashboard.dto.ServiciosPorEstadoResponse;
import com.polygraph.erp.modules.dashboard.service.DashboardAdminService;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.service.ResumenTiempoService.Filtro;
import com.polygraph.erp.modules.servicios.service.ResumenTiempoService.Resumen;
import com.polygraph.erp.modules.tiemporeal.CanalCambios;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El resumen único de tiempos: conteos, matriz etapa × estado de tiempo, cumplimiento del periodo, las más atrasadas, la autorización por rol
 *  y que el Resumen y el Semáforo del administrador devuelven exactamente los mismos totales. Datos inventados. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ResumenTiempoService.class, DashboardAdminService.class, CanalCambios.class, com.polygraph.erp.shared.service.DiasHabilesService.class,
        com.polygraph.erp.shared.service.TiempoServicioService.class, com.polygraph.erp.shared.service.CalendarioHabilService.class,
        com.polygraph.erp.shared.service.FestivoService.class, com.polygraph.erp.shared.service.ParametrosTiempoService.class,
        com.polygraph.erp.config.RelojConfig.class})
@DisplayName("ResumenTiempoService — el resumen único de los tableros")
class ResumenTiempoServiceTest {

    @Autowired private ResumenTiempoService service;
    @Autowired private DashboardAdminService dashboardAdmin;
    @Autowired private EntityManager em;

    private Usuario admin, gestor1, gestor2, analista, usuarioClienteA, usuarioClienteB;
    private Cliente clienteA, clienteB;
    private Proceso proceso;
    private Usuario solicitante;

    @BeforeEach
    void datos() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("SEG").nombre("Seguridad").activo(true).build());
        proceso = persistir(Proceso.builder().nombreProceso("AVANZADO").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        admin = usuario("admin.t@test.com", Rol.ADMIN_POLYGRAPH, null);
        gestor1 = usuario("gestor1.t@test.com", Rol.GESTOR, null);
        gestor2 = usuario("gestor2.t@test.com", Rol.GESTOR, null);
        analista = usuario("analista.t@test.com", Rol.ANALISTA_INTERNO, null);
        solicitante = usuario("sol.t@test.com", Rol.GESTOR, null);
        clienteA = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA).razonSocial("Cliente A S.A.S.")
                .emailPrincipal("a.t@test.com").idGestor(gestor1.getIdUsuario()).build());
        clienteB = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA).razonSocial("Cliente B S.A.S.")
                .emailPrincipal("b.t@test.com").idGestor(gestor2.getIdUsuario()).build());
        usuarioClienteA = usuario("uca.t@test.com", Rol.ADMIN_CLIENTE, clienteA.getIdCliente());
        usuarioClienteB = usuario("ucb.t@test.com", Rol.ANALISTA_CLIENTE, clienteB.getIdCliente());

        LocalDate hoy = LocalDate.now();
        // Cliente A: 2 vencidas (9 y 3 días de retraso), 1 por vencer, 1 a tiempo, 1 en pausa, 1 finalizada vencida, 1 cancelada,
        //            publicadas: 2 a tiempo y 1 tarde (entregadas hace 5 días)
        servicio(clienteA, EstadoServicio.EN_EJECUCION, EstadoTiempo.VENCIDA, 9, hoy.minusDays(30));
        servicio(clienteA, EstadoServicio.PROGRAMANDO, EstadoTiempo.VENCIDA, 3, hoy.minusDays(10));
        servicio(clienteA, EstadoServicio.EN_EJECUCION, EstadoTiempo.POR_VENCER, 0, hoy.minusDays(5));
        servicio(clienteA, EstadoServicio.REPROGRAMADO, EstadoTiempo.A_TIEMPO, 0, hoy.minusDays(2));
        servicio(clienteA, EstadoServicio.PENDIENTE, EstadoTiempo.EN_PAUSA, 0, hoy.minusDays(1));
        servicio(clienteA, EstadoServicio.FINALIZADO, EstadoTiempo.VENCIDA, 1, hoy.minusDays(20));
        servicio(clienteA, EstadoServicio.CANCELADO, null, 0, hoy.minusDays(20));
        publicada(clienteA, Cumplimiento.A_TIEMPO, hoy.minusDays(5), hoy.minusDays(40));
        publicada(clienteA, Cumplimiento.A_TIEMPO, hoy.minusDays(5), hoy.minusDays(41));
        publicada(clienteA, Cumplimiento.TARDE, hoy.minusDays(5), hoy.minusDays(42));
        // Cliente B: 1 vencida (20 días de retraso), 1 a tiempo, 1 publicada a tiempo hace 60 días
        servicio(clienteB, EstadoServicio.EN_EJECUCION, EstadoTiempo.VENCIDA, 20, hoy.minusDays(50));
        servicio(clienteB, EstadoServicio.PROGRAMANDO, EstadoTiempo.A_TIEMPO, 0, hoy.minusDays(1));
        publicada(clienteB, Cumplimiento.A_TIEMPO, hoy.minusDays(60), hoy.minusDays(90));
        em.flush();
        em.clear();
    }

    // ───────────────────────── conteos y matriz ─────────────────────────

    @Test
    @DisplayName("Administrador: conteos por estado de tiempo y matriz etapa × estado de tiempo de todo el sistema")
    void administradorVeTodo() {
        Resumen r = service.resumirPara(admin.getEmail(), null, null, null, null, null);
        assertThat(r.total()).isEqualTo(13);
        assertThat(r.porEstadoTiempo()).containsEntry("VENCIDA", 4L).containsEntry("POR_VENCER", 1L).containsEntry("A_TIEMPO", 2L)
                .containsEntry("EN_PAUSA", 1L).containsEntry("ENTREGADA_A_TIEMPO", 3L).containsEntry("ENTREGADA_TARDE", 1L);
        assertThat(r.sinEstadoTiempo()).isEqualTo(1);   // la cancelada
        // en curso = pendiente + programando + en ejecución + reprogramado (sin finalizado, publicado ni cancelado)
        assertThat(r.enCurso()).isEqualTo(7);

        var enEjecucion = r.matriz().stream().filter(f -> f.etapa().equals("EN_EJECUCION")).findFirst().orElseThrow();
        assertThat(enEjecucion.porEstadoTiempo()).containsEntry("VENCIDA", 2L).containsEntry("POR_VENCER", 1L).containsEntry("A_TIEMPO", 0L);
        assertThat(enEjecucion.total()).isEqualTo(3);
        var cancelada = r.matriz().stream().filter(f -> f.etapa().equals("CANCELADO")).findFirst().orElseThrow();
        assertThat(cancelada.sinEstadoTiempo()).isEqualTo(1);
        assertThat(r.matriz()).hasSize(EstadoServicio.values().length);
        // las filas de la matriz suman el total, y la matriz suma lo mismo que los conteos por estado de tiempo
        assertThat(r.matriz().stream().mapToLong(ResumenTiempoService.FilaMatriz::total).sum()).isEqualTo(r.total());
        assertThat(r.porEtapa().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(r.total());
    }

    @Test
    @DisplayName("Filtros de cliente y de gestor (solo el administrador puede pedirlos libremente)")
    void filtros() {
        assertThat(service.resumirPara(admin.getEmail(), clienteB.getIdCliente(), null, null, null, null).total()).isEqualTo(3);
        assertThat(service.resumirPara(admin.getEmail(), null, gestor1.getIdUsuario(), null, null, null).total()).isEqualTo(10);
        assertThat(service.resumirPara(admin.getEmail(), clienteA.getIdCliente(), gestor2.getIdUsuario(), null, null, null).total()).isZero();
    }

    @Test
    @DisplayName("Rango de fechas: filtra por la fecha de la solicitud (y el cumplimiento, por la de entrega real)")
    void rangoDeFechas() {
        LocalDate hoy = LocalDate.now();
        // las solicitudes del cliente A son de hace 1 a 42 días; solo las de los últimos 12 días
        Resumen reciente = service.resumirPara(admin.getEmail(), clienteA.getIdCliente(), null, hoy.minusDays(12), hoy, null);
        assertThat(reciente.total()).isEqualTo(4);   // fechas de solicitud: -10, -5, -2, -1
        // las 3 publicadas de A se entregaron hace 5 días
        assertThat(service.resumirPara(admin.getEmail(), clienteA.getIdCliente(), null, hoy.minusDays(6), hoy.minusDays(4), null).cumplimiento().publicadas()).isEqualTo(3);
        assertThat(service.resumirPara(admin.getEmail(), clienteA.getIdCliente(), null, hoy.minusDays(3), hoy, null).cumplimiento().publicadas()).isZero();
        assertThatThrownBy(() -> service.resumirPara(admin.getEmail(), null, null, hoy, hoy.minusDays(1), null))
                .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ───────────────────────── cumplimiento y más atrasadas ─────────────────────────

    @Test
    @DisplayName("Cumplimiento del periodo: publicadas a tiempo / publicadas")
    void cumplimiento() {
        var c = service.resumirPara(admin.getEmail(), clienteA.getIdCliente(), null, null, null, null).cumplimiento();
        assertThat(c.publicadas()).isEqualTo(3);
        assertThat(c.aTiempo()).isEqualTo(2);
        assertThat(c.tarde()).isEqualTo(1);
        assertThat(c.porcentajeATiempo()).isEqualTo(66.7);
        assertThat(service.resumirPara(admin.getEmail(), clienteB.getIdCliente(), null, null, null, null).cumplimiento().porcentajeATiempo()).isEqualTo(100.0);
        assertThat(service.resumirPara(gestor1.getEmail(), null, null, LocalDate.now().minusDays(3), LocalDate.now(), null).cumplimiento().porcentajeATiempo()).isNull();
    }

    @Test
    @DisplayName("Las más atrasadas: las vencidas con más días hábiles de retraso primero, limitadas por top")
    void masAtrasadas() {
        Resumen r = service.resumirPara(admin.getEmail(), null, null, null, null, 3);
        assertThat(r.masAtrasadas()).extracting(ResumenTiempoService.Atrasada::diasRetrasoHabiles).containsExactly(20, 9, 3);
        assertThat(r.masAtrasadas().get(0).cliente()).isEqualTo("Cliente B S.A.S.");
        assertThat(r.masAtrasadas().get(0).etapa()).isEqualTo("EN_EJECUCION");
        assertThat(service.resumirPara(admin.getEmail(), null, null, null, null, 1).masAtrasadas()).hasSize(1);
        assertThat(service.resumirPara(admin.getEmail(), null, null, null, null, 500).masAtrasadas()).hasSize(4);   // tope de 50, solo hay 4
    }

    // ───────────────────────── autorización ─────────────────────────

    @Test
    @DisplayName("Un cliente solo ve sus propias solicitudes, aunque no mande filtros")
    void clienteSoloVeLoSuyo() {
        Resumen a = service.resumirPara(usuarioClienteA.getEmail(), null, null, null, null, null);
        assertThat(a.total()).isEqualTo(10);
        assertThat(a.filtro().idCliente()).isEqualTo(clienteA.getIdCliente());
        assertThat(a.masAtrasadas()).allSatisfy(x -> assertThat(x.idCliente()).isEqualTo(clienteA.getIdCliente()));
        Resumen b = service.resumirPara(usuarioClienteB.getEmail(), clienteB.getIdCliente(), null, null, null, null);
        assertThat(b.total()).isEqualTo(3);
        assertThat(b.masAtrasadas()).extracting(ResumenTiempoService.Atrasada::cliente).containsOnly("Cliente B S.A.S.");
    }

    @Test
    @DisplayName("Un cliente que pide otro cliente, o filtra por gestor, recibe 403")
    void clienteNoPuedeVerAjeno() {
        assertThatThrownBy(() -> service.resumirPara(usuarioClienteA.getEmail(), clienteB.getIdCliente(), null, null, null, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.resumirPara(usuarioClienteA.getEmail(), null, gestor2.getIdUsuario(), null, null, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Un gestor solo ve los clientes que gestiona")
    void gestorSoloVeLosSuyos() {
        assertThat(service.resumirPara(gestor1.getEmail(), null, null, null, null, null).total()).isEqualTo(10);
        assertThat(service.resumirPara(gestor2.getEmail(), null, null, null, null, null).total()).isEqualTo(3);
        assertThat(service.resumirPara(gestor1.getEmail(), clienteA.getIdCliente(), gestor1.getIdUsuario(), null, null, null).total()).isEqualTo(10);
        assertThatThrownBy(() -> service.resumirPara(gestor1.getEmail(), clienteB.getIdCliente(), null, null, null, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.resumirPara(gestor1.getEmail(), null, gestor2.getIdUsuario(), null, null, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Los demás roles internos no ven el resumen")
    void otrosRoles() {
        assertThatThrownBy(() -> service.resumirPara(analista.getEmail(), null, null, null, null, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.resumirPara("nadie@test.com", null, null, null, null, null)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ───────────────────────── la lista detrás de cada número ─────────────────────────

    @Test
    @DisplayName("La lista de una celda de la matriz: etapa × estado de tiempo, de la más atrasada a la menos, con cliente y gestor")
    void listaDeUnaCelda() {
        var pagina = service.listarPara(admin.getEmail(), null, null, null, null, EstadoServicio.EN_EJECUCION, java.util.List.of(EstadoTiempo.VENCIDA),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(ResumenTiempoService.FilaSolicitud::cliente).containsExactly("Cliente B S.A.S.", "Cliente A S.A.S.");
        assertThat(pagina.getContent().get(0).tiempo().diasRetrasoHabiles()).isEqualTo(20);
        assertThat(pagina.getContent().get(0).tiempo().etiqueta()).isEqualTo("Vencida · 20 días hábiles");
        assertThat(pagina.getContent().get(0).gestor()).isEqualTo("Usuario GESTOR");
    }

    @Test
    @DisplayName("La lista admite varios estados de tiempo (Entregadas = a tiempo + tarde), pagina y respeta la autorización")
    void listaVariosEstadosYAutorizacion() {
        var entregadas = service.listarPara(admin.getEmail(), null, null, null, null, null,
                java.util.List.of(EstadoTiempo.ENTREGADA_A_TIEMPO, EstadoTiempo.ENTREGADA_TARDE), org.springframework.data.domain.PageRequest.of(0, 2));
        assertThat(entregadas.getTotalElements()).isEqualTo(4);
        assertThat(entregadas.getContent()).hasSize(2);
        assertThat(entregadas.getTotalPages()).isEqualTo(2);

        // el cliente A solo ve lo suyo aunque la celda tenga solicitudes de otros
        var delCliente = service.listarPara(usuarioClienteA.getEmail(), null, null, null, null, EstadoServicio.EN_EJECUCION,
                java.util.List.of(EstadoTiempo.VENCIDA), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(delCliente.getContent()).extracting(ResumenTiempoService.FilaSolicitud::idCliente).containsOnly(clienteA.getIdCliente());
        assertThatThrownBy(() -> service.listarPara(usuarioClienteA.getEmail(), clienteB.getIdCliente(), null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.listarPara(analista.getEmail(), null, null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10))).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Las más atrasadas traen el nombre del gestor del cliente")
    void atrasadasConGestor() {
        assertThat(service.resumirPara(admin.getEmail(), null, null, null, null, 1).masAtrasadas().get(0).gestor()).isEqualTo("Usuario GESTOR");
    }

    // ───────────────────────── el Resumen y el Semáforo dan lo mismo ─────────────────────────

    @Test
    @DisplayName("El Resumen y el Semáforo del administrador devuelven exactamente los mismos totales que el resumen único")
    void resumenYSemaforoCoinciden() {
        Resumen unico = service.resumir(Filtro.TODO, 0);
        DashboardAdminResponse tablero = dashboardAdmin.obtenerDashboard();
        ServiciosPorEstadoResponse semaforo = dashboardAdmin.serviciosPorEstado(null);

        assertThat(tablero.servicios().total()).isEqualTo(unico.total()).isEqualTo(semaforo.totalServicios());
        assertThat(tablero.servicios().enCurso()).isEqualTo(unico.enCurso());
        assertThat(tablero.servicios().entregasVencidas()).isEqualTo(unico.vencidas()).isEqualTo(semaforo.vencidas()).isEqualTo(4);
        assertThat(tablero.servicios().entregasPorVencer()).isEqualTo(unico.porVencer()).isEqualTo(1);
        assertThat(tablero.servicios().porEstado()).isEqualTo(unico.porEtapa());
        // el Semáforo, etapa por etapa
        semaforo.estados().forEach(e -> assertThat(e.total()).as(e.estado()).isEqualTo(unico.porEtapa().get(e.estado())));
        // "en curso" del Semáforo (pendiente + programando + en ejecución + reprogramado) = el del Resumen
        long enCursoSemaforo = semaforo.estados().stream().filter(e -> java.util.List.of("PENDIENTE", "PROGRAMANDO", "EN_EJECUCION", "REPROGRAMADO").contains(e.estado()))
                .mapToLong(ServiciosPorEstadoResponse.EstadoDto::total).sum();
        assertThat(enCursoSemaforo).isEqualTo(tablero.servicios().enCurso());
    }

    @Test
    @DisplayName("Con el filtro de cliente del Semáforo, sus cifras son las del resumen único de ese cliente")
    void semaforoPorCliente() {
        ServiciosPorEstadoResponse semaforo = dashboardAdmin.serviciosPorEstado(clienteA.getIdCliente());
        Resumen unico = service.resumir(new Filtro(clienteA.getIdCliente(), null, null, null), 0);
        assertThat(semaforo.totalServicios()).isEqualTo(unico.total()).isEqualTo(10);
        assertThat(semaforo.vencidas()).isEqualTo(unico.vencidas()).isEqualTo(3);
        semaforo.estados().forEach(e -> assertThat(e.total()).as(e.estado()).isEqualTo(unico.porEtapa().get(e.estado())));
    }

    @Test
    @DisplayName("El resumen del gestor y el del administrador filtrado por ese gestor son el mismo")
    void gestorYAdministradorCoinciden() {
        Resumen delGestor = service.resumirPara(gestor1.getEmail(), null, null, null, null, null);
        Resumen delAdmin = service.resumirPara(admin.getEmail(), null, gestor1.getIdUsuario(), null, null, null);
        assertThat(delGestor.total()).isEqualTo(delAdmin.total());
        assertThat(delGestor.porEstadoTiempo()).isEqualTo(delAdmin.porEstadoTiempo());
        assertThat(delGestor.porEtapa()).isEqualTo(delAdmin.porEtapa());
        assertThat(delGestor.cumplimiento()).isEqualTo(delAdmin.cumplimiento());
    }

    // ───────────────────────── apoyo ─────────────────────────

    private Usuario usuario(String email, Rol rol, Integer idCliente) {
        return persistir(Usuario.builder().nombre("Usuario " + rol).email(email).password("x").rol(rol).activo(true).emailVerificado(true)
                .idCliente(idCliente).build());
    }

    private void servicio(Cliente cliente, EstadoServicio etapa, EstadoTiempo tiempo, int retraso, LocalDate solicitada) {
        Candidato candidato = persistir(Candidato.builder().cedula("10" + System.nanoTime() % 100000000L).tipoDocumento("CC").nombres("Luis")
                .apellidos("Prueba").build());
        persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante).fechaSolicitud(solicitada)
                .estado(etapa).estadoTiempo(tiempo).diasRetrasoHabiles(tiempo == null ? null : retraso).diasRestantesHabiles(0)
                .fechaEntregaEstimada(solicitada.plusDays(5)).fechaCompromisoAjustada(solicitada.plusDays(5)).build());
    }

    private void publicada(Cliente cliente, Cumplimiento cumplimiento, LocalDate entregaReal, LocalDate solicitada) {
        Candidato candidato = persistir(Candidato.builder().cedula("20" + System.nanoTime() % 100000000L).tipoDocumento("CC").nombres("Ana")
                .apellidos("Entrega").build());
        persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante).fechaSolicitud(solicitada)
                .estado(EstadoServicio.PUBLICADO).cumplimiento(cumplimiento).fechaEntregaReal(entregaReal)
                .estadoTiempo(cumplimiento == Cumplimiento.TARDE ? EstadoTiempo.ENTREGADA_TARDE : EstadoTiempo.ENTREGADA_A_TIEMPO)
                .diasRetrasoHabiles(cumplimiento == Cumplimiento.TARDE ? 2 : 0).fechaEntregaEstimada(solicitada.plusDays(5))
                .fechaCompromisoAjustada(solicitada.plusDays(5)).build());
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
