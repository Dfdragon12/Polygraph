package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import com.polygraph.erp.modules.solicitudes.repository.DocumentoEstudioRepository;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.shared.entity.Notificacion;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.NotificacionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PC-01: el PDF del estudio — «listo» con una sola regla, aviso al cliente solo cuando está listo, aviso al gestor si falla, reintento y límite de 15 minutos. */
@DisplayName("PC-01 — PDF del estudio completo")
class PC01EstudioPdfTest {

    private ServicioRepository servicios;
    private DocumentoEstudioRepository documentos;
    private UsuarioRepository usuarios;
    private NotificacionRepository notificaciones;
    private ColaTrabajosService cola;
    private EmisionEstudioService servicio;
    private Servicio s;
    private final Usuario gestor = Usuario.builder().idUsuario(5L).email("g@x.co").rol(Rol.GESTOR).activo(true).build();
    private final Usuario clienteUsuario = Usuario.builder().idUsuario(9L).email("c@x.co").rol(Rol.ADMIN_CLIENTE).activo(true).idCliente(1).build();

    @BeforeEach
    void setUp() {
        servicios = mock(ServicioRepository.class);
        documentos = mock(DocumentoEstudioRepository.class);
        usuarios = mock(UsuarioRepository.class);
        notificaciones = mock(NotificacionRepository.class);
        cola = mock(ColaTrabajosService.class);
        servicio = new EmisionEstudioService(servicios, mock(ServicioSubprocesoRepository.class), usuarios, mock(CalificacionRiesgoService.class),
                mock(CancelacionSolicitudService.class), documentos, cola, mock(JdbcTemplate.class), mock(AlmacenTrabajos.class), notificaciones);
        s = Servicio.builder().idServicio(42).estado(EstadoServicio.PUBLICADO).pastaEstado(PastaEstado.EMITIDA)
                .cliente(Cliente.builder().idCliente(1).idGestor(5L).build())
                .candidato(Candidato.builder().nombres("Laura").apellidos("Ejemplo").build()).build();
        when(servicios.findById(42)).thenReturn(Optional.of(s));
        when(usuarios.findById(5L)).thenReturn(Optional.of(gestor));
        when(usuarios.findByEmail("g@x.co")).thenReturn(Optional.of(gestor));
        when(usuarios.findByIdCliente(1)).thenReturn(List.of(clienteUsuario));
    }

    private DocumentoEstudio doc(String estado, String ruta, String tipo, int version) {
        DocumentoEstudio d = DocumentoEstudio.builder().id(77L).idServicio(42).version(version).tipo(tipo).estado(estado).ruta(ruta).vigente(true).build();
        when(documentos.findById(77L)).thenReturn(Optional.of(d));
        when(documentos.findFirstByIdServicioAndVigenteTrueOrderByVersionDesc(42)).thenReturn(Optional.of(d));
        return d;
    }

    private UserDetails sesion() {
        UserDetails u = mock(UserDetails.class);
        when(u.getUsername()).thenReturn("g@x.co");
        return u;
    }

    @Test
    @DisplayName("«Listo» = publicado + pasta emitida + PDF LISTO guardado; con el PDF generándose, en error o sin PDF no está listo")
    void reglaUnicaDeListo() {
        assertThat(servicio.disponibleParaCliente(s)).isFalse();                      // pasta emitida sin ningún PDF (estudio migrado)
        doc(DocumentoEstudio.GENERANDO, null, "FINAL", 1);
        assertThat(servicio.disponibleParaCliente(s)).isFalse();
        doc(DocumentoEstudio.ERROR, null, "FINAL", 1);
        assertThat(servicio.disponibleParaCliente(s)).isFalse();
        doc(DocumentoEstudio.LISTO, "estudios/42-v1.pdf", "FINAL", 1);
        assertThat(servicio.disponibleParaCliente(s)).isTrue();
        s.setPastaEstado(PastaEstado.PRELIMINAR);                                      // un preliminar nunca es «listo» para el cliente
        assertThat(servicio.disponibleParaCliente(s)).isFalse();
    }

    @Test
    @DisplayName("El cliente recibe «Tu informe está listo» cuando el PDF queda listo, una sola vez")
    void avisaAlClienteAlTerminar() {
        doc(DocumentoEstudio.GENERANDO, null, "FINAL", 1);
        servicio.marcarListo(77L, "estudios/42-v1.pdf", 5);
        ArgumentCaptor<Notificacion> c = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(c.capture());
        assertThat(c.getValue().getTitulo()).isEqualTo("Tu informe está listo");
        assertThat(c.getValue().getUsuario()).isEqualTo(clienteUsuario);
        servicio.marcarListo(77L, "estudios/42-v1.pdf", 5);                             // un reintento duplicado no vuelve a avisar
        verify(notificaciones, times(1)).save(any());
    }

    @Test
    @DisplayName("Una versión nueva avisa que el PDF se actualizó; un preliminar no avisa al cliente")
    void versionNuevaYPreliminar() {
        doc(DocumentoEstudio.GENERANDO, null, "FINAL", 2);
        servicio.marcarListo(77L, "estudios/42-v2.pdf", 6);
        ArgumentCaptor<Notificacion> c = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(c.capture());
        assertThat(c.getValue().getTitulo()).isEqualTo("El PDF de tu estudio se actualizó");

        org.mockito.Mockito.reset(notificaciones);
        doc(DocumentoEstudio.GENERANDO, null, "PRELIMINAR", 3);
        servicio.marcarListo(77L, "estudios/42-v3.pdf", 3);
        verify(notificaciones, never()).save(any());
    }

    @Test
    @DisplayName("Si falla de verdad, el PDF queda en ERROR y el gestor del cliente recibe el aviso (una vez)")
    void falloAvisaAlGestor() {
        DocumentoEstudio d = doc(DocumentoEstudio.GENERANDO, null, "FINAL", 1);
        servicio.marcarError(77L, "No se pudo armar el PDF: plantilla");
        assertThat(d.getEstado()).isEqualTo(DocumentoEstudio.ERROR);
        ArgumentCaptor<Notificacion> c = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(c.capture());
        assertThat(c.getValue().getUsuario()).isEqualTo(gestor);
        assertThat(c.getValue().getMensaje()).contains("Reintentar");
        servicio.marcarError(77L, "otra vez");
        verify(notificaciones, times(1)).save(any());
    }

    @Test
    @DisplayName("Un PDF con más de 15 minutos en GENERANDO pasa a ERROR")
    void vencenLosQueTardan() {
        DocumentoEstudio viejo = DocumentoEstudio.builder().id(77L).idServicio(42).version(1).tipo("FINAL").estado(DocumentoEstudio.GENERANDO)
                .fecha(LocalDateTime.now().minusMinutes(40)).build();
        when(documentos.findByEstadoAndFechaBefore(eq(DocumentoEstudio.GENERANDO), any())).thenReturn(List.of(viejo));
        when(documentos.findById(77L)).thenReturn(Optional.of(viejo));
        assertThat(servicio.vencerLosQueTardan()).isEqualTo(1);
        assertThat(viejo.getEstado()).isEqualTo(DocumentoEstudio.ERROR);
        assertThat(viejo.getDetalle()).contains("15 minutos");
    }

    @Test
    @DisplayName("Reintentar vuelve a encolar la MISMA versión; no se reintenta uno listo ni uno que se está generando hace poco")
    void reintentar() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            DocumentoEstudio d = doc(DocumentoEstudio.ERROR, null, "FINAL", 3);
            var r = servicio.reintentarPdf(42, sesion());
            assertThat(r.version()).isEqualTo(3);
            assertThat(d.getEstado()).isEqualTo(DocumentoEstudio.GENERANDO);
            for (var t : TransactionSynchronizationManager.getSynchronizations()) t.afterCommit();
            verify(cola).encolarTrasCommit(eq(TipoTrabajo.ESTUDIO_PDF), any(), any());

            doc(DocumentoEstudio.LISTO, "estudios/42-v3.pdf", "FINAL", 3);
            assertThatThrownBy(() -> servicio.reintentarPdf(42, sesion())).isInstanceOf(ApiException.class).hasMessageContaining("ya está listo");
            DocumentoEstudio reciente = doc(DocumentoEstudio.GENERANDO, null, "FINAL", 3);
            reciente.setFecha(LocalDateTime.now());
            assertThatThrownBy(() -> servicio.reintentarPdf(42, sesion())).isInstanceOf(ApiException.class).hasMessageContaining("se está generando");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("La vista previa lleva la marca «VISTA PREVIA» en cada página y conserva sus páginas")
    void marcaDeAgua() throws Exception {
        byte[] original;
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.addPage(new PDPage());
            d.addPage(new PDPage());
            d.save(o);
            original = o.toByteArray();
        }
        byte[] marcado = EstudioPdfProcesador.marcaDeAgua(original, "VISTA PREVIA");
        try (PDDocument d = Loader.loadPDF(marcado)) {
            assertThat(d.getNumberOfPages()).isEqualTo(2);
            for (PDPage pagina : d.getPages()) {   // el texto va rotado: se revisa el contenido de la página, no el texto extraído
                String contenido = new String(pagina.getContents().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                assertThat(contenido).contains("(VISTA PREVIA)");
            }
        }
    }
}
