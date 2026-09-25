package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Semáforo de Solicitudes (gestor): un servicio con subprocesos en distintos estados debe
 *  aparecer en TODOS los filtros que apliquen, no solo en el estado agregado de Servicio.estado
 *  (ver GestorService.ESTADOS_SUBPROCESO_POR_TAB). Se prueba contra un H2 real (@DataJpaTest,
 *  mismo patrón que ServicioRepositoryBusquedaTest) porque lo que hay que verificar es el JPQL
 *  con `exists` en sí, no solo que compile. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("ServicioRepository — filtro de semáforo por subproceso")
class ServicioRepositoryMultiEstadoTest {

    @Autowired private ServicioRepository servicioRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private final Long idGestor = 100L;
    private Cliente cliente;
    private TipoProgreso poligrafia;
    private TipoProgreso visita;
    private Usuario solicitante;

    private Servicio servicioMismoEstado;   // los 2 subprocesos en EN_EJECUCION (bucket)
    private Servicio servicioTresEstados;   // subproceso PENDIENTE + subproceso PUBLICADO, s.estado EN_EJECUCION
    private Servicio servicioSinSubprocesos; // sin filas en servicio_subprocesos

    @BeforeEach
    void setUp() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder()
                .codigo("SEG").nombre("Seguridad").activo(true).build());

        Proceso proceso = persistir(Proceso.builder()
                .nombreProceso("Estudio de seguridad").clasificacion(clasificacion)
                .aplicaPrecioCiudad(false).build());

        poligrafia = persistir(TipoProgreso.builder()
                .nombreProgreso("Poligrafía").rolResponsable(Rol.POLIGRAFISTA).requiereDocumento(false).build());
        visita = persistir(TipoProgreso.builder()
                .nombreProgreso("Visita domiciliaria").rolResponsable(Rol.VISITADOR).requiereDocumento(false).build());

        solicitante = persistir(Usuario.builder()
                .nombre("Sistema").email("sistema@polygraph.test").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());

        cliente = persistir(Cliente.builder()
                .tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Cliente de prueba S.A.S.").emailPrincipal("cliente@test.com")
                .idGestor(idGestor).build());

        // Caso 1: todos los subprocesos en el mismo estado (ambos ASIGNADO → bucket EN_EJECUCION).
        Candidato candidatoA = persistir(candidato("1000111222", "Ana"));
        servicioMismoEstado = persistir(servicioBase(candidatoA, proceso, EstadoServicio.EN_EJECUCION));
        persistir(subproceso(servicioMismoEstado, poligrafia, EstadoAsignacion.ASIGNADO));
        persistir(subproceso(servicioMismoEstado, visita, EstadoAsignacion.ASIGNADO));

        // Caso 2: subprocesos en estados distintos — poligrafía PENDIENTE, visita PUBLICADO.
        // Debe aparecer en PENDIENTE (por el subproceso), en EN_EJECUCION (por s.estado directo,
        // que es como quedaría agregado hoy) y en FINALIZADO (por el subproceso PUBLICADO).
        Candidato candidatoB = persistir(candidato("1000333444", "Beto"));
        servicioTresEstados = persistir(servicioBase(candidatoB, proceso, EstadoServicio.EN_EJECUCION));
        persistir(subproceso(servicioTresEstados, poligrafia, EstadoAsignacion.PENDIENTE));
        persistir(subproceso(servicioTresEstados, visita, EstadoAsignacion.PUBLICADO));

        // Caso 3: servicio recién creado, sin subprocesos generados todavía.
        Candidato candidatoC = persistir(candidato("1000555666", "Carla"));
        servicioSinSubprocesos = persistir(servicioBase(candidatoC, proceso, EstadoServicio.PENDIENTE));

        em.flush();
    }

    @Test
    @DisplayName("Subprocesos en el mismo estado → el servicio aparece en un solo filtro")
    void todosMismoEstadoAparaceEnUnFiltro() {
        assertThat(idsEn(EstadoServicio.EN_EJECUCION)).contains(servicioMismoEstado.getIdServicio());
        assertThat(idsEn(EstadoServicio.PENDIENTE)).doesNotContain(servicioMismoEstado.getIdServicio());
        assertThat(idsEn(EstadoServicio.FINALIZADO)).doesNotContain(servicioMismoEstado.getIdServicio());
    }

    @Test
    @DisplayName("Subprocesos en estados distintos → el servicio aparece en cada filtro que aplique")
    void subprocesosEnVariosEstadosAparecenEnCadaFiltro() {
        assertThat(idsEn(EstadoServicio.PENDIENTE)).contains(servicioTresEstados.getIdServicio());
        assertThat(idsEn(EstadoServicio.EN_EJECUCION)).contains(servicioTresEstados.getIdServicio());
        assertThat(idsEn(EstadoServicio.FINALIZADO)).contains(servicioTresEstados.getIdServicio());
    }

    @Test
    @DisplayName("Servicio sin subprocesos aparece solo en PENDIENTE (su estado por defecto)")
    void sinSubprocesosSoloEnPendiente() {
        assertThat(idsEn(EstadoServicio.PENDIENTE)).contains(servicioSinSubprocesos.getIdServicio());
        assertThat(idsEn(EstadoServicio.EN_EJECUCION)).doesNotContain(servicioSinSubprocesos.getIdServicio());
        assertThat(idsEn(EstadoServicio.FINALIZADO)).doesNotContain(servicioSinSubprocesos.getIdServicio());
    }

    @Test
    @DisplayName("Conteo y página coinciden y no hay filas duplicadas del mismo servicio")
    void conteoYPaginaSinDuplicados() {
        // EN_EJECUCION: servicioMismoEstado (por s.estado) + servicioTresEstados (por s.estado
        // Y por el subproceso PENDIENTE) — debe contar 2, no 2+1 por el `distinct`.
        long conteo = servicioRepository.countDistinctByCliente_IdGestorAndEstadoOSubprocesoEstadoIn(
                idGestor, EstadoServicio.EN_EJECUCION, List.of(EstadoAsignacion.ASIGNADO, EstadoAsignacion.COMPLETADO));
        Page<Servicio> pagina = servicioRepository.findByCliente_IdGestorAndEstadoOSubprocesoEstadoIn(
                idGestor, EstadoServicio.EN_EJECUCION, List.of(EstadoAsignacion.ASIGNADO, EstadoAsignacion.COMPLETADO),
                PageRequest.of(0, 10));

        assertThat(conteo).isEqualTo(2);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        List<Integer> ids = pagina.getContent().stream().map(Servicio::getIdServicio).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    private List<Integer> idsEn(EstadoServicio estado) {
        List<EstadoAsignacion> bucket = switch (estado) {
            case PENDIENTE -> List.of(EstadoAsignacion.PENDIENTE);
            case EN_EJECUCION -> List.of(EstadoAsignacion.ASIGNADO, EstadoAsignacion.COMPLETADO);
            case FINALIZADO -> List.of(EstadoAsignacion.PUBLICADO);
            default -> throw new IllegalArgumentException("Sin bucket de subproceso para " + estado);
        };
        return servicioRepository.findByCliente_IdGestorAndEstadoOSubprocesoEstadoIn(
                        idGestor, estado, bucket, PageRequest.of(0, 20))
                .getContent().stream().map(Servicio::getIdServicio).toList();
    }

    private Candidato candidato(String cedula, String nombre) {
        return Candidato.builder().cedula(cedula).tipoDocumento("CC").nombres(nombre).apellidos("Test").build();
    }

    private Servicio servicioBase(Candidato candidato, Proceso proceso, EstadoServicio estado) {
        return Servicio.builder()
                .cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante)
                .fechaSolicitud(LocalDate.now()).estado(estado).build();
    }

    private ServicioSubproceso subproceso(Servicio servicio, TipoProgreso tipoProgreso, EstadoAsignacion estado) {
        return ServicioSubproceso.builder()
                .servicio(servicio).tipoProgreso(tipoProgreso).estado(estado).build();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
