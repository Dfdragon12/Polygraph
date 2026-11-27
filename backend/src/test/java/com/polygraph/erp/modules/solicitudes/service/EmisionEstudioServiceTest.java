package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.riesgo.dto.CalificacionRiesgoResponse;
import com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.solicitudes.dto.CambioEstadoRequest;
import com.polygraph.erp.modules.solicitudes.dto.EmitirEstudioRequest;
import com.polygraph.erp.modules.solicitudes.entity.DocumentoEstudio;
import com.polygraph.erp.modules.solicitudes.repository.DocumentoEstudioRepository;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PB-03: emitir el estudio (preliminar o final), con versión y sin dejar al cliente descargar un PDF que no coincide con lo publicado. */
class EmisionEstudioServiceTest {

    private ServicioRepository servicios;
    private ServicioSubprocesoRepository subprocesos;
    private CalificacionRiesgoService riesgo;
    private CancelacionSolicitudService cancelacion;
    private DocumentoEstudioRepository documentos;
    private ColaTrabajosService cola;
    private EmisionEstudioService servicio;
    private Servicio s;
    private final UserDetails sesion = mock(UserDetails.class);
    private final Usuario gestor = Usuario.builder().idUsuario(5L).email("g@x.co").rol(Rol.ADMIN_POLYGRAPH).build();

    private ServicioSubproceso sp(long id, String nombre, EstadoSubproceso estado) {
        return ServicioSubproceso.builder().id(id).servicio(s).estado(estado).tipoProgreso(TipoProgreso.builder().nombreProgreso(nombre).build()).build();
    }

    private CalificacionRiesgoResponse riesgo(boolean revision) {
        CalificacionRiesgoResponse r = mock(CalificacionRiesgoResponse.class);
        when(r.requiereRevision()).thenReturn(revision);
        when(r.nivelEfectivo()).thenReturn("BAJO");
        return r;
    }

    @BeforeEach
    void setUp() {
        servicios = mock(ServicioRepository.class);
        subprocesos = mock(ServicioSubprocesoRepository.class);
        riesgo = mock(CalificacionRiesgoService.class);
        cancelacion = mock(CancelacionSolicitudService.class);
        documentos = mock(DocumentoEstudioRepository.class);
        cola = mock(ColaTrabajosService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        servicio = new EmisionEstudioService(servicios, subprocesos, usuarios, riesgo, cancelacion, documentos, cola, mock(JdbcTemplate.class), mock(AlmacenTrabajos.class),
                mock(com.polygraph.erp.shared.repository.NotificacionRepository.class));
        s = Servicio.builder().idServicio(52).cliente(Cliente.builder().idCliente(1).build()).estado(EstadoServicio.FINALIZADO).build();
        when(sesion.getUsername()).thenReturn("g@x.co");
        when(usuarios.findByEmail("g@x.co")).thenReturn(Optional.of(gestor));
        when(servicios.findById(52)).thenReturn(Optional.of(s));
        when(documentos.save(any())).thenAnswer(i -> { DocumentoEstudio d = i.getArgument(0); d.setId(77L); return d; });
        when(documentos.findFirstByIdServicioOrderByVersionDesc(52)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("El estudio final está bloqueado mientras haya subprocesos sin publicar; no cambia nada")
    void finalConPendientesNoSePuede() {
        when(subprocesos.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(52)).thenReturn(List.of(sp(1, "Antecedentes", EstadoSubproceso.PUBLICADO), sp(2, "Visita", EstadoSubproceso.EN_EJECUCION)));
        assertThatThrownBy(() -> servicio.emitir(52, new EmitirEstudioRequest("FINAL", "Nota", false, null), sesion))
                .isInstanceOf(ApiException.class).hasMessageContaining("faltan por publicar Visita");
        verify(cancelacion, never()).publicarEstudioSinAviso(any(), any(), any());
        assertThat(s.getPastaEstado()).isEqualTo(PastaEstado.PENDIENTE);
    }

    @Test
    @DisplayName("La nota del gestor es obligatoria, y cada alerta lleva su justificación interna")
    void notaYAlertasObligatorias() {
        assertThatThrownBy(() -> servicio.emitir(52, new EmitirEstudioRequest("FINAL", " ", false, null), sesion))
                .isInstanceOf(ApiException.class).hasMessageContaining("nota del gestor");
        assertThatThrownBy(() -> servicio.emitir(52, new EmitirEstudioRequest("PRELIMINAR", "Nota", false, List.of(new EmitirEstudioRequest.Alerta("Alerta", " "))), sesion))
                .isInstanceOf(ApiException.class).hasMessageContaining("justificación");
    }

    @Test
    @DisplayName("Preliminar con pendientes: la pasta queda PRELIMINAR, el PDF dice qué falta y el cliente no ve nivel")
    void preliminar() {
        when(subprocesos.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(52)).thenReturn(List.of(sp(1, "Antecedentes", EstadoSubproceso.PUBLICADO), sp(2, "Visita", EstadoSubproceso.EN_EJECUCION)));
        CalificacionRiesgoResponse resp = riesgo(false);
        when(riesgo.obtener(eq(52), any())).thenReturn(resp);
        var r = servicio.emitir(52, new EmitirEstudioRequest("PRELIMINAR", "Pendiente la visita", false, null), sesion);
        assertThat(r.version()).isEqualTo(1);
        assertThat(s.getPastaEstado()).isEqualTo(PastaEstado.PRELIMINAR);
        assertThat(s.getConceptoGestor()).isEqualTo("Pendiente la visita");
        verify(cancelacion, never()).publicarEstudioSinAviso(any(), any(), any());
        ArgumentCaptor<DocumentoEstudio> c = ArgumentCaptor.forClass(DocumentoEstudio.class);
        verify(documentos).save(c.capture());
        assertThat(c.getValue().getNivel()).isNull();
        assertThat(c.getValue().getDetalle()).isEqualTo("PRELIMINAR – CIERRE PENDIENTE: Visita");
    }

    @Test
    @DisplayName("Final con todo publicado: publica el servicio con la nota, invalida el PDF anterior y crea la versión siguiente; el trabajo se encola al confirmar")
    void finalCreaVersionNueva() {
        when(subprocesos.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(52)).thenReturn(List.of(sp(1, "Antecedentes", EstadoSubproceso.PUBLICADO)));
        CalificacionRiesgoResponse resp = riesgo(false);
        when(riesgo.obtener(eq(52), any())).thenReturn(resp);
        DocumentoEstudio anterior = DocumentoEstudio.builder().id(70L).idServicio(52).version(1).tipo("FINAL").build();
        when(documentos.findFirstByIdServicioOrderByVersionDesc(52)).thenReturn(Optional.of(anterior));

        var r = servicio.emitir(52, new EmitirEstudioRequest("FINAL", "Todo en orden", false, null), sesion);

        assertThat(r.version()).isEqualTo(2);
        verify(documentos).invalidar(52);
        ArgumentCaptor<CambioEstadoRequest> c = ArgumentCaptor.forClass(CambioEstadoRequest.class);
        verify(cancelacion).publicarEstudioSinAviso(eq(52), c.capture(), any());   // sin avisar al cliente: el aviso sale cuando el PDF queda LISTO
        assertThat(c.getValue().estado()).isEqualTo("PUBLICADO");
        assertThat(c.getValue().observacion()).isEqualTo("Todo en orden");
        verify(cola, never()).encolarTrasCommit(any(), any(), any());              // todavía no hay commit
        for (TransactionSynchronization t : TransactionSynchronizationManager.getSynchronizations()) t.afterCommit();
        // PC-01: en una transacción NUEVA (REQUIRES_NEW): un INSERT que se uniera a la ya cerrada nunca se confirmaría
        verify(cola).encolarTrasCommit(eq(com.polygraph.erp.shared.enums.TipoTrabajo.ESTUDIO_PDF), any(), any());
        verify(cola, never()).encolar(any(), any(), any());
    }

    @Test
    @DisplayName("Si el motor pide revisión manual, el final exige confirmarla (y queda quién y cuándo)")
    void revisionManual() {
        when(subprocesos.findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(52)).thenReturn(List.of(sp(1, "Antecedentes", EstadoSubproceso.PUBLICADO)));
        CalificacionRiesgoResponse resp = riesgo(true);
        when(riesgo.obtener(eq(52), any())).thenReturn(resp);
        assertThatThrownBy(() -> servicio.emitir(52, new EmitirEstudioRequest("FINAL", "Nota", false, null), sesion))
                .isInstanceOf(ApiException.class).hasMessageContaining("revisión manual");
        servicio.emitir(52, new EmitirEstudioRequest("FINAL", "Nota", true, null), sesion);
        assertThat(s.getRevisionManualPor()).isEqualTo(5L);
        assertThat(s.getRevisionManualEn()).isNotNull();
    }
}
