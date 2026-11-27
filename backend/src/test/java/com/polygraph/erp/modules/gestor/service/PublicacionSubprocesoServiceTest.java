package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.formularios.service.ConclusionFormatoService;
import com.polygraph.erp.modules.gestor.dto.PublicarSubprocesoRequest;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PB-02: publicar un subproceso con conclusión y calificación, siempre con historial. */
class PublicacionSubprocesoServiceTest {

    private AsignacionService asignacion;
    private CalificacionRiesgoService riesgo;
    private JdbcTemplate jdbc;
    private ConclusionFormatoService conclusionFormato;
    private NotificacionRepository notificaciones;
    private UsuarioRepository usuarios;
    private NovedadSubprocesoService novedades;
    private PublicacionSubprocesoService servicio;
    private final Usuario gestor = Usuario.builder().idUsuario(5L).nombre("Andrea").rol(Rol.GESTOR).build();
    private ServicioSubproceso sp;

    @BeforeEach
    void setUp() {
        asignacion = mock(AsignacionService.class);
        riesgo = mock(CalificacionRiesgoService.class);
        jdbc = mock(JdbcTemplate.class);
        conclusionFormato = mock(ConclusionFormatoService.class);
        notificaciones = mock(NotificacionRepository.class);
        usuarios = mock(UsuarioRepository.class);
        novedades = mock(NovedadSubprocesoService.class);
        servicio = new PublicacionSubprocesoService(asignacion, riesgo, jdbc, notificaciones, usuarios, conclusionFormato, novedades);
        sp = ServicioSubproceso.builder().id(9L).estado(EstadoSubproceso.FINALIZADO).conclusion("Se confirmaron 2 de 2 experiencias.")
                .tipoProgreso(TipoProgreso.builder().idTipoProgreso(1).nombreProgreso("Validacion Laboral").build())
                .servicio(Servicio.builder().cliente(Cliente.builder().idGestor(5L).build()).build()).build();
        when(conclusionFormato.efectiva(sp)).thenReturn("Se confirmaron 2 de 2 experiencias.");   // lo que escribió el ejecutor
        when(asignacion.obtenerOFallar(9L)).thenReturn(sp);
        when(riesgo.resumenSubproceso(eq(9L), any())).thenReturn(new CalificacionRiesgoService.ResumenSubproceso(
                true, "REALIZADO", "BAJO", new BigDecimal("15"), false, null, true, null));
    }

    @Test
    @DisplayName("PC-09: un gestor de otro cliente recibe 403 al publicar, sin enterarse del estado del subproceso (aunque no esté en Finalizado)")
    void unGestorAjenoRecibe403AntesQueElEstado() {
        Usuario ajeno = Usuario.builder().idUsuario(6L).nombre("Otro").rol(Rol.GESTOR).build();
        sp.setEstado(EstadoSubproceso.PUBLICADO);
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), ajeno, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("no gestionas")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    @Test
    @DisplayName("Sin marcar «revisé el informe» no se publica")
    void exigeLaCasillaDeRevision() {
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, false), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Confirma que revisaste");
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    @Test
    @DisplayName("Publica con la conclusión del ejecutor y el nivel sugerido; queda el historial de la publicación")
    void publicaConLoSugerido() {
        servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        verify(asignacion).aprobar(eq(9L), eq(gestor), any());
        assertThat(sp.getConclusionPublicada()).isEqualTo("Se confirmaron 2 de 2 experiencias.");
        assertThat(sp.getNivelPublicado()).isEqualTo("BAJO");
        assertThat(sp.getPuntajePublicado()).isEqualByComparingTo("15");
        assertThat(sp.getPublicadoPor()).isEqualTo(5L);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("PUBLICAR"), any(), eq("BAJO"), any(), eq(5L), any());
    }

    @Test
    @DisplayName("Si el gestor cambia la conclusión, el texto original queda en el historial")
    void cambiarLaConclusionGuardaLaOriginal() {
        servicio.publicar(9L, new PublicarSubprocesoRequest("Texto ajustado por el gestor.", null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        assertThat(sp.getConclusionPublicada()).isEqualTo("Texto ajustado por el gestor.");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("CAMBIO_CONCLUSION"),
                eq("Se confirmaron 2 de 2 experiencias."), eq("Texto ajustado por el gestor."), any(), eq(5L), any());
    }

    @Test
    @DisplayName("Cambiar la calificación sugerida exige motivo; con motivo queda anterior, nuevo y por qué")
    void cambiarElNivelExigeMotivo() {
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, "MEDIO", " ", true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("motivo");
        verify(asignacion, never()).aprobar(any(), any(), any());

        servicio.publicar(9L, new PublicarSubprocesoRequest(null, "MEDIO", "Hubo una inconsistencia menor", true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        assertThat(sp.getNivelPublicado()).isEqualTo("MEDIO");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("CAMBIO_NIVEL"),
                eq("BAJO"), eq("MEDIO"), eq("Hubo una inconsistencia menor"), eq(5L), any());
    }

    @Test
    @DisplayName("Solo se publica lo que está en Finalizado; sin conclusión tampoco")
    void soloDesdeFinalizadoYConConclusion() {
        sp.setEstado(EstadoSubproceso.EN_EJECUCION);
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Finalizado");
        sp.setEstado(EstadoSubproceso.FINALIZADO);
        sp.setConclusion(null);
        when(conclusionFormato.efectiva(sp)).thenReturn(null);
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("conclusión");
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    // ───────────────────────── PC-02 ─────────────────────────

    @Test
    @DisplayName("PC-02 A · Un informe sin texto en el cuadro genérico publica con lo que el ejecutor escribió en su formato («Concepto del analista»)")
    void laConclusionDelFormatoLlegaPrecargada() {
        sp.setConclusion(null);   // el caso del #42: el analista escribió su concepto en el formato, no en el cuadro genérico
        when(conclusionFormato.efectiva(sp)).thenReturn("No registra antecedentes ni coincidencias en listas restrictivas.");
        servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        assertThat(sp.getConclusionPublicada()).isEqualTo("No registra antecedentes ni coincidencias en listas restrictivas.");
        assertThat(sp.getConclusion()).isEqualTo("No registra antecedentes ni coincidencias en listas restrictivas.");   // queda el original para el historial
    }

    @Test
    @DisplayName("PC-02 B · Con «favorable» o «desfavorable» (en cualquier mayúscula) no se publica, venga de la ventana o del ejecutor")
    void conFavorableNoSePublica() {
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest("Se emite concepto FAVORABLE para el cargo.", null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Favorable").hasMessageContaining("nivel");
        when(conclusionFormato.efectiva(sp)).thenReturn("Resultado desfavorable.");
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Desfavorable");
        verify(asignacion, never()).aprobar(any(), any(), any());
        // Quitada la palabra, publica.
        servicio.publicar(9L, new PublicarSubprocesoRequest("No registra antecedentes.", null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        verify(asignacion).aprobar(eq(9L), eq(gestor), any());
    }

    @Test
    @DisplayName("PC-02 C · Un subproceso que califica (poligrafía, visita, antecedentes…) no se publica sin nivel; dice por qué")
    void unSubprocesoQueCalificaNoSePublicaSinNivel() {
        when(riesgo.resumenSubproceso(eq(9L), any())).thenReturn(new CalificacionRiesgoService.ResumenSubproceso(
                true, "REALIZADO", null, null, false, null, false, "Falta la criticidad del cargo."));
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.POLIGRAFISTA)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("necesita su calificación para publicarse").hasMessageContaining("Falta la criticidad del cargo.")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));
        // Ni siquiera escogiendo un nivel a mano: no hay calificación en que apoyarlo.
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, "BAJO", "Lo decido yo", true), gestor, Set.of(Rol.POLIGRAFISTA)))
                .isInstanceOf(ApiException.class).hasMessageContaining("necesita su calificación");
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    @Test
    @DisplayName("PC-02 C · Un tipo que de verdad no califica (sin motor) se publica sin nivel")
    void unTipoSinMotorSePublicaSinNivel() {
        when(riesgo.resumenSubproceso(eq(9L), any())).thenReturn(new CalificacionRiesgoService.ResumenSubproceso(
                false, "REALIZADO", null, null, false, null, true, null));
        servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO));
        verify(asignacion).aprobar(eq(9L), eq(gestor), any());
        assertThat(sp.getNivelPublicado()).isNull();
        assertThat(sp.getConclusionPublicada()).isEqualTo("Se confirmaron 2 de 2 experiencias.");
    }

    @Test
    @DisplayName("PC-02 C · Si no se puede leer la calificación no se publica a ciegas")
    void sinLecturaDeCalificacionNoSePublica() {
        when(riesgo.resumenSubproceso(eq(9L), any())).thenThrow(new IllegalStateException("base caída"));
        assertThatThrownBy(() -> servicio.publicar(9L, new PublicarSubprocesoRequest(null, null, null, true), gestor, Set.of(Rol.ANALISTA_INTERNO)))
                .isInstanceOf(ApiException.class).hasMessageContaining("No se pudo leer la calificación");
        verify(asignacion, never()).aprobar(any(), any(), any());
    }

    @Test
    @DisplayName("PC-02 · Los datos de la ventana: conclusión del formato, calificación sugerida y si el subproceso la necesita; solo el gestor del cliente")
    void datosParaLaVentana() {
        when(conclusionFormato.origen(sp)).thenReturn("FORMATO");
        var d = servicio.datosParaPublicar(9L, gestor);
        assertThat(d.conclusion()).isEqualTo("Se confirmaron 2 de 2 experiencias.");
        assertThat(d.origenConclusion()).isEqualTo("FORMATO");
        assertThat(d.califica()).isTrue();
        assertThat(d.calculado()).isTrue();
        assertThat(d.nivel()).isEqualTo("BAJO");
        assertThat(d.puntaje()).isEqualByComparingTo("15");

        when(riesgo.resumenSubproceso(eq(9L), any())).thenReturn(new CalificacionRiesgoService.ResumenSubproceso(
                true, "REALIZADO", null, null, false, null, false, "Todavía no se ha diligenciado el formulario."));
        var sin = servicio.datosParaPublicar(9L, gestor);
        assertThat(sin.califica()).isTrue();
        assertThat(sin.calculado()).isFalse();
        assertThat(sin.nivel()).isNull();
        assertThat(sin.motivoSinCalcular()).contains("diligenciado");

        Usuario otroGestor = Usuario.builder().idUsuario(77L).nombre("Otro").rol(Rol.GESTOR).build();
        assertThatThrownBy(() -> servicio.datosParaPublicar(9L, otroGestor)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
    }

    // ───────────────────────── PC-04 · Solicitar corrección ─────────────────────────

    private final Usuario ejecutor = Usuario.builder().idUsuario(7L).nombre("Johana").rol(Rol.ANALISTA_INTERNO).build();

    @Test
    @DisplayName("PC-04 · «Solicitar corrección» solo en un informe publicado, eligiendo qué corregir y explicándolo")
    void solicitarCorreccionExigeLoQueDiceElDiseno() {
        assertThatThrownBy(() -> servicio.solicitarCorreccion(9L, "DATO_ERRADO", "Falta un dato", ejecutor)).isInstanceOf(ApiException.class).hasMessageContaining("publicado");
        sp.setEstado(EstadoSubproceso.PUBLICADO);
        assertThatThrownBy(() -> servicio.solicitarCorreccion(9L, null, "Falta un dato", ejecutor)).isInstanceOf(ApiException.class).hasMessageContaining("Elige qué hay que corregir");
        assertThatThrownBy(() -> servicio.solicitarCorreccion(9L, "INVENTADO", "Falta un dato", ejecutor)).isInstanceOf(ApiException.class).hasMessageContaining("Elige qué hay que corregir");
        assertThatThrownBy(() -> servicio.solicitarCorreccion(9L, "DATO_ERRADO", " ", ejecutor)).isInstanceOf(ApiException.class).hasMessageContaining("Explícale al gestor");
        verify(novedades, never()).registrarCorreccion(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PC-04 · Crea la novedad del tipo elegido, deja el historial y avisa al gestor del cliente (a quien se le dice «enviada a …»)")
    void solicitarCorreccionCreaLaNovedadParaElGestor() {
        sp.setEstado(EstadoSubproceso.PUBLICADO);
        Usuario gestorCliente = Usuario.builder().idUsuario(5L).nombre("Andrea").apellido("Clamente").activo(true).rol(Rol.GESTOR).build();
        when(usuarios.findById(5L)).thenReturn(java.util.Optional.of(gestorCliente));

        var r = servicio.solicitarCorreccion(9L, "dato_errado", "La fecha de expedición quedó 01/06/2010 y es 01/06/2011.", ejecutor);

        assertThat(r.gestor()).isEqualTo("Andrea Clamente");
        verify(novedades).registrarCorreccion(eq(sp), eq("CORRECCION_DATO_ERRADO"), eq("Dato errado: La fecha de expedición quedó 01/06/2010 y es 01/06/2011."), eq(ejecutor));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO historial_publicacion_subproceso"), eq(9L), eq("SOLICITUD_CORRECCION"),
                any(), eq("Dato errado"), eq("La fecha de expedición quedó 01/06/2010 y es 01/06/2011."), eq(7L), any());
        org.mockito.ArgumentCaptor<com.polygraph.erp.shared.entity.Notificacion> aviso = org.mockito.ArgumentCaptor.forClass(com.polygraph.erp.shared.entity.Notificacion.class);
        verify(notificaciones).save(aviso.capture());
        assertThat(aviso.getValue().getUsuario()).isSameAs(gestorCliente);
        assertThat(aviso.getValue().getTipo()).isEqualTo("CORRECCION_SOLICITADA");
        assertThat(aviso.getValue().getMensaje()).contains("dato errado").contains("01/06/2011");
    }

    @Test
    @DisplayName("PC-04 · Cada motivo del diseño tiene su tipo de novedad (V279)")
    void cadaMotivoTieneSuTipo() {
        assertThat(PublicacionSubprocesoService.TIPOS_CORRECCION).containsOnlyKeys("DATO_ERRADO", "SOPORTE_EQUIVOCADO", "HALLAZGO_MAL_CALIFICADO", "OTRO");
        assertThat(PublicacionSubprocesoService.TIPOS_CORRECCION.values()).allSatisfy(clave -> assertThat(clave).startsWith("CORRECCION_").hasSizeLessThanOrEqualTo(40));
    }
}
