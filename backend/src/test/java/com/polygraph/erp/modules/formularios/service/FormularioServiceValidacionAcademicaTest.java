package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.EducacionEvaluado;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba end-to-end (contra H2 real) del esquema de "columnas de validación" sobre una tabla
 *  fija de origen automático (acá, Educación — la que el usuario llamó "validación académica")
 *  — el punto central que había que verificar: cada fila de validación queda correlacionada con
 *  la fila REAL de EducacionEvaluado que le corresponde (por id, no por posición), sin escribir
 *  nada en esa tabla original, y una fila nueva agregada después no corrompe lo ya guardado.
 *  @Import trae FormularioService/CandidatoInfoService reales (no están en el slice de
 *  @DataJpaTest por defecto); NotificacionService y AlmacenamientoDocumentosService se mockean
 *  porque no participan en obtener()/guardar() de este escenario. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FormularioService.class, CandidatoInfoService.class})
@DisplayName("FormularioService — columnas de validación sobre tabla fija (Educación)")
class FormularioServiceValidacionAcademicaTest {

    @Autowired private FormularioService formularioService;
    @Autowired private jakarta.persistence.EntityManager em;
    @MockBean private NotificacionService notificacionService;
    @MockBean private AlmacenamientoDocumentosService almacenamientoService;

    private Usuario analista;
    private ServicioSubproceso subproceso;
    private FormularioSeccion seccionEducacion;
    private FormularioColumna columnaVerificado;
    private EducacionEvaluado educacionA;
    private EducacionEvaluado educacionB;
    private HojaVidaEvaluado hojaVida;

    @BeforeEach
    void setUp() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder()
                .codigo("SEG").nombre("Seguridad").activo(true).build());
        Proceso proceso = persistir(Proceso.builder()
                .nombreProceso("Estudio de seguridad").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        TipoProgreso tipoProgreso = persistir(TipoProgreso.builder()
                .nombreProgreso("Académica").rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(false).build());

        analista = persistir(Usuario.builder()
                .nombre("Ana").email("ana.analista@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Usuario solicitante = persistir(Usuario.builder()
                .nombre("Sistema").email("sistema2@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());

        Cliente cliente = persistir(Cliente.builder()
                .tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Cliente de prueba S.A.S.").emailPrincipal("cliente2@test.com").idGestor(1L).build());

        Candidato candidato = persistir(Candidato.builder()
                .cedula("1000999888").tipoDocumento("CC").nombres("Diego").apellidos("Prueba").build());

        hojaVida = persistir(HojaVidaEvaluado.builder()
                .candidato(candidato).autorizacionDatos(true).completado(true).build());
        educacionA = persistir(EducacionEvaluado.builder()
                .hojaVidaEvaluado(hojaVida).institucion("Universidad Nacional").nivel("Universitario")
                .fechaInicio(LocalDate.of(2015, 1, 1)).build());
        educacionB = persistir(EducacionEvaluado.builder()
                .hojaVidaEvaluado(hojaVida).institucion("Colegio San José").nivel("Bachillerato")
                .fechaInicio(LocalDate.of(2008, 1, 1)).build());

        Servicio servicio = persistir(Servicio.builder()
                .cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante)
                .fechaSolicitud(LocalDate.now()).estado(EstadoServicio.PENDIENTE).build());

        subproceso = persistir(ServicioSubproceso.builder()
                .servicio(servicio).tipoProgreso(tipoProgreso).usuarioAsignado(analista)
                .estado(EstadoAsignacion.ASIGNADO).fechaCreacion(java.time.LocalDateTime.now()).build());

        seccionEducacion = persistir(FormularioSeccion.builder()
                .tipoProgreso(tipoProgreso).nombre("Educación").tipo(TipoSeccionFormulario.TABLA)
                .orden(0).activo(true).clave("educacion").origenFijo("educacion").versionCreacion(1).build());
        columnaVerificado = persistir(FormularioColumna.builder()
                .seccion(seccionEducacion).nombre("Verificado").tipoDato(TipoDatoFormulario.BOOLEANO)
                .orden(0).activo(true).clave("verificado").build());

        em.flush();
        em.clear();
    }

    private FormularioResponse.TablaFijaResponse tablaEducacion(FormularioResponse r) {
        return r.tablasFijas().stream().filter(t -> "educacion".equals(t.clave())).findFirst()
                .orElseThrow(() -> new AssertionError("No vino la tabla fija 'educacion' en la respuesta"));
    }

    @Test
    @DisplayName("Antes de guardar nada: 2 filas fijas, columna de validación presente, sin idFila todavía")
    void obtenerAntesDeGuardar() {
        FormularioResponse r = formularioService.obtener(subproceso.getId(), analista);
        FormularioResponse.TablaFijaResponse tabla = tablaEducacion(r);

        assertThat(tabla.columnasValidacion()).hasSize(1);
        assertThat(tabla.columnasValidacion().get(0).clave()).isEqualTo("verificado");
        assertThat(tabla.filas()).hasSize(2);
        assertThat(tabla.validacionPorId()).hasSize(2);
        assertThat(tabla.validacionPorId().values()).allMatch(v -> v.idFila() == null && v.valores().isEmpty());
    }

    @Test
    @DisplayName("Guardar valida cada fila por separado — no se mezclan entre sí, y la tabla original no se toca")
    void guardarCorrelacionaPorFilaSinTocarLaTablaOriginal() {
        var req = new GuardarFormularioRequest(List.of(
                new GuardarFormularioRequest.SeccionDatosRequest(seccionEducacion.getIdSeccion(), null, List.of(
                        new GuardarFormularioRequest.FilaDatosRequest(null, Map.of(columnaVerificado.getIdColumna(), "true"), educacionA.getId()),
                        new GuardarFormularioRequest.FilaDatosRequest(null, Map.of(columnaVerificado.getIdColumna(), "false"), educacionB.getId())
                ))
        ));
        formularioService.guardar(subproceso.getId(), req, analista);
        em.clear();

        FormularioResponse r = formularioService.obtener(subproceso.getId(), analista);
        FormularioResponse.TablaFijaResponse tabla = tablaEducacion(r);

        var validacionA = tabla.validacionPorId().get(String.valueOf(educacionA.getId()));
        var validacionB = tabla.validacionPorId().get(String.valueOf(educacionB.getId()));
        assertThat(validacionA.idFila()).isNotNull();
        assertThat(validacionB.idFila()).isNotNull();
        assertThat(validacionA.idFila()).isNotEqualTo(validacionB.idFila());
        assertThat(validacionA.valores().get(columnaVerificado.getIdColumna())).isEqualTo("true");
        assertThat(validacionB.valores().get(columnaVerificado.getIdColumna())).isEqualTo("false");

        // La tabla original del candidato sigue exactamente igual — nada de esto la tocó.
        EducacionEvaluado recargada = em.find(EducacionEvaluado.class, educacionA.getId());
        assertThat(recargada.getInstitucion()).isEqualTo("Universidad Nacional");
    }

    @Test
    @DisplayName("Una fila de educación agregada DESPUÉS de guardar aparece sin validar, sin afectar las ya guardadas")
    void filaNuevaAgregadaDespuesNoCorrompeLasExistentes() {
        var req = new GuardarFormularioRequest(List.of(
                new GuardarFormularioRequest.SeccionDatosRequest(seccionEducacion.getIdSeccion(), null, List.of(
                        new GuardarFormularioRequest.FilaDatosRequest(null, Map.of(columnaVerificado.getIdColumna(), "true"), educacionA.getId()),
                        new GuardarFormularioRequest.FilaDatosRequest(null, Map.of(columnaVerificado.getIdColumna(), "false"), educacionB.getId())
                ))
        ));
        formularioService.guardar(subproceso.getId(), req, analista);
        em.clear();

        HojaVidaEvaluado hv = em.find(HojaVidaEvaluado.class, hojaVida.getId());
        EducacionEvaluado educacionC = EducacionEvaluado.builder()
                .hojaVidaEvaluado(hv).institucion("Instituto Nuevo").nivel("Técnico")
                .fechaInicio(LocalDate.of(2020, 1, 1)).build();
        em.persist(educacionC);
        em.flush();
        em.clear();

        FormularioResponse r = formularioService.obtener(subproceso.getId(), analista);
        FormularioResponse.TablaFijaResponse tabla = tablaEducacion(r);

        assertThat(tabla.filas()).hasSize(3);
        var validacionA = tabla.validacionPorId().get(String.valueOf(educacionA.getId()));
        var validacionB = tabla.validacionPorId().get(String.valueOf(educacionB.getId()));
        var validacionC = tabla.validacionPorId().get(String.valueOf(educacionC.getId()));
        assertThat(validacionA.valores().get(columnaVerificado.getIdColumna())).isEqualTo("true");
        assertThat(validacionB.valores().get(columnaVerificado.getIdColumna())).isEqualTo("false");
        assertThat(validacionC.idFila()).isNull();
        assertThat(validacionC.valores()).isEmpty();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
