package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.formularios.service.DocumentoPdfConverter;
import com.polygraph.erp.modules.formularios.service.FormularioDocumentoService;
import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.modules.trabajos.service.LimpiezaTrabajosJob;
import com.polygraph.erp.modules.trabajos.service.ProcesadorTrabajo;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PC-01: el procesador solo marca el PDF como fallido cuando ya no habrá otro intento; sin permiso es 403; al arrancar se retoma lo que se interrumpió. */
@DisplayName("PC-01 — reintentos del PDF, 403 y reinicio")
class PC01ProcesadorYColaTest {

    private final Usuario gestor = Usuario.builder().idUsuario(5L).email("g@x.co").build();

    private TrabajoAsincrono trabajo(int intentos, int max) {
        return TrabajoAsincrono.builder().usuario(gestor).tipo(TipoTrabajo.ESTUDIO_PDF).estado(EstadoTrabajo.PROCESANDO).intentos(intentos).maxIntentos(max).build();
    }

    @Test
    @DisplayName("Mientras queden intentos el PDF sigue «generándose»; en el último intento queda en ERROR")
    void soloElUltimoIntentoMarcaError() {
        var emision = mock(EmisionEstudioService.class);
        when(emision.portada(77L)).thenThrow(new IllegalStateException("LibreOffice no responde"));
        var procesador = new EstudioPdfProcesador(emision, mock(FormularioDocumentoService.class), mock(DocumentoPdfConverter.class), mock(AlmacenTrabajos.class));
        var contexto = mock(ProcesadorTrabajo.ContextoTrabajo.class);

        assertThatThrownBy(() -> procesador.procesar(trabajo(1, 3), Map.of("idDocumento", 77L), contexto)).isInstanceOf(IllegalStateException.class);
        verify(emision).marcarGenerando(77L);
        verify(emision, never()).marcarError(any(), anyString());

        assertThatThrownBy(() -> procesador.procesar(trabajo(3, 3), Map.of("idDocumento", 77L), contexto)).isInstanceOf(IllegalStateException.class);
        verify(emision).marcarError(eq(77L), anyString());
    }

    @Test
    @DisplayName("Sin permiso responde 403 con un texto claro, no «Error interno»")
    void sinPermisoEs403() {
        var r = new GlobalExceptionHandler().manejarSinPermiso(new AccessDeniedException("Access Denied"));
        assertThat(r.getStatusCode().value()).isEqualTo(403);
        assertThat(r.getBody()).containsEntry("mensaje", "No tienes permiso para esta acción.");
    }

    @Test
    @DisplayName("Al arrancar, lo que estaba PROCESANDO vuelve a la cola (o queda FALLIDO si ya no tiene intentos)")
    void alArrancarSeRetoma() {
        var repo = mock(TrabajoAsincronoRepository.class);
        var props = mock(PropiedadesTrabajos.class);
        when(props.isHabilitado()).thenReturn(true);
        when(props.tipo(any())).thenReturn(new PropiedadesTrabajos.Tipo());
        TrabajoAsincrono conIntentos = trabajo(1, 3);
        TrabajoAsincrono sinIntentos = trabajo(3, 3);
        when(repo.findByTipoAndEstadoAndFechaInicioBefore(eq(TipoTrabajo.ESTUDIO_PDF), eq(EstadoTrabajo.PROCESANDO), any(LocalDateTime.class)))
                .thenReturn(List.of(conIntentos, sinIntentos));
        var limpieza = new LimpiezaTrabajosJob(repo, props, mock(AlmacenTrabajos.class), mock(ColaTrabajosService.class), new TransactionTemplate(mock(PlatformTransactionManager.class)));

        limpieza.rescatarAlArrancar();

        assertThat(conIntentos.getEstado()).isEqualTo(EstadoTrabajo.EN_COLA);
        assertThat(sinIntentos.getEstado()).isEqualTo(EstadoTrabajo.FALLIDO);
    }
}
