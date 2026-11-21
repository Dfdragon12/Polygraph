package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.clientes.entity.AprobacionPospago;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.entity.ClientePospago;
import com.polygraph.erp.modules.clientes.entity.ExpedientePospago;
import com.polygraph.erp.modules.clientes.entity.SoporteCliente;
import com.polygraph.erp.modules.clientes.repository.AprobacionPospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.ExpedientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.SoporteClienteRepository;
import com.polygraph.erp.modules.clientes.service.AltaPospagoService.Requisito;
import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.enums.EstadoMora;
import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.enums.TipoSoporte;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

/** TP-13: alta controlada del pospago: lista de verificación leída de Documentos, excepciones, aprobación y reporte de los pospago actuales. */
class TiendaTp13Test {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    private ClienteRepository clientes;
    private ClientePospagoRepository pospagos;
    private SoporteClienteRepository soportes;
    private ExpedientePospagoRepository expedientes;
    private AprobacionPospagoRepository aprobaciones;
    private ParametrosTiendaService parametros;
    private NotificacionService notificaciones;
    private AltaPospagoService servicio;
    private final List<SoporteCliente> documentos = new ArrayList<>();
    private Cliente acme;
    private ExpedientePospago expediente;

    @BeforeEach
    void setUp() {
        clientes = mock(ClienteRepository.class);
        pospagos = mock(ClientePospagoRepository.class);
        soportes = mock(SoporteClienteRepository.class);
        expedientes = mock(ExpedientePospagoRepository.class);
        aprobaciones = mock(AprobacionPospagoRepository.class);
        parametros = mock(ParametrosTiendaService.class);
        notificaciones = mock(NotificacionService.class);
        when(parametros.activo(ParametrosTiendaService.ALTA_POSPAGO_CONTROLADA)).thenReturn(true);
        when(parametros.entero("pospagoEmpleadosMinimos")).thenReturn(200);
        when(parametros.entero("pospagoProcesosMinimosMes")).thenReturn(20);
        when(parametros.entero("diasVigenciaCamaraComercio")).thenReturn(90);
        servicio = new AltaPospagoService(clientes, pospagos, soportes, expedientes, aprobaciones, parametros, mock(AlmacenamientoDocumentosService.class),
                notificaciones, Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneOffset.UTC));

        acme = Cliente.builder().idCliente(1).tipoPersona(TipoPersona.JURIDICA).tipoCliente(TipoCliente.PREPAGO).nit("900123456").razonSocial("Acme S.A.S.")
                .idGestor(5L).build();
        when(clientes.findById(1)).thenReturn(Optional.of(acme));
        when(clientes.findAll()).thenReturn(List.of(acme));
        when(soportes.findByCliente_IdCliente(1)).thenReturn(documentos);
        expediente = ExpedientePospago.builder().idCliente(1).build();
        when(expedientes.findById(1)).thenReturn(Optional.of(expediente));
    }

    private void documento(TipoSoporte tipo, EstadoSoporte estado, LocalDate entrega) {
        documentos.removeIf(d -> d.getTipoSoporte() == tipo);
        documentos.add(SoporteCliente.builder().tipoSoporte(tipo).estado(estado).archivoAdjunto("clientes/x/" + tipo + ".pdf").fechaEntrega(entrega).build());
    }

    /** Todo en regla: documentos aprobados, estudio, contrato, cupo y corte; empleados y procesos por encima del mínimo. */
    private void todoEnRegla() {
        documento(TipoSoporte.RUT, EstadoSoporte.VALIDADO, HOY.minusDays(10));
        documento(TipoSoporte.CAMARA_COMERCIO, EstadoSoporte.VALIDADO, HOY.minusDays(30));
        documento(TipoSoporte.CEDULA_150, EstadoSoporte.VALIDADO, HOY.minusDays(10));
        expediente.setEmpleadosDeclarados(350);
        expediente.setSoporteEmpleados("clientes/x/pila.pdf");
        expediente.setProcesosProyectados(60);
        expediente.setSoporteProcesos("clientes/x/propuesta.pdf");
        expediente.setEstudioCreditoArchivo("clientes/x/estudio.pdf");
        expediente.setEstudioCreditoFecha(HOY.minusDays(2));
        expediente.setEstudioCreditoResultado("APROBADO");
        expediente.setEstudioCreditoPor("Analista de crédito");
        expediente.setContratoArchivo("clientes/x/contrato.pdf");
        expediente.setCupoSolicitado(new BigDecimal("15000000"));
        expediente.setDiaCorte(25);
    }

    private Requisito req(String codigo) {
        return servicio.verificar(1).requisitos().stream().filter(r -> r.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    // ───────────── los documentos se leen de Documentos ─────────────

    @Test
    @DisplayName("RUT aprobado en Documentos: sale en verde sin cargarlo de nuevo; si se rechaza allá, la lista lo muestra como faltante con enlace a Documentos")
    void rutDesdeDocumentos() {
        documento(TipoSoporte.RUT, EstadoSoporte.VALIDADO, HOY.minusDays(5));
        assertThat(req(AltaPospagoService.RUT).cumple()).isTrue();
        assertThat(req(AltaPospagoService.RUT).enlace()).isNull();

        documento(TipoSoporte.RUT, EstadoSoporte.RECHAZADO, HOY.minusDays(5));
        var rechazado = req(AltaPospagoService.RUT);
        assertThat(rechazado.cumple()).isFalse();
        assertThat(rechazado.enlace()).isEqualTo("DOCUMENTOS");
        assertThat(rechazado.falta()).contains("el RUT").contains("rechazado");
    }

    @Test
    @DisplayName("Sin cargar, pendiente de aprobación o vencido también cuentan como faltante")
    void sinCargarPendienteVencido() {
        assertThat(req(AltaPospagoService.RUT).detalle()).isEqualTo("Sin cargar");
        documento(TipoSoporte.RUT, EstadoSoporte.PENDIENTE, HOY);
        assertThat(req(AltaPospagoService.RUT).cumple()).isFalse();
        documento(TipoSoporte.RUT, EstadoSoporte.VENCIDO, HOY.minusYears(2));
        assertThat(req(AltaPospagoService.RUT).cumple()).isFalse();
    }

    @Test
    @DisplayName("La Cámara de Comercio además debe tener máximo diasVigenciaCamaraComercio (90) días")
    void camaraVigente() {
        documento(TipoSoporte.CAMARA_COMERCIO, EstadoSoporte.VALIDADO, HOY.minusDays(90));
        assertThat(req(AltaPospagoService.CAMARA_COMERCIO).cumple()).isTrue();
        documento(TipoSoporte.CAMARA_COMERCIO, EstadoSoporte.VALIDADO, HOY.minusDays(91));
        var r = req(AltaPospagoService.CAMARA_COMERCIO);
        assertThat(r.cumple()).isFalse();
        assertThat(r.detalle()).contains("90 días");
    }

    @Test
    @DisplayName("Persona natural: la lista no aplica y no se puede abrir el expediente")
    void personaNatural() {
        acme.setTipoPersona(TipoPersona.NATURAL);
        acme.setNit(null);
        var v = servicio.verificar(1);
        assertThat(v.aplica()).isFalse();
        assertThat(req(AltaPospagoService.PERSONA_JURIDICA).cumple()).isFalse();
        assertThatThrownBy(() -> servicio.guardarExpediente(1, new AltaPospagoService.ExpedienteRequest(300, null, null, null, null, null, null), "a@x.co"))
                .isInstanceOf(ApiException.class);
    }

    // ───────────── botón y mensajes ─────────────

    @Test
    @DisplayName("Sin estudio de crédito: el botón queda bloqueado con el mensaje «Falta el estudio de crédito» y aprobar da 400 (no tiene excepción)")
    void sinEstudio() {
        todoEnRegla();
        expediente.setEstudioCreditoArchivo(null);
        expediente.setEstudioCreditoResultado(null);
        var v = servicio.verificar(1);
        assertThat(v.sinFaltantes()).isFalse();
        assertThat(v.aprobable()).isFalse();
        assertThat(v.mensajeBloqueo()).contains("Falta el estudio de crédito");

        assertThatThrownBy(() -> servicio.aprobar(1, true, "es estratégico", "admin@x.co", Rol.ADMIN_POLYGRAPH)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Falta el estudio de crédito");
        assertThat(acme.getTipoCliente()).isEqualTo(TipoCliente.PREPAGO);
        verify(aprobaciones, never()).save(any());
    }

    @Test
    @DisplayName("Un estudio de crédito RECHAZADO no permite aprobar")
    void estudioRechazado() {
        todoEnRegla();
        expediente.setEstudioCreditoResultado("RECHAZADO");
        assertThat(req(AltaPospagoService.ESTUDIO_CREDITO).falta()).isEqualTo("El estudio de crédito fue rechazado");
        assertThat(servicio.verificar(1).aprobable()).isFalse();
    }

    @Test
    @DisplayName("Los documentos de la empresa tampoco tienen excepción")
    void documentosSinExcepcion() {
        todoEnRegla();
        documento(TipoSoporte.RUT, EstadoSoporte.RECHAZADO, HOY);
        assertThatThrownBy(() -> servicio.aprobar(1, true, "motivo", "admin@x.co", Rol.ADMIN_POLYGRAPH)).isInstanceOf(ApiException.class)
                .hasMessageContaining("el RUT");
    }

    // ───────────── aprobación ─────────────

    @Test
    @DisplayName("Todo en regla: aprueba; el cliente pasa a pospago con su cupo y corte, queda registro y se notifica al cliente y a su gestor")
    void apruebaTodoEnRegla() {
        todoEnRegla();
        when(pospagos.findByIdCliente(1)).thenReturn(Optional.empty());

        var v = servicio.aprobar(1, false, null, "admin@x.co", Rol.ADMIN_POLYGRAPH);

        assertThat(acme.getTipoCliente()).isEqualTo(TipoCliente.POSPAGO);
        ArgumentCaptor<ClientePospago> cp = ArgumentCaptor.forClass(ClientePospago.class);
        verify(pospagos).save(cp.capture());
        assertThat(cp.getValue().getLimiteCredito()).isEqualByComparingTo("15000000");
        assertThat(cp.getValue().getCreditoDisponible()).isEqualByComparingTo("15000000");
        assertThat(cp.getValue().getDiaCorte()).isEqualTo(25);
        assertThat(cp.getValue().getEstadoMora()).isEqualTo(EstadoMora.NORMAL);
        assertThat(cp.getValue().getRequiereAprobacion()).isFalse();
        ArgumentCaptor<AprobacionPospago> ap = ArgumentCaptor.forClass(AprobacionPospago.class);
        verify(aprobaciones).save(ap.capture());
        assertThat(ap.getValue().getAprobadoPor()).isEqualTo("admin@x.co");
        assertThat(ap.getValue().getExcepcion()).isFalse();
        assertThat(ap.getValue().getRequisitos()).contains("RUT=CUMPLE");
        verify(notificaciones).crearParaCliente(eq(1), eq("POSPAGO_APROBADO"), any(), any());
        verify(notificaciones).crearParaUsuario(eq(5L), any(), any(), any());
        assertThat(v.tipoCliente()).isEqualTo("POSPAGO");
    }

    @Test
    @DisplayName("Excepción (cliente estratégico con menos empleados): sin marcarla o sin motivo escrito no guarda; con motivo y el administrador principal sí, y queda registrada")
    void excepcion() {
        todoEnRegla();
        expediente.setEmpleadosDeclarados(120);   // menos de 200
        when(pospagos.findByIdCliente(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(1, false, null, "admin@x.co", Rol.ADMIN_POLYGRAPH)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Faltan los empleados");
        assertThatThrownBy(() -> servicio.aprobar(1, true, "  ", "admin@x.co", Rol.ADMIN_POLYGRAPH)).isInstanceOf(ApiException.class)
                .hasMessageContaining("motivo de la excepción es obligatorio");
        assertThat(acme.getTipoCliente()).isEqualTo(TipoCliente.PREPAGO);
        verify(aprobaciones, never()).save(any());

        servicio.aprobar(1, true, "Cliente estratégico: contrato marco con el grupo", "admin@x.co", Rol.ADMIN_POLYGRAPH);

        ArgumentCaptor<AprobacionPospago> ap = ArgumentCaptor.forClass(AprobacionPospago.class);
        verify(aprobaciones).save(ap.capture());
        assertThat(ap.getValue().getExcepcion()).isTrue();
        assertThat(ap.getValue().getMotivoExcepcion()).isEqualTo("Cliente estratégico: contrato marco con el grupo");
        assertThat(acme.getTipoCliente()).isEqualTo(TipoCliente.POSPAGO);
    }

    @Test
    @DisplayName("Quien no es el administrador principal no puede aprobar una excepción (403)")
    void excepcionSoloElPrincipal() {
        todoEnRegla();
        expediente.setProcesosProyectados(5);   // menos de 20
        assertThatThrownBy(() -> servicio.aprobar(1, true, "motivo", "gestor@x.co", Rol.GESTOR)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        assertThat(acme.getTipoCliente()).isEqualTo(TipoCliente.PREPAGO);
    }

    @Test
    @DisplayName("«Más de» es estricto: exactamente 200 empleados no cumple")
    void masDeEsEstricto() {
        todoEnRegla();
        expediente.setEmpleadosDeclarados(200);
        assertThat(req(AltaPospagoService.EMPLEADOS).cumple()).isFalse();
        expediente.setEmpleadosDeclarados(201);
        assertThat(req(AltaPospagoService.EMPLEADOS).cumple()).isTrue();
    }

    @Test
    @DisplayName("Con el interruptor apagado no se puede usar el alta controlada")
    void interruptorApagado() {
        when(parametros.activo(ParametrosTiendaService.ALTA_POSPAGO_CONTROLADA)).thenReturn(false);
        todoEnRegla();
        assertThatThrownBy(() -> servicio.aprobar(1, false, null, "admin@x.co", Rol.ADMIN_POLYGRAPH)).isInstanceOf(ApiException.class);
        assertThat(acme.getTipoCliente()).isEqualTo(TipoCliente.PREPAGO);
    }

    @Test
    @DisplayName("Un cliente que ya es pospago no se convierte otra vez")
    void yaEsPospago() {
        acme.setTipoCliente(TipoCliente.POSPAGO);
        todoEnRegla();
        assertThatThrownBy(() -> servicio.aprobar(1, false, null, "admin@x.co", Rol.ADMIN_POLYGRAPH)).isInstanceOf(ApiException.class)
                .hasMessageContaining("ya es pospago");
    }

    // ───────────── reporte de los pospago actuales ─────────────

    @Test
    @DisplayName("Cliente pospago con cupo de $15.000.000 y 4 documentos sin cargar: aparece en el reporte con sus faltantes y NO se le toca el cupo")
    void reportePospagoActual() {
        acme.setTipoCliente(TipoCliente.POSPAGO);
        var cupo = ClientePospago.builder().idCliente(1).limiteCredito(new BigDecimal("15000000")).creditoDisponible(new BigDecimal("15000000"))
                .estadoMora(EstadoMora.NORMAL).build();
        when(pospagos.findByIdCliente(1)).thenReturn(Optional.of(cupo));
        when(expedientes.findById(1)).thenReturn(Optional.empty());

        var filas = servicio.reporte();

        assertThat(filas).hasSize(1);
        var f = filas.get(0);
        assertThat(f.cupo()).isEqualByComparingTo("15000000");
        assertThat(f.cumpleTodo()).isFalse();
        assertThat(f.faltantes()).anyMatch(s -> s.contains("el RUT")).anyMatch(s -> s.contains("Cámara de Comercio"))
                .anyMatch(s -> s.contains("representante legal")).anyMatch(s -> s.contains("estudio de crédito"));
        assertThat(cupo.getLimiteCredito()).isEqualByComparingTo("15000000");
        verify(pospagos, never()).save(any());
        verify(clientes, never()).save(any());
    }

    @Test
    @DisplayName("El reporte no incluye prepago")
    void reporteSoloPospago() {
        assertThat(servicio.reporte()).isEmpty();
    }
}
