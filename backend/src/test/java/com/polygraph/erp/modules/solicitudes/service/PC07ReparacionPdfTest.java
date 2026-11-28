package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import com.polygraph.erp.modules.solicitudes.repository.DocumentoEstudioRepository;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PC-07: el PDF del estudio que nunca se armó (el del #42) se reemplaza por una versión nueva que va por la cola, una sola vez, sin borrar la anterior. */
@DisplayName("PC-07 — regenerar el PDF atascado como versión nueva")
class PC07ReparacionPdfTest {

    private DocumentoEstudioRepository documentos;
    private UsuarioRepository usuarios;
    private ColaTrabajosService cola;
    private EmisionEstudioService emision;
    private final Usuario gestor = Usuario.builder().idUsuario(5L).email("g@x.co").rol(Rol.GESTOR).activo(true).build();

    @BeforeEach
    void setUp() {
        documentos = mock(DocumentoEstudioRepository.class);
        usuarios = mock(UsuarioRepository.class);
        cola = mock(ColaTrabajosService.class);
        emision = new EmisionEstudioService(mock(ServicioRepository.class), mock(ServicioSubprocesoRepository.class), usuarios, mock(CalificacionRiesgoService.class),
                mock(CancelacionSolicitudService.class), documentos, cola, mock(JdbcTemplate.class), mock(AlmacenTrabajos.class), mock(NotificacionRepository.class));
        when(usuarios.findById(5L)).thenReturn(Optional.of(gestor));
        when(documentos.save(any(DocumentoEstudio.class))).thenAnswer(i -> { DocumentoEstudio d = i.getArgument(0); if (d.getId() == null) d.setId(99L); return d; });
    }

    private DocumentoEstudio atascado(String estado) {
        DocumentoEstudio d = DocumentoEstudio.builder().id(2L).idServicio(42).version(1).tipo("FINAL").nivel("BAJO").estado(estado).vigente(true).idUsuario(5L).build();
        when(documentos.findById(2L)).thenReturn(Optional.of(d));
        when(documentos.findFirstByIdServicioOrderByVersionDesc(42)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    @DisplayName("El #42: la v1 (GENERANDO para siempre) queda en el historial como no vigente y en ERROR; nace la v2 y entra a la cola con quien emitió")
    void reemplazaPorUnaVersionNueva() {
        DocumentoEstudio v1 = atascado(DocumentoEstudio.GENERANDO);
        int version = emision.regenerarComoNuevaVersion(2L);

        assertThat(version).isEqualTo(2);
        assertThat(v1.getVigente()).isFalse();
        assertThat(v1.getEstado()).isEqualTo(DocumentoEstudio.ERROR);
        assertThat(v1.getDetalle()).contains("versión 2").contains("PC-07");
        ArgumentCaptor<DocumentoEstudio> nuevo = ArgumentCaptor.forClass(DocumentoEstudio.class);
        verify(documentos, org.mockito.Mockito.atLeastOnce()).save(nuevo.capture());
        DocumentoEstudio v2 = nuevo.getAllValues().stream().filter(d -> d != v1).findFirst().orElseThrow();
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getTipo()).isEqualTo("FINAL");
        assertThat(v2.getNivel()).isEqualTo("BAJO");
        assertThat(v2.getEstado()).isEqualTo(DocumentoEstudio.GENERANDO);
        assertThat(v2.getVigente()).isTrue();
        verify(cola).encolarTrasCommit(eq(TipoTrabajo.ESTUDIO_PDF), eq(gestor), eq(Map.of("idDocumento", 99L)));
    }

    @Test
    @DisplayName("También reemplaza uno que quedó en ERROR sin archivo")
    void tambienDelEnError() {
        atascado(DocumentoEstudio.ERROR);
        assertThat(emision.regenerarComoNuevaVersion(2L)).isEqualTo(2);
    }

    @Test
    @DisplayName("No toca lo que no hace falta: un PDF listo, con archivo, preliminar, que ya no es el vigente o sin saber quién lo emitió")
    void noTocaLoQueEstaBien() {
        DocumentoEstudio d = atascado(DocumentoEstudio.LISTO);
        d.setRuta("estudios/42-v1.pdf");
        assertThat(emision.regenerarComoNuevaVersion(2L)).isZero();

        d.setEstado(DocumentoEstudio.GENERANDO); d.setRuta(null); d.setTipo("PRELIMINAR");
        assertThat(emision.regenerarComoNuevaVersion(2L)).isZero();

        d.setTipo("FINAL"); d.setVigente(false);
        assertThat(emision.regenerarComoNuevaVersion(2L)).isZero();

        d.setVigente(true); d.setIdUsuario(null);
        assertThat(emision.regenerarComoNuevaVersion(2L)).isZero();
        assertThat(emision.regenerarComoNuevaVersion(404L)).isZero();
        verify(cola, never()).encolarTrasCommit(any(), any(), any());
    }

    @Test
    @DisplayName("Al arrancar corre una sola vez por PDF: lo pendiente del reporte se regenera y se anota; lo ya anotado no se repite")
    void correUnaSolaVez() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmisionEstudioService emisionFalsa = mock(EmisionEstudioService.class);
        when(emisionFalsa.regenerarComoNuevaVersion(2L)).thenReturn(2);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id_servicio", 42, "despues", "2")));
        ReparacionPdfEstudios reparacion = new ReparacionPdfEstudios(jdbc, emisionFalsa);

        assertThat(reparacion.regenerarPendientes()).isEqualTo(1);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("PDF_REGENERADO"), eq(42), eq("2"), eq("versión 2 en la cola"));

        when(jdbc.queryForList(anyString())).thenReturn(List.of());                      // el reporte ya trae su PDF_REGENERADO: no hay nada pendiente
        assertThat(reparacion.regenerarPendientes()).isZero();
        verify(emisionFalsa, org.mockito.Mockito.times(1)).regenerarComoNuevaVersion(anyLong());
    }

    @Test
    @DisplayName("Sin la tabla del reporte (otro entorno) o con un fallo, arrancar no se rompe")
    void sinTablaNoRompe() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("x", "SELECT", new java.sql.SQLException("no existe la tabla")));
        new ReparacionPdfEstudios(jdbc, mock(EmisionEstudioService.class)).alArrancar();   // no lanza
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private static long anyLong() { return org.mockito.ArgumentMatchers.anyLong(); }
}
