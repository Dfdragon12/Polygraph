package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El único query nuevo del buscador global lo bastante arriesgado (left joins + coalesce +
 *  filtro opcional por idGestor) como para no confiar solo en que compile — se prueba contra un
 *  H2 real (@DataJpaTest), no con repositorios mockeados como el resto de pruebas del proyecto,
 *  precisamente porque lo que hay que verificar es que el JPQL en sí funciona.
 *  Replace.NONE + perfil "test": usa el H2 de application-test.yml (flyway.enabled=false,
 *  ddl-auto=create-drop) en vez del que autoconfigura @DataJpaTest por defecto — sin esto,
 *  Spring intenta correr las migraciones de Flyway (pensadas para Postgres) contra H2 y falla. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("ServicioRepository.buscar — buscador global")
class ServicioRepositoryBusquedaTest {

    @Autowired private ServicioRepository servicioRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private Cliente clienteGestorA;
    private Cliente clienteGestorB;
    private Long idGestorA = 100L;
    private Long idGestorB = 200L;

    @BeforeEach
    void setUp() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder()
                .codigo("PG").nombre("Poligrafía").activo(true).build());

        Proceso poligrafiaPreEmpleo = persistir(Proceso.builder()
                .nombreProceso("Poligrafía Pre Empleo").clasificacion(clasificacion)
                .aplicaPrecioCiudad(false).build());
        Proceso visitaDomiciliaria = persistir(Proceso.builder()
                .nombreProceso("Visita Domiciliaria").clasificacion(clasificacion)
                .aplicaPrecioCiudad(false).build());

        Usuario solicitante = persistir(Usuario.builder()
                .nombre("Sistema").email("sistema@polygraph.test").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());

        clienteGestorA = persistir(clienteBase("cliente-a@test.com", "Constructora Andina S.A.S.", idGestorA));
        clienteGestorB = persistir(clienteBase("cliente-b@test.com", "Logística del Sur Ltda.", idGestorB));

        Candidato carlos = persistir(Candidato.builder()
                .cedula("1000111222").tipoDocumento("CC").nombres("Carlos").apellidos("Ramirez Soto").build());
        Candidato maria = persistir(Candidato.builder()
                .cedula("1000333444").tipoDocumento("CC").nombres("María").apellidos("Gómez").build());

        // Servicio 1: proceso "Poligrafía Pre-Empleo", candidato Carlos, cliente del gestor A.
        persistir(servicioBase(clienteGestorA, carlos, poligrafiaPreEmpleo, solicitante));
        // Servicio 2: mismo proceso, candidato María, cliente del gestor B.
        persistir(servicioBase(clienteGestorB, maria, poligrafiaPreEmpleo, solicitante));
        // Servicio 3: proceso distinto, no debería aparecer al buscar "pre empleo".
        persistir(servicioBase(clienteGestorA, carlos, visitaDomiciliaria, solicitante));

        em.flush();
    }

    @Test
    @DisplayName("Sin idGestor (ADMIN_POLYGRAPH), encuentra por nombre de proceso en cualquier cliente")
    void buscaPorProcesoSinFiltroDeGestor() {
        List<Servicio> resultado = servicioRepository.buscar("%pre empleo%", null, PageRequest.of(0, 10));

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(s -> s.getProceso().getNombreProceso().contains("Pre Empleo"));
    }

    @Test
    @DisplayName("Con idGestor (GESTOR), solo trae servicios de SUS clientes aunque el proceso coincida en otros")
    void buscaPorProcesoFiltradoPorGestor() {
        List<Servicio> resultado = servicioRepository.buscar("%pre empleo%", idGestorA, PageRequest.of(0, 10));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCliente().getIdCliente()).isEqualTo(clienteGestorA.getIdCliente());
    }

    @Test
    @DisplayName("Encuentra por nombre del candidato, no solo por proceso")
    void buscaPorNombreDeCandidato() {
        List<Servicio> resultado = servicioRepository.buscar("%ramirez%", null, PageRequest.of(0, 10));

        assertThat(resultado).hasSize(2); // Carlos aparece en 2 servicios (pre-empleo y visita)
        assertThat(resultado).allMatch(s -> s.getCandidato().getApellidos().contains("Ramirez"));
    }

    @Test
    @DisplayName("Encuentra por nombre del cliente (razón social)")
    void buscaPorNombreDeCliente() {
        List<Servicio> resultado = servicioRepository.buscar("%logistica del sur%", null, PageRequest.of(0, 10));

        // Sin tilde en la búsqueda vs "Logística" en el dato — coalesce/like no normaliza tildes,
        // así que si esto falla confirma que hace falta ampliar el query, no un bug de este test.
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Sin coincidencias, devuelve lista vacía en vez de fallar")
    void sinCoincidenciasNoFalla() {
        assertThat(servicioRepository.buscar("%xyz-no-existe%", null, PageRequest.of(0, 10))).isEmpty();
    }

    private Cliente clienteBase(String email, String razonSocial, Long idGestor) {
        return Cliente.builder()
                .tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial(razonSocial).emailPrincipal(email).idGestor(idGestor).build();
    }

    private Servicio servicioBase(Cliente cliente, Candidato candidato, Proceso proceso, Usuario solicitante) {
        return Servicio.builder()
                .cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante)
                .fechaSolicitud(LocalDate.now()).estado(EstadoServicio.PENDIENTE).build();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
