package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.dto.FormularioResponse;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest.FilaDatosRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarFormularioRequest.SeccionDatosRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.ColumnaRequest;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest.SeccionRequest;
import com.polygraph.erp.modules.formularios.dto.SeccionPlantillaResponse;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.*;
import com.polygraph.erp.shared.exceptions.ApiException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Secciones opcionales (V212, pedido del usuario 2026-10-14): el admin decide cuáles puede quitar
 *  quien diligencia; una sección quitada no exige sus obligatorios, no cuenta en el avance y lo que
 *  tenía se borra. Una obligatoria no se puede quitar. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FormularioPlantillaService.class, FormularioService.class, CandidatoInfoService.class})
@DisplayName("Secciones opcionales del formulario")
class SeccionesOpcionalesFormularioTest {

    @Autowired private FormularioPlantillaService plantillaService;
    @Autowired private FormularioService formularioService;
    @Autowired private jakarta.persistence.EntityManager em;
    @MockBean private NotificacionService notificacionService;
    @MockBean private AlmacenamientoDocumentosService almacenamientoService;
    @MockBean private com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService calculoSubservicioService;
    @MockBean private FormularioDocumentoService documentoService;
    @MockBean private FormularioPlantillaWordService plantillaWordService;

    private TipoProgreso tipoProgreso;
    private Usuario analista;
    private ServicioSubproceso subproceso;
    private Integer idBasica;
    private Integer idOpcional;
    private Integer idTablaOpcional;

    @BeforeEach
    void setUp() {
        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("SEG").nombre("Seguridad").activo(true).build());
        Proceso proceso = persistir(Proceso.builder().nombreProceso("BASICO").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        tipoProgreso = persistir(TipoProgreso.builder().nombreProgreso("Antecedentes")
                .rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(false).build());
        analista = persistir(Usuario.builder().nombre("Ana").email("ana.opcional@test.com").password("x")
                .rol(Rol.ANALISTA_INTERNO).activo(true).emailVerificado(true).build());
        Usuario solicitante = persistir(Usuario.builder().nombre("Sis").email("sis.opcional@test.com").password("x")
                .rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
        Cliente cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA)
                .razonSocial("Cliente S.A.S.").emailPrincipal("cli.opcional@test.com").idGestor(1L).build());
        Candidato candidato = persistir(Candidato.builder().cedula("1000333444").tipoDocumento("CC").nombres("Luis").apellidos("Prueba").build());
        Servicio servicio = persistir(Servicio.builder().cliente(cliente).candidato(candidato).proceso(proceso).usuarioSolicita(solicitante)
                .fechaSolicitud(LocalDate.now()).estado(EstadoServicio.PENDIENTE).build());
        subproceso = persistir(ServicioSubproceso.builder().servicio(servicio).tipoProgreso(tipoProgreso).usuarioAsignado(analista)
                .estado(EstadoSubproceso.EN_EJECUCION).fechaCreacion(java.time.LocalDateTime.now()).build());

        // Básica (obligatoria), Observaciones (opcional, con un campo obligatorio) y Hallazgos (tabla opcional)
        List<SeccionPlantillaResponse> plantilla = plantillaService.guardar(tipoProgreso.getIdTipoProgreso(), new GuardarPlantillaRequest(List.of(
                new SeccionRequest(null, "Básica", "CAMPOS", "basica", null, null,
                        List.of(campo("Concepto", "concepto", false)), null, null, false),
                new SeccionRequest(null, "Observaciones", "CAMPOS", "observaciones", null, null,
                        List.of(campo("Observación", "observacion", true)), null, null, true),
                new SeccionRequest(null, "Hallazgos", "TABLA", "hallazgos", null, null,
                        List.of(campo("Detalle", "detalle", false)), null, null, true))));
        idBasica = plantilla.get(0).idSeccion();
        idOpcional = plantilla.get(1).idSeccion();
        idTablaOpcional = plantilla.get(2).idSeccion();
        assertThat(plantilla).extracting(SeccionPlantillaResponse::opcional).containsExactly(false, true, true);
        em.flush();
        em.clear();
    }

    private static ColumnaRequest campo(String nombre, String clave, boolean obligatorio) {
        return new ColumnaRequest(null, nombre, "TEXTO", null, null, clave, null, null, null, null, null, null, obligatorio);
    }

    private Integer columna(FormularioResponse r, Integer idSeccion) {
        return r.secciones().stream().filter(s -> s.idSeccion().equals(idSeccion)).findFirst().orElseThrow().columnas().get(0).idColumna();
    }

    private FormularioResponse.SeccionDatosResponse seccion(FormularioResponse r, Integer idSeccion) {
        return r.secciones().stream().filter(s -> s.idSeccion().equals(idSeccion)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Quitar una opcional: no exige su obligatorio, borra lo que tenía y se puede volver a agregar")
    void quitarOpcional() {
        FormularioResponse vacio = formularioService.obtener(subproceso.getId(), analista);
        Integer colObs = columna(vacio, idOpcional);
        Integer colDetalle = columna(vacio, idTablaOpcional);
        assertThat(seccion(vacio, idOpcional).opcional()).isTrue();
        assertThat(seccion(vacio, idBasica).opcional()).isFalse();

        // Primero la llena (y una fila en la tabla)...
        formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new SeccionDatosRequest(idOpcional, Map.of(colObs, "Algo"), null),
                new SeccionDatosRequest(idTablaOpcional, null, List.of(new FilaDatosRequest("", Map.of(colDetalle, "Fila"), null))))), analista);
        em.flush();
        em.clear();
        // ...el obligatorio vacío sí bloquea mientras la sección va
        formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new SeccionDatosRequest(idTablaOpcional, null, List.of(new FilaDatosRequest("", Map.of(colDetalle, "Fila"), null))))), analista);
        em.flush();
        em.clear();
        assertThat(formularioService.camposObligatoriosVacios(subproceso.getId())).containsExactly("Observaciones › Observación");

        // Quitar las dos opcionales: aunque la tabla tenía filas, quitarla sí las borra (lo pidió
        // quien diligencia), y lo que llegue de una sección quitada no se guarda
        FormularioResponse r = formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(
                List.of(new SeccionDatosRequest(idOpcional, Map.of(colObs, "No debe guardarse"), null)),
                List.of(idOpcional, idTablaOpcional)), analista);
        em.flush();
        em.clear();
        assertThat(seccion(r, idOpcional).omitida()).isTrue();
        assertThat(seccion(r, idTablaOpcional).omitida()).isTrue();
        assertThat(seccion(r, idOpcional).valores().get(colObs)).isEmpty();
        assertThat(formularioService.camposObligatoriosVacios(subproceso.getId())).isEmpty();

        // Volver a agregarla: vuelve a exigir el obligatorio
        FormularioResponse otra = formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(), List.of()), analista);
        em.flush();
        em.clear();
        assertThat(seccion(otra, idOpcional).omitida()).isFalse();
        assertThat(formularioService.camposObligatoriosVacios(subproceso.getId())).containsExactly("Observaciones › Observación");
    }

    @Test
    @DisplayName("Una sección obligatoria no se puede quitar")
    void obligatoriaNoSeQuita() {
        assertThatThrownBy(() -> formularioService.guardar(subproceso.getId(),
                new GuardarFormularioRequest(List.of(), List.of(idBasica)), analista))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("es obligatoria");
    }

    @Test
    @DisplayName("El ejecutor puede quitar una tabla opcional aunque ya tenga filas guardadas")
    void quitarTablaConFilas() {
        FormularioResponse vacio = formularioService.obtener(subproceso.getId(), analista);
        Integer colDetalle = columna(vacio, idTablaOpcional);
        formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of(
                new SeccionDatosRequest(idTablaOpcional, null, List.of(new FilaDatosRequest("", Map.of(colDetalle, "Fila"), null))))), analista);
        em.flush();
        em.clear();
        // Sin quitarla, mandar la tabla sin filas está prohibido para el ejecutor...
        assertThatThrownBy(() -> formularioService.guardar(subproceso.getId(), new GuardarFormularioRequest(List.of()), analista))
                .isInstanceOf(ApiException.class);
        em.clear();
        // ...quitándola sí se puede, y las filas se van
        FormularioResponse r = formularioService.guardar(subproceso.getId(),
                new GuardarFormularioRequest(List.of(), List.of(idTablaOpcional)), analista);
        assertThat(seccion(r, idTablaOpcional).filas()).isEmpty();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
