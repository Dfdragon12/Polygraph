package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.repository.CandidatoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import jakarta.persistence.EntityManager;
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

/** Las dos consultas del buscador global para ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR/
 *  PROGRAMADOR — el punto crítico a confirmar es que el alcance por usuario asignado
 *  (o programador responsable) de verdad excluye lo de otros empleados, y que el join "ad hoc"
 *  entre Candidato y Servicio (sin relación navegable directa entre ellos) funciona en JPQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Buscador global — alcance 'asignado a mí' (analista/poligrafista/visitador/programador)")
class BusquedaAsignadaATest {

    @Autowired private CandidatoRepository candidatoRepository;
    @Autowired private ServicioSubprocesoRepository servicioSubprocesoRepository;
    @Autowired private EntityManager em;

    private Usuario poligrafistaA;
    private Usuario poligrafistaB;
    private Usuario programador;
    private Candidato candidatoDeA;
    private Candidato candidatoDeB;

    @BeforeEach
    void setUp() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder()
                .codigo("PG").nombre("Poligrafía").activo(true).build());
        Proceso proceso = persistir(Proceso.builder()
                .nombreProceso("Poligrafía Pre Empleo").clasificacion(clasificacion)
                .aplicaPrecioCiudad(false).build());

        poligrafistaA = persistir(usuario("poligrafista.a@test.com", Rol.POLIGRAFISTA));
        poligrafistaB = persistir(usuario("poligrafista.b@test.com", Rol.POLIGRAFISTA));
        programador = persistir(usuario("programador@test.com", Rol.PROGRAMADOR));

        Cliente cliente = persistir(Cliente.builder()
                .tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Cliente de Prueba S.A.S.").emailPrincipal("cliente@test.com").build());

        candidatoDeA = persistir(Candidato.builder()
                .cedula("2000111222").tipoDocumento("CC").nombres("Carlos").apellidos("Perez").build());
        candidatoDeB = persistir(Candidato.builder()
                .cedula("2000333444").tipoDocumento("CC").nombres("Diana").apellidos("Lopez").build());

        Servicio servicioA = persistir(servicio(cliente, candidatoDeA, proceso, poligrafistaA));
        Servicio servicioB = persistir(servicio(cliente, candidatoDeB, proceso, poligrafistaA));

        // Subproceso 1: asignado directo a poligrafistaA.
        persistir(subproceso(servicioA, poligrafistaA, null));
        // Subproceso 2: candidato distinto, también asignado a poligrafistaA — para confirmar que
        // buscarAsignadoA de Candidato encuentra a los DOS candidatos de A, no solo a uno.
        persistir(subproceso(servicioB, poligrafistaA, null));
        // Subproceso 3: mismo candidato que el de A, pero asignado a poligrafistaB — NO debe
        // aparecer en los resultados de A.
        persistir(subproceso(servicioA, poligrafistaB, null));
        // Subproceso 4: sin usuarioAsignado, pero con programadorResponsable — debe aparecer para
        // el programador aunque nunca sea el "usuarioAsignado" de ningún subproceso.
        persistir(subproceso(servicioA, null, programador));

        em.flush();
    }

    @Test
    @DisplayName("ServicioSubproceso: solo trae subprocesos asignados a ESE usuario, no a otro")
    void servicioSubprocesoSoloDelUsuarioAsignado() {
        List<ServicioSubproceso> resultado = servicioSubprocesoRepository
                .buscarAsignadoA("%pre empleo%", poligrafistaA.getIdUsuario(), PageRequest.of(0, 10));

        assertThat(resultado).hasSize(2); // subprocesos 1 y 2 — no el 3 (es de B) ni el 4 (es de programador)
        assertThat(resultado).allMatch(sp -> sp.getUsuarioAsignado().getIdUsuario().equals(poligrafistaA.getIdUsuario()));
    }

    @Test
    @DisplayName("ServicioSubproceso: el programador ve lo suyo por programadorResponsable, aunque no sea usuarioAsignado")
    void servicioSubprocesoPorProgramadorResponsable() {
        List<ServicioSubproceso> resultado = servicioSubprocesoRepository
                .buscarAsignadoA("%pre empleo%", programador.getIdUsuario(), PageRequest.of(0, 10));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getProgramadorResponsable().getIdUsuario()).isEqualTo(programador.getIdUsuario());
    }

    @Test
    @DisplayName("Candidato: el join 'ad hoc' Candidato-Servicio funciona y trae solo los candidatos de ese usuario")
    void candidatoSoloDelUsuarioAsignado() {
        List<Candidato> resultado = candidatoRepository
                .buscarAsignadoA("%a%", poligrafistaA.getIdUsuario(), PageRequest.of(0, 10)); // "a" está en Carlos y en Perez/Lopez

        assertThat(resultado).extracting(Candidato::getIdCandidato)
                .containsExactlyInAnyOrder(candidatoDeA.getIdCandidato(), candidatoDeB.getIdCandidato());
    }

    @Test
    @DisplayName("Candidato: un usuario sin ninguna asignación no encuentra nada, aunque el candidato exista")
    void candidatoSinAsignacionNoEncuentraNada() {
        Usuario sinAsignaciones = persistir(usuario("sin.asignaciones@test.com", Rol.POLIGRAFISTA));
        em.flush();

        List<Candidato> resultado = candidatoRepository
                .buscarAsignadoA("%carlos%", sinAsignaciones.getIdUsuario(), PageRequest.of(0, 10));

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Buscador de 'Mis asignaciones': por nombre, cédula o número de servicio, solo lo del usuario")
    void buscadorMisAsignaciones() {
        var mias = ServicioSubprocesoSpecifications.conFiltros(null, null, poligrafistaA.getIdUsuario(), null, null);

        assertThat(servicioSubprocesoRepository.findAll(mias.and(ServicioSubprocesoSpecifications.buscar("diana"))))
                .extracting(sp -> sp.getServicio().getCandidato().getCedula()).containsExactly("2000333444");
        assertThat(servicioSubprocesoRepository.findAll(mias.and(ServicioSubprocesoSpecifications.buscar("carlos perez"))))
                .hasSize(1);
        assertThat(servicioSubprocesoRepository.findAll(mias.and(ServicioSubprocesoSpecifications.buscar("2000111"))))
                .hasSize(1);
        Integer idServicioB = candidatoDeB.getIdCandidato() == null ? null
                : servicioSubprocesoRepository.findAll(mias.and(ServicioSubprocesoSpecifications.buscar("Lopez")))
                        .get(0).getServicio().getIdServicio();
        assertThat(servicioSubprocesoRepository.findAll(mias.and(ServicioSubprocesoSpecifications.buscar("#" + idServicioB))))
                .extracting(sp -> sp.getServicio().getIdServicio()).containsExactly(idServicioB);
        assertThat(servicioSubprocesoRepository.findAll(mias.and(ServicioSubprocesoSpecifications.buscar("nadie"))))
                .isEmpty();
    }

    private Usuario usuario(String email, Rol rol) {
        return Usuario.builder().nombre(email).email(email).password("x")
                .rol(rol).activo(true).emailVerificado(true).build();
    }

    private Servicio servicio(Cliente cliente, Candidato candidato, Proceso proceso, Usuario solicitante) {
        return Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso)
                .usuarioSolicita(solicitante).fechaSolicitud(LocalDate.now()).estado(EstadoServicio.PENDIENTE).build();
    }

    private ServicioSubproceso subproceso(Servicio servicio, Usuario usuarioAsignado, Usuario programadorResponsable) {
        return ServicioSubproceso.builder().servicio(servicio)
                .tipoProgreso(tipoProgresoDePrueba())
                .usuarioAsignado(usuarioAsignado).programadorResponsable(programadorResponsable)
                .estado(EstadoSubproceso.PENDIENTE).build();
    }

    private com.polygraph.erp.modules.catalogo.entity.TipoProgreso tipoProgresoCache;

    private com.polygraph.erp.modules.catalogo.entity.TipoProgreso tipoProgresoDePrueba() {
        if (tipoProgresoCache == null) {
            tipoProgresoCache = persistir(com.polygraph.erp.modules.catalogo.entity.TipoProgreso.builder()
                    .nombreProgreso("Aplicación de la prueba")
                    .rolResponsable(Rol.POLIGRAFISTA).requiereDocumento(false).build());
        }
        return tipoProgresoCache;
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
