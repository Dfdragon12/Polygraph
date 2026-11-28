package com.polygraph.erp.modules.usuarios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.service.ReasignacionGestorService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.usuarios.dto.ActualizarUsuarioInternoRequest;
import com.polygraph.erp.modules.usuarios.dto.CiudadAsignadaRequest;
import com.polygraph.erp.modules.usuarios.entity.EmpleadoCiudad;
import com.polygraph.erp.modules.usuarios.entity.UsuariosInternos;
import com.polygraph.erp.modules.usuarios.repository.UsuariosIntenosRepository;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Editar un poligrafista sin cambiarle la ciudad y la sala respondía 409 por la restricción
 * {@code uq_empleado_ciudad_sala}: {@code actualizar} vaciaba la lista de ciudades y la volvía a llenar,
 * y Hibernate inserta las filas nuevas ANTES de borrar las huérfanas, así que la fila nueva chocaba con
 * la vieja que aún no se había ido. Ahora se concilia por (ciudad, sala): lo que ya está no se toca.
 * El flush explícito de cada prueba es el que dispara la restricción (en el servicio el flush ocurre al
 * hacer commit).
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(UsuariosInternosService.class)
@DisplayName("Editar un usuario interno: sus ciudades y salas se concilian, no se borran y reinsertan")
class UsuariosInternosCiudadesTest {

    @Autowired private UsuariosInternosService servicio;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuariosIntenosRepository empleadoRepository;
    @Autowired private CiudadRepository ciudadRepository;
    @Autowired private EntityManager em;

    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private NotificacionService notificacionService;
    @MockBean private ReasignacionGestorService reasignacionGestorService;

    private Ciudad bogota;
    private Ciudad cali;
    private Ciudad medellin;

    @BeforeEach
    void setUp() {
        bogota = ciudad("Bogotá");
        cali = ciudad("Cali");
        medellin = ciudad("Medellín");
    }

    private Ciudad ciudad(String nombre) {
        return ciudadRepository.save(Ciudad.builder().nombreCiudad(nombre).departamento("X")
                .nivelCiudad(NivelCiudad.PRINCIPAL).pendienteConfirmacion(false).build());
    }

    /** Un empleado del rol dado con las ciudades indicadas {@code {ciudad, sala}} (sala 0 = sin sala). */
    private Usuario empleado(Rol rol, Object[]... coberturas) {
        UsuariosInternos emp = UsuariosInternos.builder().tipoEmpleado(rol.name()).nombre("Ana").apellido("Ruiz")
                .email("ana@polygraph.com").activo(true).fechaIngreso(LocalDate.now()).build();
        for (Object[] c : coberturas) {
            int sala = (int) c[1];
            emp.getCiudadesAsignadas().add(EmpleadoCiudad.builder().empleado(emp).ciudad((Ciudad) c[0])
                    .numeroSala(sala == 0 ? null : sala).build());
        }
        empleadoRepository.save(emp);
        Usuario u = usuarioRepository.save(Usuario.builder().nombre("Ana").apellido("Ruiz").email("ana@polygraph.com")
                .password("x").rol(rol).activo(true).requiere2fa(false).emailVerificado(true)
                .idEmpleado(emp.getIdEmpleado()).fechaCreacion(LocalDateTime.now())
                .requiereCambioPassword(false).build());
        em.flush();
        em.clear();
        return u;
    }

    private ActualizarUsuarioInternoRequest edicion(Rol rol, List<CiudadAsignadaRequest> ciudades) {
        return new ActualizarUsuarioInternoRequest("Ana María", "Ruiz", "ana@polygraph.com", null, rol,
                "3001112233", "CC", "123", null, null, ciudades, null);
    }

    /** Lo que quedó guardado en la base como «idCiudad:sala», ordenado. */
    private List<String> guardadas(Usuario u) {
        em.flush();
        em.clear();
        return empleadoRepository.findById(u.getIdEmpleado()).orElseThrow().getCiudadesAsignadas().stream()
                .map(c -> c.getCiudad().getIdCiudad() + ":" + c.getNumeroSala()).sorted().toList();
    }

    @Test
    @DisplayName("Poligrafista: guardar sin cambiar ciudad ni sala ya no choca con uq_empleado_ciudad_sala")
    void sinCambios() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA,
                List.of(new CiudadAsignadaRequest(bogota.getIdCiudad(), 1))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":1");
    }

    @Test
    @DisplayName("Poligrafista: conserva las que siguen, agrega la nueva y quita la que ya no está")
    void agregaYQuita() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1}, new Object[]{cali, 2});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA, List.of(
                new CiudadAsignadaRequest(bogota.getIdCiudad(), 1),
                new CiudadAsignadaRequest(medellin.getIdCiudad(), 3))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":1", medellin.getIdCiudad() + ":3");
    }

    @Test
    @DisplayName("Poligrafista: cambiar solo el número de sala de una ciudad reemplaza esa fila")
    void cambiaLaSala() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA,
                List.of(new CiudadAsignadaRequest(bogota.getIdCiudad(), 2))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":2");
    }

    @Test
    @DisplayName("Poligrafista: la misma ciudad y sala repetida en la petición cuenta una sola vez")
    void repetidaEnLaPeticion() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA, List.of(
                new CiudadAsignadaRequest(bogota.getIdCiudad(), 1),
                new CiudadAsignadaRequest(bogota.getIdCiudad(), 1))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":1");
    }

    @Test
    @DisplayName("Poligrafista: la misma ciudad con DOS salas distintas es válida")
    void mismaCiudadDosSalas() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA, List.of(
                new CiudadAsignadaRequest(bogota.getIdCiudad(), 1),
                new CiudadAsignadaRequest(bogota.getIdCiudad(), 2))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":1", bogota.getIdCiudad() + ":2");
    }

    @Test
    @DisplayName("Visitador: guardar sin cambios deja una sola fila por ciudad (sin sala)")
    void visitadorSinCambios() {
        Usuario u = empleado(Rol.VISITADOR, new Object[]{bogota, 0}, new Object[]{cali, 0});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.VISITADOR, List.of(
                new CiudadAsignadaRequest(bogota.getIdCiudad(), null),
                new CiudadAsignadaRequest(cali.getIdCiudad(), null))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":null", cali.getIdCiudad() + ":null");
    }

    @Test
    @DisplayName("Pasar de poligrafista a visitador cambia las filas (la sala deja de aplicar)")
    void cambiaDeRol() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1});

        servicio.actualizar(u.getIdUsuario(), edicion(Rol.VISITADOR,
                List.of(new CiudadAsignadaRequest(bogota.getIdCiudad(), 1))));

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":null");
    }

    @Test
    @DisplayName("Sigue exigiendo al menos una ciudad y una sala válida (400) y no toca lo guardado")
    void validaciones() {
        Usuario u = empleado(Rol.POLIGRAFISTA, new Object[]{bogota, 1});

        assertThatThrownBy(() -> servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA, List.of())))
                .isInstanceOf(ApiException.class).hasMessageContaining("al menos una ciudad");
        assertThatThrownBy(() -> servicio.actualizar(u.getIdUsuario(), edicion(Rol.POLIGRAFISTA,
                List.of(new CiudadAsignadaRequest(bogota.getIdCiudad(), null)))))
                .isInstanceOf(ApiException.class).hasMessageContaining("número de sala");

        assertThat(guardadas(u)).containsExactly(bogota.getIdCiudad() + ":1");
    }
}
